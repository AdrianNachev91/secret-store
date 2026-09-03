package photos.sluice.secrets.example;

import photos.sluice.secrets.SecretHolding;
import photos.sluice.secrets.SecretId;
import photos.sluice.secrets.SecretStatus;
import photos.sluice.secrets.SecretStore;

import java.nio.file.Path;

/**
 * Stores, reads, locates and clears one credential from the command line.
 *
 * <p>Built the way a real consumer composes the store: one builder call held for the process, and
 * the four operations against it.
 */
public final class Main {

    private static final SecretId ANTHROPIC = new SecretId("anthropic", "ANTHROPIC_API_KEY");

    /**
     * Prevents instantiation of this entry-point class.
     */
    private Main() {}

    /**
     * Runs one command against the store.
     *
     * @param args {@link String} array, the command and, for {@code store}, the credential
     */
    static void main(final String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: store <credential> | read | where | forget");
            return;
        }
        final SecretStore store = SecretStore.forApplication("SecretStoreExample")
                .inNamespace("photos.sluice.example")
                .withEnvironmentOverride()
                .withCredentialFilesIn(
                        Path.of(System.getProperty("user.home"), ".secret-store-example"))
                .open();

        switch (args[0]) {
            case "store" -> store(store, args);
            case "read" -> read(store);
            case "where" -> where(store);
            case "forget" -> forget(store);
            default -> System.out.println("Unknown command: " + args[0]);
        }
    }

    /**
     * Stores the credential given on the command line, then says where it landed.
     *
     * @param store {@link SecretStore} the store to write to
     * @param args {@link String} array, whose second element is the credential
     */
    private static void store(final SecretStore store, final String[] args) {
        if (args.length < 2) {
            System.out.println("store needs the credential to store");
            return;
        }
        store.save(ANTHROPIC, args[1]);
        System.out.println("Stored. In force: " + describe(store.status(ANTHROPIC)));
    }

    /**
     * Reads the credential and says which place answered, never printing the value.
     *
     * @param store {@link SecretStore} the store to read from
     */
    private static void read(final SecretStore store) {
        final SecretStatus status = store.status(ANTHROPIC);
        if (status instanceof SecretStatus.Absent) {
            System.out.println("No credential stored.");
            return;
        }
        System.out.println("Found " + store.secret(ANTHROPIC).orElseThrow().length()
                + " characters, from " + describe(status));
    }

    /**
     * Lists every place, and where a save would go next.
     *
     * @param store {@link SecretStore} the store to report on
     */
    private static void where(final SecretStore store) {
        for (final SecretHolding holding : store.holdings(ANTHROPIC)) {
            System.out.println("  " + describe(holding.location()) + ": " + holding.holding());
        }
        System.out.println("A save would go to "
                + store.whereASaveWouldStoreIt().map(Main::describe).orElse("nowhere on this machine"));
    }

    /**
     * Clears the credential from every place that can hold one.
     *
     * @param store {@link SecretStore} the store to clear
     */
    private static void forget(final SecretStore store) {
        store.remove(ANTHROPIC);
        System.out.println("Cleared. In force: " + describe(store.status(ANTHROPIC)));
    }

    /**
     * Names a status for a reader.
     *
     * @param status {@link SecretStatus} the status to name
     * @return {@link String} what to print for it
     */
    private static String describe(final SecretStatus status) {
        return switch (status) {
            case SecretStatus.InEnvironment inEnvironment ->
                    "the environment variable " + inEnvironment.variableName();
            case SecretStatus.InKeyring ignored -> "this machine's credential store";
            case SecretStatus.InFile ignored -> "the protected file";
            case SecretStatus.Absent ignored -> "nowhere";
        };
    }
}
