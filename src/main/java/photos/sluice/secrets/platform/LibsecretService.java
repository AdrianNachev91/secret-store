package photos.sluice.secrets.platform;

import photos.sluice.secrets.SecretStoreException;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

/**
 * Talks to the freedesktop Secret Service through {@code libsecret}, bound with the JDK's own
 * foreign-function API.
 *
 * <p>No third-party library sits in between, for the same reason the other two bindings take none.
 * A credential store is the last component to hold a dependency that stops following the JDK.
 *
 * <p>libsecret 0.19.0 is the floor, because {@code secret_password_search_sync} is documented as
 * available since that release. Later versions are expected to carry it rather than known to: this
 * binds {@code libsecret-1.so.0}, and a soname exists so that symbols are not dropped beneath it.
 * Dropping one would mean a new soname, which this would fail to load instead.
 *
 * <p>Nothing here checks any of that, and nothing needs to. A version that does not resolve one of
 * these symbols leaves no keyring tier registered, so the machine keeps its credential in the
 * protected file. The binding's own failure names the missing symbol, which separates an install
 * too old to serve this library from a machine carrying no libsecret at all.
 *
 * <p>A store names the service's default collection. Reads, searches and removals name none and
 * span every collection, so an entry the user later moves is still found. Each is identified by a
 * schema built from the consumer's namespace plus one attribute carrying the entry name.
 *
 * <p>The schema is what makes a shared keyring safe to work in. The service matches on it, since
 * the flag that would disable that is deliberately not set. So a credential stored under a
 * different schema is out of reach of every lookup, search and removal made here. Two consumers
 * choosing one namespace share a schema. Nothing about this depends on the entry names being
 * unusual.
 *
 * <p>The search-and-store functions bound here are variadic in C, taking attribute name and
 * value pairs closed by a null. So each of their descriptors names where the variadic part begins,
 * and each of those calls passes exactly one pair. The release functions take fixed arguments.
 *
 * <p>A failure the service reports arrives in a {@code GError}, which is read and then released.
 * Not every failure comes that way: a store can answer that it did nothing while reporting no
 * error, and that silence is reported too. The two libraries beside libsecret are loaded for the
 * memory discipline. GLib frees the error and the search list, and GObject supplies the release
 * function each search result needs.
 */
final class LibsecretService implements LinuxSecretService {

    /**
     * The layout libsecret documents for {@code SecretSchema}. The attribute table is a fixed
     * 32-slot array, read until its first empty slot. A schema with one attribute therefore fills
     * slot one and leaves the zeroed remainder as the terminator.
     */
    private static final MemoryLayout SECRET_SCHEMA = MemoryLayout.structLayout(
            ValueLayout.ADDRESS.withName("name"),
            ValueLayout.JAVA_INT.withName("flags"),
            MemoryLayout.paddingLayout(4),
            MemoryLayout.sequenceLayout(32, MemoryLayout.structLayout(
                    ValueLayout.ADDRESS.withName("name"),
                    ValueLayout.JAVA_INT.withName("type"),
                    MemoryLayout.paddingLayout(4))).withName("attributes"),
            ValueLayout.JAVA_INT.withName("reserved"),
            MemoryLayout.paddingLayout(4),
            MemoryLayout.sequenceLayout(7, ValueLayout.ADDRESS).withName("reservedPointers"));

