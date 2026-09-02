# How Sluice builds and uses the store, and what adoption costs it

Written from Sluice's code at commit `1eeca60`, the sha this library's code was copied from.
This is what adoption is checked against. Sluice's working copy moves on from that sha, so a
later reader re-reads Sluice at adoption time and updates this file rather than trusting it.

## Composition

One Spring bean, in `config/AppConfig.java`:

```java
@Bean
public SecretStore secretStore() {
    final String osName = System.getProperty("os.name");
    return TieredSecretStore.forMachine(System::getenv, osName,
            ConfigDirLocator.secretsDir(osName, System.getenv()));
}
```

Three inputs: a lookup for environment variables, the raw `os.name`, and the directory the
protected file tier writes into (a subdirectory of the OS-native config directory, computed by
Sluice). `forMachine` builds the environment tier, asks `PlatformKeyring.forThisMachine(osName)`
for the one OS-store tier this platform offers, adds the file tier, and orders the writable tiers
by their own declared `precedence()`.

Nothing else in Sluice constructs a tier or names one. The library's factory keeps this shape and
adds the two naming inputs (`"Sluice"`, `"photos.sluice"`), so the adoption edit to this method
is one line.

## The native keys Sluice writes

| Store                      | Key the entry is found by                                      | Also written              |
|----------------------------|----------------------------------------------------------------|---------------------------|
| Windows Credential Manager | target `Sluice:<provider>`                                     | account name `<provider>` |
| macOS keychain             | service `Sluice`, account `<provider>`                         | label `Sluice:<provider>` |
| Linux Secret Service       | schema `photos.sluice.Credential`, attribute `name=<provider>` | label `Sluice:<provider>` |
| Protected file             | `<secrets directory>/<provider>` plus the tier's suffix        |                           |

Sluice will have users with entries under these keys by the time it adopts. A lookup under a
key that differs by one character misses, reports the credential absent, and leaves the old
entry as an orphan. So the library reproduces all four from Sluice's two naming inputs, pinned
by a round-trip test.

## The consumers

Nine production files outside `adapter/secrets` name the port types. None switches over
`SecretStatus` exhaustively.

| Consumer                          | What it does with the store                                                                                      |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------|
| `adapter/vision/AnthropicCuller`  | Declares its id: `new SecretId("anthropic", "ANTHROPIC_API_KEY")`. Reads `secret(id)` and throws Sluice's own `MissingCredentialException` when empty, naming `id.environmentVariable()` in the message. |
| `application/service/CullEngine`  | Before a run, `status(id) instanceof SecretStatus.Absent` throws `MissingCredentialException`, naming `id.provider()`. Declares `SecretStoreException` as a pass-through. |
| `application/service/Pipeline`    | Holds the `SecretStore` and hands it to `CullEngine`. No calls of its own.                                        |
| `adapter/ui/VisionProviderPresenter` | `save(id, key)`, catching `StaleSecretNotClearedException` before `SecretStoreException`. `remove(id)`. `status(id)`, catching `SecretStoreException` into a row that still offers Remove. `holdings(id)` twice: any `StoredLocation` that `HOLDS`, and a count of holders plus whether any `COULD_NOT_BE_ASKED`. `whereASaveWouldStoreIt()`, testing `instanceof InKeyring` to word a reassurance line. |
| `adapter/ui/SettingsPresenter`    | `SecretStore.maxSecret()` as an entry field's length limit.                                                      |
| `adapter/ui/RunRefusals`          | A `switch` arm per exception type: `MissingCredentialException`, then `SecretStoreException`, quoting `getMessage()`. |
| `adapter/cli/RefusalClassifier`   | Same two arms. On the missing case it reads `holdings(id)`, `id.provider()` and `id.environmentVariable()` into fields. On the store case it reads `broken.tier()` into a field. |
| `application/service/CullDispatcher` | Names the provider id; touches no secret type.                                                                 |
| `config/AppConfig`                | The composition above.                                                                                           |

`MissingCredentialException` is Sluice's own type, not the store's: absence is a store that
worked. It carries a `SecretId`, so at adoption it references the library's type.

The reference behaviour for regression is `TieredSecretStoreTest` and the five tier tests, which
travel with the code.

## Ledger: every deviation, priced as Sluice's adoption cost

A row is added before the deviation is coded. Counts are taken from Sluice's tree with a grep at
the time the row is written, never estimated. The columns say what the library does differently,
why, what Sluice does about it, and how many sites that touches.

| Deviation                                                     | Why the library has it                                          | What Sluice does at adoption                                              | Sites at `1eeca60`                          |
|---------------------------------------------------------------|-----------------------------------------------------------------|---------------------------------------------------------------------------|---------------------------------------------|
| Port types move from `application/port/out` to `photos.sluice.secrets` | They are the library's API                            | Delete six files, fix imports; `ArchitectureTest` allows a library import | 9 production files, 9 test files, plus `MissingCredentialException` |
| Factory takes an application name and a namespace             | The native keys must not say "Sluice" in a general library      | One line in `AppConfig.secretStore()` passes `"Sluice"`, `"photos.sluice"` | 1                                          |
| `SecretId.provider` becomes `SecretId.name`                   | "Provider" is Sluice's vocabulary                               | Rename the accessor at each call site (IDE rename)                        | 5 sites in 3 files                          |
| Messages say "credential 'x'" rather than "for provider 'x'"  | Same                                                            | Reword the one test that pins the text                                    | 1 test file                                 |
| `System.Logger` replaces SLF4J                                | No runtime dependency                                           | Nothing: Spring Boot bridges JUL into its log                             | 0                                           |
| `ReservedDeviceNames` copied in, package-private              | The library cannot reach Sluice's domain class                  | Nothing; Sluice keeps its own                                             | 0                                           |

Beside the ledger, adoption deletes `adapter/secrets` (17 classes, 11 test classes) and the six
port types, about 7,400 lines, and adds one POM dependency. Sluice's surefire already passes
`--enable-native-access=ALL-UNNAMED`. Whether it happens before Sluice goes public is decided
once this library is finished.
