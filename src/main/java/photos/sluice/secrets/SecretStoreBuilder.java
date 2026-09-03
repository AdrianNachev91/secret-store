package photos.sluice.secrets;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Gathers what a store is made of and builds it.
 *
 * <p>Mutable and not safe to share across threads.
 *
 * <p>A place is on exactly when a call named it. The file place is named by its directory, so it
 * cannot be switched on with nowhere to write, and no input can be set and unread.
 */
final class SecretStoreBuilder implements SecretStore.Builder {

    private final String applicationName;

    private @Nullable String namespace;
    private boolean readsEnvironment;
    private @Nullable Path credentialFiles;
    private String osName = System.getProperty("os.name");

    /**
     * Creates the builder for the named application.
     *
     * @param applicationName {@link String} the consumer's own name
     * @throws IllegalArgumentException when the name is blank
     */
    SecretStoreBuilder(final String applicationName) {
        if (applicationName.isBlank()) {
            throw new IllegalArgumentException("A secret store needs an application name");
        }
        this.applicationName = applicationName;
    }

    @Override
    public SecretStore.Builder inNamespace(final String namespace) {
        if (namespace.isBlank()) {
            throw new IllegalArgumentException("A secret store needs a namespace");
        }
        this.namespace = namespace;
        return this;
    }

    @Override
    public SecretStore.Builder withEnvironmentOverride() {
        this.readsEnvironment = true;
        return this;
    }

    // Both refuse null rather than storing it. A stored null would read as the place never having
    // been asked for. A caller that computed a directory and got nothing back would then be told
    // its machine can store no credential, rather than that its argument was empty.
    @Override
    public SecretStore.Builder withCredentialFilesIn(final Path directory) {
        this.credentialFiles = Objects.requireNonNull(directory, "credential directory");
        return this;
    }

    @Override
    public SecretStore.Builder onOperatingSystem(final String osName) {
        this.osName = Objects.requireNonNull(osName, "operating system name");
        return this;
    }

    @Override
    public SecretStore open() {
        final String reverseDomain = this.namespace;
        if (reverseDomain == null) {
            throw new IllegalStateException("The secret store for '" + this.applicationName
                    + "' was opened without a namespace, which the Secret Service schema is built"
                    + " from. Call inNamespace before open.");
        }
        final List<WritableSecretTier> tiers = new ArrayList<>(2);
        PlatformKeyring.forThisMachine(this.applicationName, reverseDomain, this.osName)
                .ifPresent(tiers::add);
        if (this.credentialFiles != null) {
            tiers.add(new FileSecretTier(this.credentialFiles));
        }
        if (!this.readsEnvironment) {
            return new TieredSecretStore(tiers);
        }
        return new TieredSecretStore(new EnvironmentSecretTier(System::getenv), tiers);
    }
}
