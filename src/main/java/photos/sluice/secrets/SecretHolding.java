package photos.sluice.secrets;

/**
 * What one place holds for a credential, carrying no credential.
 *
 * <p>{@link SecretStatus} answers which place is in force. This answers all of them, so a caller
 * can say that more than one place holds a value for one credential and which wins. It cannot say
 * they differ: nothing here compares values, by the same design that keeps a credential out of
 * {@link SecretStatus}.
 */
public record SecretHolding(SecretStatus.Location location, Holding holding) {

    /**
     * What a place answered when asked.
     */
    public enum Holding {

        /**
         * A credential for this id is there.
         */
        HOLDS,

        /**
         * The place was asked and holds nothing for this id.
         */
        EMPTY,

        /**
         * The place refused the question. A machine's credential store can be reachable and still
         * refuse one entry, and a protected file can be unreadable. Neither is absence, and a
         * caller counting holders has to say so rather than count the place as empty.
         */
        COULD_NOT_BE_ASKED
    }
}
