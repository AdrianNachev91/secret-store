package photos.sluice.secrets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Both halves are used to locate a stored value. An id missing either would let a lookup read
// somewhere else without saying so, which is why the refusal sits in the constructor.
class SecretIdTest {

    @Test
    void keepsBothHalvesOfAValidId() {
        final var id = new SecretId("anthropic", "ANTHROPIC_API_KEY");

        assertThat(id.name()).isEqualTo("anthropic");
        assertThat(id.environmentVariable()).isEqualTo("ANTHROPIC_API_KEY");
    }

    @Test
    void refusesAnIdWithNoName() {
        assertThatThrownBy(() -> new SecretId("   ", "ANTHROPIC_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    // The message names the credential, since that is the half still present to identify the id by.
    @Test
    void refusesAnIdWithNoEnvironmentVariableName() {
        assertThatThrownBy(() -> new SecretId("anthropic", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anthropic")
                .hasMessageContaining("environment variable");
    }

    // The name becomes part of a path under the credential directory. A separator or a parent
    // reference in it would put the credential file somewhere else entirely.
    @Test
    void refusesANameThatWouldReachOutOfTheCredentialDirectory() {
        assertThatThrownBy(() -> new SecretId("../../etc/passwd", "ANTHROPIC_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretId("a/b", "ANTHROPIC_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretId("a\\b", "ANTHROPIC_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Windows resolves a device name whatever extension follows it, so 'con.key' would name the
    // console rather than a file. The refusal runs on every platform, or a caller would ship an id
    // that works where they built it and breaks for a Windows user.
    @Test
    void refusesANameMatchingAWindowsDevice() {
        assertThatThrownBy(() -> new SecretId("con", "CON_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved device name");
        assertThatThrownBy(() -> new SecretId("nul", "NUL_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretId("com1", "COM1_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecretId("lpt9", "LPT9_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Refusing a name that works is its own defect. A probe on Windows 10 created com0.key and
    // lpt0.key without complaint. A device name also claims a whole segment, so an id that merely
    // starts with one is an ordinary filename.
    @Test
    void acceptsANameThatOnlyResemblesAWindowsDevice() {
        assertThatCode(() -> new SecretId("com0", "COM0_API_KEY")).doesNotThrowAnyException();
        assertThatCode(() -> new SecretId("lpt0", "LPT0_API_KEY")).doesNotThrowAnyException();
        assertThatCode(() -> new SecretId("console", "CONSOLE_API_KEY")).doesNotThrowAnyException();
        assertThatCode(() -> new SecretId("nullify", "NULLIFY_API_KEY")).doesNotThrowAnyException();
    }

    // The caller names its own credential, so the shape it may choose is pinned here rather than
    // left to whatever that caller happens to pick.
    @Test
    void acceptsLowerCaseLettersDigitsAndHyphensAndRefusesTheRest() {
        assertThatCode(() -> new SecretId("some-name-2", "SOME_NAME_API_KEY"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new SecretId("SomeName", "SOME_NAME_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lower-case");
        assertThatThrownBy(() -> new SecretId("some name", "SOME_NAME_API_KEY"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
