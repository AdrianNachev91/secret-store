package photos.sluice.secrets.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Apart from LibsecretServiceTest because this needs no Secret Service and no Linux, so it runs on
// every runner. What it pins is the one string a consumer's stored entries are found by.
class LibsecretSchemaNameTest {

    // Pinned against the literal rather than against the suffix constant. Every entry already in a
    // consumer's keyring carries this exact schema, and the service matches on it. A changed suffix
    // makes all of them invisible to every lookup, search and removal made here.
    @Test
    void namesTheSchemaAsTheNamespaceThenCredential() {
        assertThat(LibsecretService.schemaNameFor("photos.sluice"))
                .isEqualTo("photos.sluice.Credential");
    }
}
