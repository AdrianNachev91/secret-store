package photos.sluice.secrets;

import photos.sluice.secrets.platform.LinuxSecretService;
import photos.sluice.secrets.platform.MacKeychain;
import photos.sluice.secrets.platform.PlatformBindings;
import photos.sluice.secrets.platform.WindowsCredentialManager;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Picks the credential store this machine's operating system offers, where it offers one.
 *
 * <p>The one place a platform's credential store is registered. All three desktop platforms have a
 * binding. A machine gets no keyring tier where none is written for its platform, a BSD or a
 * Solaris, or where its own platform's library will not load. The store above then falls through
 * to whatever writable tier its consumer named. That is a working install rather than a degraded
 * one.
 */
final class PlatformKeyring {

    private static final Logger log = System.getLogger(PlatformKeyring.class.getName());

    /**
     * Prevents instantiation of this static utility class.
     */
    private PlatformKeyring() {}

    /**
     * Resolves the credential store tier for the given operating system.
     *
     * @param applicationName {@link String} the consumer's name, which every platform's entry name
     *         is built from
     * @param namespace {@link String} the consumer's reverse-domain namespace, which the Secret
     *         Service schema is built from
     * @param osName {@link String} the raw OS name (e.g. system property os.name)
     * @return an {@link Optional} of {@link WritableSecretTier}, empty where this platform offers no
     *         credential store this library can reach
     */
    static Optional<WritableSecretTier> forThisMachine(final String applicationName,
            final String namespace, final String osName) {
        if (isWindows(osName)) {
            return windowsCredentialManager()
                    .map(credentials -> new WindowsCredentialTier(credentials, applicationName));
        }
        if (isLinux(osName)) {
            return linuxSecretService(namespace)
                    .map(secrets -> new LinuxSecretServiceTier(secrets, applicationName));
        }
        if (isMac(osName)) {
            return macKeychain(applicationName)
                    .map(keychain -> new MacKeychainTier(keychain, applicationName));
        }
        return Optional.empty();
    }

    /**
     * Whether the given operating system name is a Windows one.
     *
     * <p>Matched on a substring rather than on a list of releases. The name carries its release
     * with it, and every release is a fresh string. Windows 10, Windows Server 2022 and anything
     * later all read the same way here.
     *
     * <p>Matched on the full word rather than on "win". A shorter substring is the trap here:
     * "Darwin", the name macOS's own tools print for its kernel, carries "win" inside it. Nothing
     * guarantees a JVM never reports an OS name containing it, and reading one as Windows would
     * route a save at the wrong platform's credential store.
     *
     * @param osName {@link String} the raw OS name (e.g. system property os.name)
     * @return boolean true when the name is a Windows one
     */
    static boolean isWindows(final String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("windows");
    }

    /**
     * Whether the given operating system name is a Linux one.
     *
     * <p>The JVM reports plain {@code Linux} there, with no release riding along.
     *
     * @param osName {@link String} the raw OS name (e.g. system property os.name)
     * @return boolean true when the name is a Linux one
     */
    static boolean isLinux(final String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("linux");
    }

    /**
     * Whether the given operating system name is a macOS one.
     *
     * <p>The JVM reports {@code Mac OS X} there, and has done across every release this ships to.
     * The version rides in a separate property rather than in this string, so the name has stayed
     * put while the product name changed around it.
     *
     * @param osName {@link String} the raw OS name (e.g. system property os.name)
     * @return boolean true when the name is a macOS one
     */
    static boolean isMac(final String osName) {
        return osName.toLowerCase(Locale.ROOT).contains("mac");
    }

    /**
     * Binds the Windows Credential Manager, or answers with nothing where this machine has none.
     *
     * @return an {@link Optional} of {@link WindowsCredentialManager}, empty where this machine has
     *         no Windows Credential Manager to bind
     */
    private static Optional<WindowsCredentialManager> windowsCredentialManager() {
        return bindOrExplain(PlatformBindings::openWindowsCredentialManager);
    }

    /**
     * Binds the Secret Service through libsecret, or answers with nothing where this machine has
     * none. A Linux install without a desktop keyring is ordinary, a server most of all. Such a
     * machine is working rather than broken, and keeps its credential in the protected file where
     * the consumer asked for one.
     *
     * @param namespace {@link String} the consumer's reverse-domain namespace, which the schema
     *         every call carries is built from
     * @return an {@link Optional} of {@link LinuxSecretService}, empty where this machine has no
     *         Secret Service to bind
     */
    private static Optional<LinuxSecretService> linuxSecretService(final String namespace) {
        return bindOrExplain(() -> PlatformBindings.openLinuxSecretService(namespace));
    }

    /**
     * Binds the macOS keychain through Security.framework, or answers with nothing where this
     * machine will not load it.
     *
     * @param applicationName {@link String} the consumer's name, which the keychain service
     *         attribute grouping its entries is built from
     * @return an {@link Optional} of {@link MacKeychain}, empty where this machine has no keychain
     *         to bind
     */
    private static Optional<MacKeychain> macKeychain(final String applicationName) {
        return bindOrExplain(() -> PlatformBindings.openMacKeychain(applicationName));
    }

    /**
     * Binds a platform's credential store, or answers with nothing and says why.
     *
     * <p>The reason is recorded rather than swallowed, because the ways a binding fails are worth
     * telling apart and only the failure itself knows which happened. A machine without the
     * library reports that it could not be loaded. An installation too old to carry a function
     * this library needs reports which symbol it lacks. That is the difference between "no keyring
     * here" and "your keyring is older than this library supports". Both leave the machine on the
     * protected file, which works, so this is a diagnosis rather than a fault.
     *
     * @param binding a {@link Supplier} that binds one platform's store, or throws
     * @param <T> the bound store's own type
     * @return an {@link Optional} of the bound store, empty where this machine has none
     */
    private static <T> Optional<T> bindOrExplain(final Supplier<T> binding) {
        try {
            return Optional.of(binding.get());
        } catch (final IllegalArgumentException | UnsatisfiedLinkError absent) {
            log.log(Level.INFO, "Could not bind a credential store on this machine, so credentials"
                    + " go to the protected file instead. Reason: {0}", absent.getMessage());
            return Optional.empty();
        }
    }
}
