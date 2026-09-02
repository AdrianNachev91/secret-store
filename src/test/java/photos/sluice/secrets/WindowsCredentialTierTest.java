package photos.sluice.secrets;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import photos.sluice.secrets.platform.WindowsCredentialManager;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// A fake credential store rather than the real one, so every case here runs on every runner. What
// the real Windows Credential Manager does with these calls is Advapi32CredentialManagerTest's
// subject, and it can only be asked on Windows.
class WindowsCredentialTierTest {

    // A fixture consumer name rather than a real one. Every entry name below starts with it, so a
    // tier that hardcoded a prefix instead of reading its constructor would fail all of them.
    private static final String APPLICATION = "Testbed";

    private static final SecretId ANTHROPIC = new SecretId("anthropic", "ANTHROPIC_API_KEY");
    private static final SecretId OTHER = new SecretId("other-name", "OTHER_API_KEY");

    @Nested
    class Read {

        @Test
        void answersWithTheStoredCredential() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "sk-synthetic-0001");

            assertThat(tier(credentials).read(ANTHROPIC)).contains("sk-synthetic-0001");
        }

        @Test
        void answersWithNothingWhenTheStoreHoldsNoEntry() {
            assertThat(tier(new FakeCredentialManager()).read(ANTHROPIC)).isEmpty();
        }

        // The entry name carries the credential name, so one credential cannot answer for
        // another's. A tier naming every entry the same way would pass every other test here.
        @Test
        void neverAnswersWithAnotherCredentialsValue() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "sk-synthetic-0001");

            assertThat(tier(credentials).read(OTHER)).isEmpty();
        }

        // A credential is stored stripped, so whitespace can only arrive here from an entry someone
        // wrote by hand. It would still travel to the issuing service as part of the credential.
        @Test
        void stripsSurroundingWhitespaceOffTheStoredValue() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "  sk-synthetic-0001\n");

            assertThat(tier(credentials).read(ANTHROPIC)).contains("sk-synthetic-0001");
        }

        @Test
        void treatsAnEntryHoldingOnlyWhitespaceAsHoldingNothing() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", " \n ");

            assertThat(tier(credentials).read(ANTHROPIC)).isEmpty();
        }

        @Test
        void treatsAnEmptyEntryAsHoldingNothing() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "");

            assertThat(tier(credentials).read(ANTHROPIC)).isEmpty();
        }

        // Nothing stops an issuing service minting a credential with characters outside the ASCII
        // range. An encode and a decode that disagree would corrupt one silently rather than fail.
        @Test
        void roundTripsACredentialCarryingCharactersOutsideAscii() {
            final var credentials = new FakeCredentialManager();
            final var tier = tier(credentials);

            tier.write(ANTHROPIC, "sk-synthetic-éü-0001");

            assertThat(tier.read(ANTHROPIC)).contains("sk-synthetic-éü-0001");
        }

        // A store that answers other calls and refused this one is a broken install. Reporting that
        // as an absent credential would send the user to re-enter a key that is already stored.
        @Test
        void failsLoudWhenAWorkingStoreRefusesTheRead() {
            final var credentials = new FakeCredentialManager();
            credentials.refuseOnly("Testbed:anthropic");

            assertThatThrownBy(() -> tier(credentials).read(ANTHROPIC))
                    .isInstanceOf(SecretStoreException.class);
        }

        // A store refusing every call holds no credential this tier failed to fetch, so it cannot
        // be hiding a newer one than the file tier below. Throwing here would stop that file ever
        // being reached, while the same machine's save is routed to it. Such a machine could then
        // store a credential and never read it back.
        @Test
        void answersWithNothingWhenTheWholeStoreIsUnusableHere() {
            final var credentials = new FakeCredentialManager();
            credentials.refuseEverything();

            assertThat(tier(credentials).read(ANTHROPIC)).isEmpty();
        }
    }

    @Nested
    class Holds {

        @Test
        void answersTrueOnlyWhenTheStoreHoldsACredential() {
            final var credentials = new FakeCredentialManager();

            assertThat(tier(credentials).holds(ANTHROPIC)).isFalse();

            credentials.store("Testbed:anthropic", "sk-synthetic-0001");

            assertThat(tier(credentials).holds(ANTHROPIC)).isTrue();
        }

        // Whitespace is not a credential on the way out of any tier, so it cannot be one here
        // either. A status line would otherwise report a credential no service would accept.
        @Test
        void answersFalseForAnEntryHoldingOnlyWhitespace() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "   ");

            assertThat(tier(credentials).holds(ANTHROPIC)).isFalse();
        }

        @Test
        void failsLoudWhenAWorkingStoreRefusesTheRead() {
            final var credentials = new FakeCredentialManager();
            credentials.refuseOnly("Testbed:anthropic");

            assertThatThrownBy(() -> tier(credentials).holds(ANTHROPIC))
                    .isInstanceOf(SecretStoreException.class);
        }
    }

    @Nested
    class Availability {

        @Test
        void answersAvailableWhenTheStoreCanBeReached() {
            assertThat(tier(new FakeCredentialManager()).available()).isTrue();
        }

        // A store refusing every call is what a process with no user profile loaded sees. The save
        // then routes down to the file tier, so such a machine can still keep a credential.
        @Test
        void answersUnavailableWhenTheStoreRefusesEveryCall() {
            final var credentials = new FakeCredentialManager();
            credentials.refuseEverything();

            assertThat(tier(credentials).available()).isFalse();
        }

        // The probe asks about a name nothing writes. Reaching for a stored credential's entry would
        // make availability depend on whether a credential happens to be stored.
        @Test
        void neverReadsAStoredCredentialToDecideAvailability() {
            final var credentials = new FakeCredentialManager();

            tier(credentials).available();

            assertThat(credentials.namesRead).doesNotContain("Testbed:anthropic");
            assertThat(credentials.namesRead).isNotEmpty();
        }
    }

    @Nested
    class Registration {

        @Test
        void outranksTheFileTier() {
            assertThat(WindowsCredentialTier.PRECEDENCE).isGreaterThan(FileSecretTier.PRECEDENCE);
        }

        @Test
        void namesItselfTheKeyring() {
            assertThat(tier(new FakeCredentialManager()).storedLocation())
                    .isEqualTo(new SecretStatus.InKeyring());
        }
    }

    @Nested
    class Write {

        @Test
        void storesTheCredentialUnderItsOwnEntry() {
            final var credentials = new FakeCredentialManager();

            tier(credentials).write(ANTHROPIC, "sk-synthetic-0001");

            assertThat(credentials.entries).containsEntry("Testbed:anthropic", "sk-synthetic-0001");
        }

        // Pinned against the literal rather than against the constructor's own input. A consumer's
        // existing entries are found by a name of this shape. Changing the shape leaves every one
        // of them unreachable, and a lookup that misses reports the credential absent rather than
        // failing.
        @Test
        void buildsTheTargetAsTheApplicationNameThenTheCredentialName() {
            final var credentials = new FakeCredentialManager();

            new WindowsCredentialTier(credentials, "Sluice").write(ANTHROPIC, "sk-synthetic-0001");

            assertThat(credentials.entries).containsOnlyKeys("Sluice:anthropic");
        }

        // A user browsing their own credentials sees the account name, so it names the credential
        // rather than being left blank.
        @Test
        void showsTheCredentialsNameAsTheEntrysAccountName() {
            final var credentials = new FakeCredentialManager();

            tier(credentials).write(ANTHROPIC, "sk-synthetic-0001");

            assertThat(credentials.userNames).containsEntry("Testbed:anthropic", "anthropic");
        }

        @Test
        void replacesACredentialTheStoreAlreadyHeld() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "sk-synthetic-0001");

            tier(credentials).write(ANTHROPIC, "sk-synthetic-0002");

            assertThat(credentials.entries).containsEntry("Testbed:anthropic", "sk-synthetic-0002");
        }

        // A save that silently did nothing is the worst outcome available here, because the user
        // walks away believing their key is stored.
        // A working store refusing one entry, rather than a store refusing everything. The store
        // above only routes a save here once availability has answered, so a comprehensively broken
        // store is a state this method never sees in production.
        @Test
        void failsLoudWhenAWorkingStoreRefusesTheWrite() {
            final var credentials = new FakeCredentialManager();
            credentials.refuseOnly("Testbed:anthropic");

            assertThatThrownBy(() -> tier(credentials).write(ANTHROPIC, "sk-synthetic-0001"))
                    .isInstanceOf(SecretStoreException.class);
        }
    }

    @Nested
    class Erase {

        @Test
        void clearsTheCredentialsEntry() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "sk-synthetic-0001");

            tier(credentials).erase(ANTHROPIC);

            assertThat(credentials.entries).doesNotContainKey("Testbed:anthropic");
        }

        @Test
        void leavesAnotherCredentialAlone() {
            final var credentials = new FakeCredentialManager();
            credentials.store("Testbed:anthropic", "sk-synthetic-0001");
            credentials.store("Testbed:other-name", "sk-synthetic-0002");

            tier(credentials).erase(ANTHROPIC);

            assertThat(credentials.entries).containsOnlyKeys("Testbed:other-name");
        }

        // The store above clears every writable tier unconditionally, so this one is asked to clear
        // a credential it never held on any removal at all.
        @Test
        void clearingWhatTheStoreDoesNotHoldIsNotAnError() {
            final var credentials = new FakeCredentialManager();

            tier(credentials).erase(ANTHROPIC);

            assertThat(credentials.entries).isEmpty();
        }

        @Test
        void failsLoudWhenAWorkingStoreRefusesTheRemoval() {
            final var credentials = new FakeCredentialManager();
            credentials.refuseOnly("Testbed:anthropic");

            assertThatThrownBy(() -> tier(credentials).erase(ANTHROPIC))
                    .isInstanceOf(SecretStoreException.class);
        }

        // The removal above this walks every writable tier and reports that a credential may still
        // answer a read. On a machine whose store holds nothing that sentence is false: the file
        // tier was cleared, and this tier never had anything to clear. Reporting a failure here
        // would put that false sentence in front of the user on every single removal.
        @Test
        void clearingIsNotAFailureWhenTheWholeStoreIsUnusableHere() {
            final var credentials = new FakeCredentialManager();
            credentials.refuseEverything();

            tier(credentials).erase(ANTHROPIC);
        }
    }

    private static WindowsCredentialTier tier(final WindowsCredentialManager credentials) {
        return new WindowsCredentialTier(credentials, APPLICATION);
    }

    // Stands in for the Windows Credential Manager, holding entries by name the way it does. A
    // missing entry answers empty on a read and passes silently on a delete. That is what the real
    // store's error codes amount to once its own interface has interpreted them.
    private static final class FakeCredentialManager implements WindowsCredentialManager {

        private final Map<String, String> entries = new HashMap<>();
        private final Map<String, String> userNames = new HashMap<>();
        private final Set<String> namesRead = new HashSet<>();
        private final Set<String> refusedNames = new HashSet<>();
        private boolean refusing;

        @Override
        public Optional<byte[]> read(final String target) {
            this.refuseIfBroken();
            this.refuseIfNamed(target);
            this.namesRead.add(target);
            return Optional.ofNullable(this.entries.get(target))
                    .map(stored -> stored.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void write(final String target, final String userName, final byte[] secret) {
            this.refuseIfBroken();
            this.refuseIfNamed(target);
            this.entries.put(target, new String(secret, StandardCharsets.UTF_8));
            this.userNames.put(target, userName);
        }

        @Override
        public void delete(final String target) {
            this.refuseIfBroken();
            this.refuseIfNamed(target);
            this.entries.remove(target);
        }

        private void store(final String target, final String secret) {
            this.entries.put(target, secret);
        }

        private void refuseEverything() {
            this.refusing = true;
        }

        // A store that works but fails one entry, which is what a broken install looks like. It is
        // the case the availability probe has to tell apart from a store that answers nothing.
        private void refuseOnly(final String target) {
            this.refusedNames.add(target);
        }

        private void refuseIfBroken() {
            if (this.refusing) {
                throw new SecretStoreException(SecretStoreException.Tier.KEYRING,
                        "this credential store refuses every call");
            }
        }

        private void refuseIfNamed(final String target) {
            if (this.refusedNames.contains(target)) {
                throw new SecretStoreException(SecretStoreException.Tier.KEYRING,
                        "this credential store cannot read " + target);
            }
        }
    }
}
