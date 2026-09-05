package photos.sluice.secrets.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Apart from LibsecretServiceTest because none of this needs a Secret Service or a Linux machine,
// so it runs on every runner. A name libsecret cannot turn into an object path has to be refused
// rather than sent. Sending one hangs the calling thread instead of failing it.
class LibsecretCollectionNameTest {

    // A hyphen is the shape a consumer reaches for first. The rest are the other ways an element
    // comes out empty or illegal once libsecret builds the path. Each one hangs the calling thread
    // if it is sent.
    @ParameterizedTest
    @ValueSource(strings = {"has-a-hyphen", "has a space", "has.a.dot", "/has/a-hyphen",
            "trailing/", "", "//doubled"})
    void refusesANameThatWouldNotBuildAPath(final String collection) {
        assertThat(LibsecretService.namesACollection(collection)).isFalse();
    }

    // libsecret chooses its branch on whether a name holds a slash anywhere, so this one is passed
    // through rather than built into an alias path. It is not a valid object path, so sending it
    // hangs. The pattern's leading slash is what refuses it.
    @Test
    void refusesARelativePathLibsecretWouldPassThroughUntouched() {
        assertThat(LibsecretService.namesACollection("foo/bar")).isFalse();
    }

    // The root is a legal object path and libsecret answers for it, so this one is refused for a
    // different reason from the rest: no collection lives there.
    @Test
    void refusesTheRootPathThoughItIsLegal() {
        assertThat(LibsecretService.namesACollection("/")).isFalse();
    }

    // Both legal shapes. An alias becomes one element under the aliases prefix, and an object path
    // is sent as it stands.
    @ParameterizedTest
    @ValueSource(strings = {"default", "login", "testbed_fixture", "Session2",
            "/org/freedesktop/secrets/collection/testbed_fixture"})
    void acceptsAnAliasAndAnObjectPath(final String collection) {
        assertThat(LibsecretService.namesACollection(collection)).isTrue();
    }

    // Read through the constant rather than its literal, because the pairing is what matters. The
    // single-argument open names this collection on every Linux machine. A change to either the
    // constant or the pattern that parted them would take every keyring with it.
    @Test
    void theDefaultCollectionIsItselfALegalName() {
        assertThat(LibsecretService.namesACollection(LibsecretService.DEFAULT_COLLECTION)).isTrue();
    }

    // The refusal runs before any library is loaded, which is what lets this case run on a machine
    // with no libsecret at all.
    @Test
    void openRefusesBeforeItReachesTheLibrary() {
        assertThatThrownBy(() -> LibsecretService.open("test.secretstore", "has-a-hyphen"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has-a-hyphen");
    }
}
