package photos.sluice.secrets;

/**
 * Which tier answers for a credential, carrying no credential.
 *
 * <p>A caller reporting where a credential lives renders a different sentence per variant, and
 * never shows the value itself. Keeping the value out of this type entirely means such a caller
 * cannot leak one it was never handed.
 *
 * <p>Absence is a variant rather than an empty result, so every caller has to say what it renders
 * when nothing is stored.
 */
public sealed interface SecretStatus {

    /**
     * A place a credential can sit, named rather than reported on. Every variant below except
     * {@link Absent} is one. So a caller that has to name a place has a type for it, and absence
     * cannot reach that caller at all.
     *
     * <p>Naming a place says nothing about whether a credential is there. That is what lets
     * {@link SecretStore#holdings} report an empty place and a place it could not ask.
     */
    sealed interface Location extends SecretStatus {
    }

    /**
     * A place a save can put a credential. The environment is the one place that is not, since
     * nothing this library does sets a variable in the session that started the process.
     */
    sealed interface StoredLocation extends Location {
    }

    /**
     * An environment variable answers, so it overrides anything saved. Saving or removing while
     * this is the status changes nothing a read can see. That is the one case a caller has to
     * explain rather than just report.
     *
     * @param variableName {@link String} name of the environment variable holding the value
     */
    record InEnvironment(String variableName) implements Location {
    }

    /**
     * The machine's own credential store answers.
     */
    record InKeyring() implements StoredLocation {
    }

    /**
     * A protected file in the directory the caller named answers, the fallback where no credential
     * store does. It is protected by file permissions rather than encryption, so the sentence a
     * caller shows for it cannot be the one it shows for a keyring.
     */
    record InFile() implements StoredLocation {
    }

    /**
     * No tier holds a value.
     */
    record Absent() implements SecretStatus {
    }
}
