package photos.sluice.secrets.platform;

/**
 * Binds one platform's credential store, whichever platform the caller is asking about.
 *
 * <p>The only way to obtain a binding. Each implementation stays package-private, so no native
 * binding's signature is a compatibility promise.
 */
public final class PlatformBindings {

    /**
     * Prevents instantiation of this static utility class.
     */
    private PlatformBindings() {}

    /**
     * Binds the Windows Credential Manager through {@code advapi32}.
     *
     * @return {@link WindowsCredentialManager} the bound credential store
     * @throws IllegalArgumentException when this machine has no {@code advapi32} to load, or it
     *         does not export a function the binding names
     * @throws UnsatisfiedLinkError when the library is present but cannot be loaded
     */
    public static WindowsCredentialManager openWindowsCredentialManager() {
        return Advapi32CredentialManager.open();
    }

    /**
     * Binds the macOS keychain through Security.framework.
     *
     * @param service {@link String} the service attribute every entry carries, which groups the
     *         consumer's credentials under one heading in Keychain Access
     * @return {@link MacKeychain} the bound keychain
     * @throws IllegalArgumentException when this machine is missing either framework, or one of
     *         them does not export a name the binding reads
     * @throws UnsatisfiedLinkError when a framework is present but cannot be loaded
     */
    public static MacKeychain openMacKeychain(final String service) {
        return SecurityFrameworkKeychain.open(service);
    }

    /**
     * Binds the freedesktop Secret Service through libsecret.
     *
     * @param namespace {@link String} the consumer's reverse-domain namespace, which the schema
     *         every entry carries is built from
     * @return {@link LinuxSecretService} the bound service
     * @throws IllegalArgumentException when this machine is missing any of the three libraries, or
     *         one of them does not export a function the binding names
     * @throws UnsatisfiedLinkError when a library is present but cannot be loaded
     */
    public static LinuxSecretService openLinuxSecretService(final String namespace) {
        return LibsecretService.open(namespace);
    }
}