    /**
     * The layout GLib documents for {@code GError}: an error domain, a code within it, and the
     * human-readable message this class reports.
     */
    private static final MemoryLayout G_ERROR = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("domain"),
            ValueLayout.JAVA_INT.withName("code"),
            ValueLayout.ADDRESS.withName("message"));

    /**
     * What the consumer's namespace is suffixed with to name the schema.
     */
    private static final String SCHEMA_SUFFIX = ".Credential";

    private static final String ATTRIBUTE_NAME = "name";

    /**
     * The alias libsecret documents as {@code SECRET_COLLECTION_DEFAULT}: whichever collection the
     * user's desktop has marked as its default keyring.
     */
    static final String DEFAULT_COLLECTION = "default";

    /**
     * The two shapes libsecret can turn into a D-Bus object path. An alias becomes one element
     * under the aliases prefix, and a path is sent as it stands. Either way every element allows
     * only letters, digits and underscores.
     *
     * <p>libsecret picks between those by whether the name holds a slash anywhere, not by whether
     * it starts with one. This requires a leading slash for the second shape, which is deliberately
     * the narrower test. It is what refuses {@code foo/bar}, a name libsecret would pass through as
     * the invalid path it already is. Widening this to match libsecret's own check reinstates the
     * hang.
     */
    private static final Pattern COLLECTION =
            Pattern.compile("[A-Za-z0-9_]+|(?:/[A-Za-z0-9_]+)+");

    private static final int SECRET_SEARCH_NONE = 0;

    /**
     * The schema name stored beside every entry, and matched again on every lookup. It scopes the
     * service's search to entries this consumer stored, so the one attribute above only has to be
     * unique within them.
     */
    private final String schemaName;

    private final String collection;
    private final MethodHandle passwordStore;
    private final MethodHandle passwordLookup;
    private final MethodHandle passwordClear;
    private final MethodHandle passwordSearch;
    private final MethodHandle passwordFree;
    private final MethodHandle errorFree;
    private final MethodHandle listFreeFull;
    private final MemorySegment objectUnref;

    /**
     * Binds the functions this class calls, failing where any of them cannot be found.
     *
     * <p>Private because a caller has no way to handle a machine without libsecret, and
     * {@link #open(String)} is where that possibility is turned into an answer.
     *
     * @param schemaName {@link String} the schema every entry carries and every lookup matches on
     * @param collection {@link String} the collection every store names, as an alias or as an
     *         object path
     * @param libsecret {@link SymbolLookup} the loaded libsecret library
     * @param glib {@link SymbolLookup} the loaded GLib library
     * @param gobject {@link SymbolLookup} the loaded GObject library
     */
    private LibsecretService(final String schemaName, final String collection,
            final SymbolLookup libsecret, final SymbolLookup glib, final SymbolLookup gobject) {
        this.schemaName = schemaName;
        this.collection = collection;
        final Linker linker = Linker.nativeLinker();
        this.passwordStore = linker.downcallHandle(
                symbol(libsecret, "secret_password_store_sync"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                Linker.Option.firstVariadicArg(6));
        this.passwordLookup = linker.downcallHandle(
                symbol(libsecret, "secret_password_lookup_sync"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                Linker.Option.firstVariadicArg(3));
        this.passwordClear = linker.downcallHandle(
                symbol(libsecret, "secret_password_clear_sync"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS),
                Linker.Option.firstVariadicArg(3));
        this.passwordSearch = linker.downcallHandle(
                symbol(libsecret, "secret_password_search_sync"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                Linker.Option.firstVariadicArg(4));
        this.passwordFree = linker.downcallHandle(
                symbol(libsecret, "secret_password_free"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.errorFree = linker.downcallHandle(
                symbol(glib, "g_error_free"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.listFreeFull = linker.downcallHandle(
                symbol(glib, "g_list_free_full"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.objectUnref = symbol(gobject, "g_object_unref");
    }

    /**
     * Binds the Secret Service through the machine's libsecret, aimed at the default collection.
     *
     * <p>Refuses rather than answering with nothing, so what an absent library means is decided by
     * the caller instead of here. This class reports what the machine did; nothing in it rules on
     * whether that machine is a broken install or simply one with no desktop keyring.
     *
     * <p>The libraries are loaded into the global scope, so they stay reachable for as long as the
     * handles built from them. Nothing here owns a moment at which unloading them would be correct.
     *
     * @param namespace {@link String} the consumer's reverse-domain namespace, which the schema
     *         every entry carries is built from
     * @return {@link LinuxSecretService} the bound service
     * @throws IllegalArgumentException when this machine is missing any of the three libraries, or
     *         one of them does not export a function named here
     * @throws UnsatisfiedLinkError when a library is present but cannot be loaded
     */
    static LinuxSecretService open(final String namespace) {
        return open(namespace, DEFAULT_COLLECTION);
    }

    /**
     * Binds the Secret Service aimed at the given collection. Package-private so a test can
     * provoke a refusal on demand, which no other operation here offers. Reads, searches and
     * removals name no collection and search all of them, so a working service refuses none.
     *
     * <p>A collection is refused here unless every element of it is letters, digits and
     * underscores. Anything else yields a syntactically invalid D-Bus object path. The library
     * asserts on that and abandons the call without completing it, and the synchronous wrapper then
     * waits for an answer that never arrives. Refusing is what keeps a bad name from hanging the
     * calling thread instead of failing it.
     *
     * <p>The refusal runs before any library is loaded, so it answers the same on every platform.
     *
     * @param namespace {@link String} the consumer's reverse-domain namespace, which the schema
     *         every entry carries is built from
     * @param collection {@link String} the collection every store names, as an alias or as an
     *         object path
     * @return {@link LinuxSecretService} the bound service
     * @throws IllegalArgumentException when the collection is neither an alias nor an object path,
     *         when this machine is missing any of the three libraries, or when one of them does not
     *         export a function named here
     * @throws UnsatisfiedLinkError when a library is present but cannot be loaded
     */
    static LinuxSecretService open(final String namespace, final String collection) {
        if (!namesACollection(collection)) {
            throw new IllegalArgumentException("the collection '" + collection + "' must be an"
                    + " alias, or an object path whose every element is letters, digits and"
                    + " underscores");
        }
        return new LibsecretService(schemaNameFor(namespace), collection,
                SymbolLookup.libraryLookup("libsecret-1.so.0", Arena.global()),
                SymbolLookup.libraryLookup("libglib-2.0.so.0", Arena.global()),
                SymbolLookup.libraryLookup("libgobject-2.0.so.0", Arena.global()));
    }

    /**
     * Whether libsecret could turn this name into a D-Bus object path. Package-private so a test
     * can pin both answers on every runner, rather than only where a Secret Service answers.
     *
     * @param collection {@link String} the name a caller offered
     * @return boolean true when it is an alias or an object path this can safely send
     */
    static boolean namesACollection(final String collection) {
        return COLLECTION.matcher(collection).matches();
    }

    /**
     * Names the schema for a consumer's namespace. Package-private so a test can pin the result
     * against the literal on every runner, rather than only where a Secret Service answers.
     *
     * @param namespace {@link String} the consumer's reverse-domain namespace
     * @return {@link String} the schema name every entry carries
     */
    static String schemaNameFor(final String namespace) {
        return namespace + SCHEMA_SUFFIX;
    }

    @Override
    public Optional<String> read(final String name) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment errorSlot = arena.allocate(ValueLayout.ADDRESS);
            final MemorySegment password = this.callLookup(arena, errorSlot, name);
            this.throwIfRefused(errorSlot, "read", name);
            if (password.equals(MemorySegment.NULL)) {
                return Optional.empty();
            }
            // The service allocated this itself, so it is released through libsecret's own free,
            // which also wipes the bytes first. Reinterpreting is what makes the C string readable:
            // its length is unknown until the terminator is found.
            try {
                return Optional.of(password.reinterpret(Long.MAX_VALUE).getString(0));
            } finally {
                this.freePassword(password);
            }
        }
    }

    @Override
    public boolean holds(final String name) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment errorSlot = arena.allocate(ValueLayout.ADDRESS);
            // The flag word is empty on purpose. Asking the service to unlock is asking it to put
            // a dialog on screen, and this call exists so a label can be drawn without one. The
            // guarantee holds at the library's own level. A search takes the unlock branch only
            // when the unlock flag is set, and loads secrets only when the secrets flag is. A
            // locked entry is still matched here, which is what makes existence answerable
            // without opening anything.
            final MemorySegment matches = this.callSearch(arena, errorSlot, name);
            this.throwIfRefused(errorSlot, "look for", name);
            if (matches.equals(MemorySegment.NULL)) {
                return false;
            }
            this.freeMatches(matches);
            return true;
        }
    }

    @Override
    public void write(final String name, final String label, final String secret) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment errorSlot = arena.allocate(ValueLayout.ADDRESS);
            final int stored = this.callStore(arena, errorSlot, name, label, secret);
            this.throwIfRefused(errorSlot, "store", name);
            // A refusal that set no error would otherwise return from here as a success. A save
            // that silently did nothing is the one outcome this tier may never produce, so the
            // silence itself is reported.
            if (stored == 0) {
                throw new SecretStoreException(SecretStoreException.Tier.KEYRING,
                        "The Secret Service did not store the entry for '" + name
                                + "' and gave no reason");
            }
        }
    }

    @Override
    public void delete(final String name) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment errorSlot = arena.allocate(ValueLayout.ADDRESS);
            this.callClear(arena, errorSlot, name);
            this.throwIfRefused(errorSlot, "remove", name);
        }
    }

    /**
     * Resolves one function in a loaded library.
     *
     * <p>Refuses rather than answering with nothing, so a library that loads but is missing a
     * function leaves {@link #open(String)} the same way a missing library does. What either one
     * means is the caller's ruling, not this class's.
     *
     * @param library {@link SymbolLookup} the loaded library
     * @param name {@link String} the function's exported name
     * @return {@link MemorySegment} the function's address
     */
    private static MemorySegment symbol(final SymbolLookup library, final String name) {
        return library.find(name).orElseThrow(() -> new IllegalArgumentException(
                "this machine's libsecret installation exports no " + name));
    }

    /**
     * Builds the schema every call passes, filled with the schema name and its one attribute.
     *
     * <p>Rebuilt per call in that call's own arena rather than kept alive in a longer one. The
     * struct is small, and this way every allocation a call makes shares one lifetime.
     *
     * @param arena {@link Arena} the allocation scope of the call being made
     * @return {@link MemorySegment} the filled-in schema
     */
    private MemorySegment schema(final Arena arena) {
        final MemorySegment schema = arena.allocate(SECRET_SCHEMA);
        schema.set(ValueLayout.ADDRESS,
                SECRET_SCHEMA.byteOffset(groupElement("name")),
                arena.allocateFrom(this.schemaName));
        // The flags, the attribute type and the remaining attribute slots are all left zero. A
        // fresh allocation is already zeroed. Zero is both the plain-schema flag word and the
        // string attribute type, and a zeroed slot is the table's terminator.
        schema.set(ValueLayout.ADDRESS,
                SECRET_SCHEMA.byteOffset(groupElement("attributes"),
                        MemoryLayout.PathElement.sequenceElement(0), groupElement("name")),
                arena.allocateFrom(ATTRIBUTE_NAME));
        return schema;
    }

    /**
     * Reports a call the service refused, reading and then releasing the error it filled in.
     *
     * <p>Returns quietly when no error was recorded. Each caller separately decides what its
     * call's own return value means once a refusal is ruled out.
     *
     * @param errorSlot {@link MemorySegment} where the call was told to record a failure
     * @param operation {@link String} what was being attempted, in the words of the caller
     * @param name {@link String} the entry the operation named
     * @throws SecretStoreException when the call recorded a failure
     */
    private void throwIfRefused(final MemorySegment errorSlot, final String operation,
            final String name) {
        final MemorySegment error = errorSlot.get(ValueLayout.ADDRESS, 0);
        if (error.equals(MemorySegment.NULL)) {
            return;
        }
        final String reason = error.reinterpret(G_ERROR.byteSize())
                .get(ValueLayout.ADDRESS, G_ERROR.byteOffset(groupElement("message")))
                .reinterpret(Long.MAX_VALUE)
                .getString(0);
        this.freeError(error);
        throw new SecretStoreException(SecretStoreException.Tier.KEYRING,
                "The Secret Service refused to " + operation + " the entry '" + name + "': "
                        + reason);
    }

    /**
     * Invokes {@code secret_password_lookup_sync}, turning the checked throwable the invocation
     * declares into the failure this class reports.
     *
     * @param arena {@link Arena} the allocation scope of this call
     * @param errorSlot {@link MemorySegment} where the call records a failure
     * @param name {@link String} the entry name to look up
     * @return {@link MemorySegment} the stored password, or null when no entry matched
     */
    private MemorySegment callLookup(final Arena arena, final MemorySegment errorSlot,
            final String name) {
        try {
            return (MemorySegment) this.passwordLookup.invokeExact(this.schema(arena), MemorySegment.NULL,
                    errorSlot, arena.allocateFrom(ATTRIBUTE_NAME), arena.allocateFrom(name),
                    MemorySegment.NULL);
        } catch (final Throwable unreachable) {
            throw binding(unreachable);
        }
    }

    /**
     * Invokes {@code secret_password_search_sync} with an empty flag word.
     *
     * @param arena {@link Arena} the allocation scope of this call
     * @param errorSlot {@link MemorySegment} where the call records a failure
     * @param name {@link String} the entry name to look for
     * @return {@link MemorySegment} the list of matches, or null when no entry matched
     */
    private MemorySegment callSearch(final Arena arena, final MemorySegment errorSlot,
            final String name) {
        try {
            return (MemorySegment) this.passwordSearch.invokeExact(this.schema(arena), SECRET_SEARCH_NONE,
                    MemorySegment.NULL, errorSlot, arena.allocateFrom(ATTRIBUTE_NAME),
                    arena.allocateFrom(name), MemorySegment.NULL);
        } catch (final Throwable unreachable) {
            throw binding(unreachable);
        }
    }

    /**
     * Invokes {@code secret_password_store_sync}.
     *
     * @param arena {@link Arena} the allocation scope of this call
     * @param errorSlot {@link MemorySegment} where the call records a failure
     * @param name {@link String} the entry name to store under
     * @param label {@link String} what a user browsing their keyring sees the entry called
     * @param secret {@link String} the credential to store
     * @return int zero when the call reported failure
     */
    private int callStore(final Arena arena, final MemorySegment errorSlot, final String name,
            final String label, final String secret) {
        try {
            return (int) this.passwordStore.invokeExact(this.schema(arena),
                    arena.allocateFrom(this.collection), arena.allocateFrom(label),
                    arena.allocateFrom(secret), MemorySegment.NULL, errorSlot,
                    arena.allocateFrom(ATTRIBUTE_NAME), arena.allocateFrom(name),
                    MemorySegment.NULL);
        } catch (final Throwable unreachable) {
            throw binding(unreachable);
        }
    }

    /**
     * Invokes {@code secret_password_clear_sync}. Its return value answers false for a missing
     * entry and for a refusal alike, so the error slot is the only failure signal worth reading.
     *
     * @param arena {@link Arena} the allocation scope of this call
     * @param errorSlot {@link MemorySegment} where the call records a failure
     * @param name {@link String} the entry name to remove
     */
    private void callClear(final Arena arena, final MemorySegment errorSlot, final String name) {
        try {
            final int _ = (int) this.passwordClear.invokeExact(this.schema(arena), MemorySegment.NULL,
                    errorSlot, arena.allocateFrom(ATTRIBUTE_NAME), arena.allocateFrom(name),
                    MemorySegment.NULL);
        } catch (final Throwable unreachable) {
            throw binding(unreachable);
        }
    }

    /**
     * Releases a password the service allocated, wiping its bytes first.
     *
     * @param password {@link MemorySegment} the password to release
     */
    private void freePassword(final MemorySegment password) {
        try {
            this.passwordFree.invokeExact(password);
        } catch (final Throwable unreachable) {
            throw binding(unreachable);
        }
    }

    /**
     * Releases an error the service allocated.
     *
     * @param error {@link MemorySegment} the error to release
     */
    private void freeError(final MemorySegment error) {
        try {
            this.errorFree.invokeExact(error);
        } catch (final Throwable unreachable) {
            throw binding(unreachable);
        }
    }

    /**
     * Releases a search's result list along with every item in it.
     *
     * @param matches {@link MemorySegment} the list to release
     */
    private void freeMatches(final MemorySegment matches) {
        try {
            this.listFreeFull.invokeExact(matches, this.objectUnref);
        } catch (final Throwable unreachable) {
            throw binding(unreachable);
        }
    }

    /**
     * Wraps whatever a native invocation threw.
     *
     * <p>A downcall handle declares {@code Throwable} because a native function may raise anything.
     * These raise nothing. What can fail reports a return value plus a recorded error, and the
     * release calls report nothing at all. So reaching here means this binding is wrong, rather
     * than a credential that could not be stored. The message says the same thing, since a user
     * who sees it is looking at a defect and not at something their machine did.
     *
     * @param cause {@link Throwable} what the invocation threw
     * @return {@link SecretStoreException} the failure to report
     */
    private static SecretStoreException binding(final Throwable cause) {
        return new SecretStoreException(SecretStoreException.Tier.KEYRING,
                "This library's binding to the Secret Service is built wrong and could not be called",
                cause);
    }
}
