package photos.sluice.secrets;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Where a credential lives, across the tiers a machine offers.
 *
 * <p>Two methods rather than one, because the two callers want opposite things. Whatever
 * authenticates with the credential needs the value and not the tier. Whatever reports where it
 * lives needs the tier and must never hold the value. So {@link #status} cannot return a
 * credential at all.
 *
 * <p>A read consults every tier and the first to answer wins, with an environment variable ahead of
 * anything stored.
 *
 * <p>A save never reaches an environment variable, and a remove clears every tier that can hold a
 * value. Clearing only the tier that answered would expose an older value underneath it.
 */
public interface SecretStore {

    // A credential is a token the issuing service mints, not prose. The longest any of them mints
    // runs to a few hundred characters, and the tightest store reached here is the Windows
    // credential blob at 2,560 bytes. So the ceiling sits below every store's own capacity, which
    // is what keeps a refusal this library's sentence rather than the platform's. It counts
    // characters against a byte cap, so a credential near the ceiling and largely outside ASCII
    // can still be refused by Windows instead. Held here rather than on a value type because a
    // credential never becomes one. It goes from a caller's input to a tier, and nothing in
    // between models it.
    /**
     * The character ceiling a credential is measured against, once stripped.
     */
    int MAX_SECRET = 1024;

    /**
     * The longest a credential may be, measured after surrounding whitespace is stripped. For a
     * control that stops a reader typing past it.
     *
     * @return int the character ceiling
     */
    static int maxSecret() {
        return MAX_SECRET;
    }

    /**
     * Starts building a store for the named application.
     *
     * <p>The name is what a user browsing their own credential store sees.
     *
     * @param applicationName {@link String} the consumer's own name
     * @return {@link Builder} the builder to name the rest on and open
     * @throws IllegalArgumentException when the name is blank
     */
    static Builder forApplication(final String applicationName) {
        return new SecretStoreBuilder(applicationName);
    }

    /**
     * Collects the places a store reads from, then opens it.
     *
     * <p>The two tiers that hold a credential in the clear are opt-in. An environment variable is
     * plaintext by nature, and a credential file is unencrypted at rest. A consumer under a policy
     * against either has to be able to leave it out, so neither is on until a call here names it.
     * The machine's own credential store is not switchable.
     *
     * <p>So a store naming neither plaintext tier, on a machine with no credential store, reads
     * nothing and refuses every save. {@link SecretStore#whereASaveWouldStoreIt} answers that in
     * advance; {@link #open} does not refuse the configuration.
     *
     * <p>The application name and the namespace decide every native key, by a fixed derivation.
     * The Windows target is {@code <applicationName>:<name>}, with {@code <name>} also written as
     * the entry's account name. The macOS service is {@code <applicationName>}, its account is
     * {@code <name>} and its label {@code <applicationName>:<name>}. The Secret Service schema is
     * {@code <namespace>.Credential}, its attribute is {@code name=<name>} and its label
     * {@code <applicationName>:<name>}. The file is {@code <name>} plus the tier's own suffix.
     * Here {@code <name>} is the {@link SecretId}'s. Passing either input differently reaches
     * different entries, so a caller that changes one stops finding what it stored.
     */
    interface Builder {

        /**
         * The namespace the Secret Service schema is built from, and the one input with no default.
         *
         * @param namespace {@link String} the consumer's reverse-domain namespace
         * @return {@link Builder} this builder
         * @throws IllegalArgumentException when the namespace is blank
         */
        Builder inNamespace(String namespace);

        /**
         * Reads the process's environment ahead of every stored place, under the variable name
         * each {@link SecretId} carries.
         *
         * @return {@link Builder} this builder
         */
        Builder withEnvironmentOverride();

        /**
         * Keeps credentials in permission-restricted files in the given directory, below whatever
         * credential store the machine offers.
         *
         * @param directory {@link Path} the directory holding one file per credential
         * @return {@link Builder} this builder
         */
        Builder withCredentialFilesIn(Path directory);

        /**
         * Decides which platform's credential store to look for, rather than asking this machine.
         *
         * @param osName {@link String} the raw OS name, as the os.name system property reports it
         * @return {@link Builder} this builder
         */
        Builder onOperatingSystem(String osName);

        /**
         * Opens the store over the tiers named so far, plus this machine's credential store where
         * it offers one.
         *
         * @return {@link SecretStore} the credential store
         * @throws IllegalStateException when no namespace was named
         */
        SecretStore open();
    }

    /**
     * The credential in force for the given id, from whichever tier answers first.
     *
     * @param id {@link SecretId} which credential to read
     * @return an {@link Optional} of {@link String}, empty when no tier holds one
     * @throws SecretStoreException when a tier cannot determine what it holds
     */
    Optional<String> secret(SecretId id);

    /**
     * Which tier answers for the given id, without reporting what it holds.
     *
     * @param id {@link SecretId} which credential to report on
     * @return {@link SecretStatus} the answering tier, or absent when none holds a value
     * @throws SecretStoreException when a tier cannot determine what it holds
     */
    SecretStatus status(SecretId id);

    /**
     * What every place holds for the given id, in the order a read consults them, carrying no
     * credential.
     *
     * <p>{@link #status} answers which place wins. This answers all of them, which is what lets a
     * caller say a fresh credential is shadowed by an older one somewhere above it. Reading it
     * back and comparing is the only way to know they differ, and no surface here is ever handed a
     * value to compare.
     *
     * <p>A place that refuses the question is reported as such rather than raised, because one
     * broken place would otherwise hide every healthy one's answer. That is the machine this exists
     * for, and it is why this can be called beside a {@link #status} that threw.
     *
     * <p>What it does not swallow is a place that cannot say what it is. Every entry names a real
     * place, which is what lets a caller count an unaskable one rather than leave it out silently.
     *
     * @param id {@link SecretId} which credential to report on
     * @return a {@link List} of {@link SecretHolding} one entry per place, in read order
     */
    List<SecretHolding> holdings(SecretId id);

    /**
     * Where {@link #save} would put a credential on this machine right now, or empty when no place
     * can take one.
     *
     * <p>For the sentence shown beside an entry field, before anything is typed. {@link #status}
     * cannot answer it: that reports where a credential already is, and says nothing about the
     * keyring when the answer is {@link SecretStatus.Absent}.
     *
     * <p>Takes no id, because nothing about the routing depends on one. It is decided by which
     * places this machine offers.
     *
     * @return an {@link Optional} of {@link SecretStatus.StoredLocation} where a save would land
     */
    Optional<SecretStatus.StoredLocation> whereASaveWouldStoreIt();

    /**
     * Stores the given credential in the strongest tier this machine offers that can be written.
     * An environment variable already naming the same credential keeps winning every read, and the
     * caller learns that by asking {@link #status} afterwards.
     *
     * <p>A blank credential is refused before any tier is reached. Storing one would replace a
     * working credential with something no service can accept, and the failure would surface later
     * as a rejected call rather than here.
     *
     * <p>What is stored is the credential stripped of surrounding whitespace, so a caller that
     * reads it back gets that rather than what it passed. A key pasted out of a browser or a
     * terminal carries whatever came with it, and the issuing service counts that as part of it.
     *
     * <p>A credential longer than {@link #maxSecret} is refused, measured on the stripped value,
     * since that is what a tier is asked to hold. An entry field stops one being typed. This is
     * what stops a caller with no field supplying one. It also keeps the refusal this library's own
     * sentence, rather than whatever the platform store says when it runs out of room.
     *
     * <p>Nothing is cleared until the write succeeds, so a failed save can never lose a credential
     * already stored. Once it does, every writable tier that outranks the one just written to is
     * asked to clear what it holds. An environment variable is never touched by a save, matching
     * {@link #remove}.
     *
     * <p>That clearing is best effort, and its limit is worth knowing. A tier only outranks the one
     * written to when it could not be written to itself, which on this machine means it could not be
     * reached. A store that cannot be reached cannot be cleared either, and reports nothing. So the
     * case where a stale value survives a save is exactly the case this cannot fix, and the save
     * reports success. What settles which value is really in force is {@link #status}.
     *
     * @param id {@link SecretId} which credential to store
     * @param secret {@link String} the credential to store
     * @throws IllegalArgumentException when the credential is blank, or longer than
     *         {@link #maxSecret} once stripped
     * @throws StaleSecretNotClearedException when the value was stored and a stale copy above it
     *         could not be cleared, so that copy may still answer a read
     * @throws SecretStoreException when no tier on this machine can store one, or when the tier
     *         that took it refused
     */
    void save(SecretId id, String secret);

    /**
     * Clears the given credential from every tier that can hold one. An environment variable is
     * left alone, so a credential named by one still answers afterwards.
     *
     * @param id {@link SecretId} which credential to clear
     * @throws SecretStoreException when a tier holding a value could not clear it
     */
    void remove(SecretId id);
}
