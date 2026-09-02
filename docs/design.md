# Design decisions

Locked 2026-09-02, over four rounds, before any code was written here. Each item records the
alternatives it beat, so a later reader can tell a decision from a default. The reasoning that
led here, and the sizing, live in the Sluice repository's own plan; this file is the working copy
from L1 on.

## Why the library exists

Every existing Java keyring library is abandoned. `com.github.javakeyring:java-keyring` has had
no commit since September 2023, pins JNA 5.13 and was never tested past JDK 17. Microsoft's
`credential-secure-storage-for-java` has been archived read-only since 2025-03-07, and its own CI
never ran its round-trip tests. Both died the same way: JNA needed re-fixing as the JDK moved and
nobody kept doing it. Hand-rolled `java.lang.foreign` bindings have no such moving part, and the
native APIs underneath are frozen: `advapi32`'s `Cred*` since Windows XP, libsecret's password
API since 2012, Security.framework's `SecItem*` since 10.6.

The proof is real rather than claimed: round-trip tests against the actual credential store on
all three CI runners.

## The decisions

**1. Its own public repository.** Not a module of Sluice's build. The library is upstream of an
AGPL consumer, so it needs its own release cadence and licence. And GitHub Actions is free and
unmetered on a public repository, which is what lets the macOS leg run on every push.

**2. `photos.sluice:secret-store`, package `photos.sluice.secrets`.** A groupId names the
publisher, not the subject, and Central only grants a namespace its owner has proven. The
alternatives were an `io.github.<user>` namespace and a personal umbrella domain; the product
domain won because a groupId migration is common enough that the option stays open. The name is
wider than "keyring" because the library handles an environment variable and a user-owned file
as well as the OS store; `secrets` and `credential-store` were the other candidates.

**3. Always the latest GA Java.** `maven.compiler.release` is the current JDK and moves up each
March and September. A floor at 22, where `java.lang.foreign` went final and where the code
already compiles, was rejected as one more thing to maintain. A consumer pinned to an LTS cannot
use the library until it moves; that is the audience as chosen.

**4. `System.Logger` instead of SLF4J; JSpecify stays.** SLF4J in one class for two `info` lines
was the only thing between the library and "no runtime dependency beyond the JDK". JSpecify is
a compile-time annotation jar and part of what a public API promises. The README claim is exactly
that: no runtime dependencies beyond the JDK, one annotation-only compile dependency.

**5. Version 1 is closed.** A tier is one place a secret can live; the library ships three.
Opening it would let a consumer add a fourth (a vault, a password manager, a cloud key service)
by implementing a published interface. That is a compatibility promise nobody has asked for.
`SecretTier` and `WritableSecretTier` stay package-private and `SecretStatus` stays sealed. A
version 2 question if anyone asks. The public types are `SecretStore`, `SecretId`,
`SecretStatus`, `SecretHolding`, `SecretStoreException`, `StaleSecretNotClearedException` and
one factory holding what Sluice's `TieredSecretStore.forMachine` does.

**6. Native naming is configuration the consumer passes: two inputs, fixed derivation.** The
factory takes an application name and a namespace and derives every native key from them. The
Windows target is `<name>:<entry>`. The macOS service is `<name>` and its label
`<name>:<entry>`. The libsecret schema is `<namespace>.Credential` with its `name` attribute.
The file is `<entry>` plus the tier's suffix, in the directory the consumer passes. Sluice's
inputs are `"Sluice"` and `"photos.sluice"`, and those reproduce the keys Sluice writes today by
construction. A round-trip test pins the derivation against the literals. A builder exposing
each native string was rejected as four knobs nothing needs; one can still grow them later
without breaking two-input callers. Also here: `SecretId(provider, environmentVariable)` becomes
`SecretId(name, environmentVariable)`, and messages say "credential 'x'" rather than "for
provider 'x'". The environment tier stays mandatory and first, `MAX_SECRET` stays at 1,024, and
`ReservedDeviceNames` is copied in as a package-private class.

**7. The example is a CRUD and nothing more.** Under `example/`, its own Maven project rather
than a reactor module, depending on the published coordinates so that building it proves the
artifact on Central rather than the working tree. One `Main.java` of about eighty lines: `store`,
`read`, `where`, `forget` from the command line, printing which place answered. Built the way
Sluice uses the store (see `sluice-usage.md`): same factory call, same read, status, save and
remove. Any flexibility the example wants and Sluice does not is a ledger row before it is code.

**8. Publish from CI on a `v*` tag; `0.1.0` first, `1.0.0` when Sluice adopts it.** The tag
workflow builds, signs with a GPG key from the repository secrets, and deploys through
`org.sonatype.central:central-publishing-maven-plugin` with the portal token. Central is
immutable, so the first version nobody has run does not promise stability. A real consumer
running on it is the only proof that earns `1.0.0`; a fixed interval was rejected because it
proves only that nobody looked.

**9. Sluice does not consume the library yet.** It ships on the code it has now. The two copies
diverge from `1eeca60`, a fix found in either is ported to the other by hand, and adoption is a
later, unscheduled step. When it happens, the shape is known. Sluice deletes `adapter/secrets`
and its six port types. Its application layer imports this library's `SecretStore` the way it
imports `java.nio.file.Path`. Its `AppConfig.secretStore()` becomes one factory call.

**10. MIT, no CLA.** MIT contributions need no relicensing right, so a CLA has no work to do.
The README's posture line: maintained best-effort, revisited each JDK release. No donation or
sponsorship link, decided separately and not to be re-floated.

**11. Adoption costs Sluice no refactor, and every deviation is priced against that.** The
separation in Sluice's `adapter/secrets` was already good enough to cut the library from as it
stood, and that is the property to keep. Anything this library does differently is an
abstraction Sluice handles at adoption as an import, a renamed call or one changed factory line.
`sluice-usage.md` holds the account of Sluice's use and the ledger; counts in it are taken from
Sluice's tree when a row is written, never estimated.

## What is not decided

- Whether the API is ever generalised past the three built-in places. The tier composition
  already generalises (one interface, one registration line, an ordering each tier declares).
  What does not is `SecretStatus`, sealed to exactly `InEnvironment`, `InKeyring`, `InFile` and
  `Absent`. A new kind of place has no variant to report through, and a consumer's exhaustive
  switch would have to grow a case. A config-driven generic tier reporting through one generic
  status variant is the candidate answer, deferred until someone needs it.

## The chunks

| Chunk | What lands                                                                                              |
|-------|---------------------------------------------------------------------------------------------------------|
| L1    | The code and its tests copied in under the new package, the two-input naming, `name` for `provider`, `System.Logger`, the POM, and three-OS CI green on every push. |
| L2    | Visibility pass on the public surface, Javadoc for it, the README, `example/`, and the adoption ledger filled in from Sluice's tree. |
| L3    | The `v*` tag workflow with signing and the Central deploy; `0.1.0` published; the example built against it. |

L1 is a large diff and a move: about two hundred lines are real, the rest is relocation. Its
review load is those lines plus the moved tests passing on three runners. L3's wall-clock is the
owner's: the namespace verification, the TXT record, the GPG key and the portal token.
