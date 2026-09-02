package photos.sluice.secrets;

import java.util.regex.Pattern;

/**
 * Names one credential, and the environment variable that overrides it.
 *
 * <p>Whatever needs a credential builds its own. Nothing central maps names to credentials, so a
 * second one means the code that needs it naming its own rather than editing a registry somewhere
 * else.
 *
 * <p>The environment variable name travels with the id because a status line quotes it back to the
 * user, and it differs per credential.
 *
 * @param name {@link String} what this credential is called
 * @param environmentVariable {@link String} name of the environment variable that overrides it
 */
public record SecretId(String name, String environmentVariable) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9-]+");

    /**
     * Refuses an id missing either half, or naming a credential that cannot safely become a
     * filename. Both halves locate a stored value, so neither can be absent without the lookup
     * silently reading somewhere else.
     *
     * <p>The name becomes a path segment under the credential directory and part of an entry name
     * in the operating system's own store, and the caller chooses it. Anything outside lower-case
     * letters, digits and hyphens is refused, which leaves no separator or parent reference to
     * escape that directory with.
     *
     * <p>A name matching a Windows device is refused too, for the reason and on the list
     * {@link ReservedDeviceNames} states.
     *
     * @param name {@link String} what this credential is called
     * @param environmentVariable {@link String} name of the environment variable that overrides it
     */
    public SecretId {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Secret id needs a name");
        }
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Secret id name '" + name
                    + "' may hold only lower-case letters, digits and hyphens");
        }
        if (ReservedDeviceNames.isReserved(name)) {
            throw new IllegalArgumentException("Secret id name '" + name
                    + "' is a reserved device name on Windows and cannot become a file there");
        }
        if (environmentVariable.isBlank()) {
            throw new IllegalArgumentException(
                    "Secret id for credential '" + name + "' needs an environment variable name");
        }
    }
}
