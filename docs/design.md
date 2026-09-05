# Design

How the library is put together, and the rules it holds itself to.

## The model

A credential lives in one or more places. A store asks them in order and the first to answer wins.

1. An environment variable.
2. The operating system's own store: Windows Credential Manager, the macOS keychain, or the
   freedesktop Secret Service.
3. A permission-restricted file in a directory the consumer names.

A read stops at the first place that answers, so a place below that one is never asked. An
environment variable holding the credential means no keyring is touched, and no unlock prompt can
be raised.

A save writes to the strongest place that can be written here, then asks every place above it to
give up what it holds. That clearing is best effort. A place is above the target only by having
answered that it cannot be written to. A keyring that cannot be reached returns quietly from a
clear as well. So a stale credential in an unreachable keyring can still win a later read, and the
save reports success. Where a place above the target is reachable and refuses the clear, the save
throws `StaleSecretNotClearedException`. The new credential is stored, and a read can still find
the old one.

A remove clears every place that can be written to, and reports any that refused. Clearing only the
place that answered would expose an older credential underneath, which reads as a removal that
silently failed. A save and a remove never touch an environment variable.

Reading a credential is separate from reporting where it lives. `status` and `holdings` cannot
return a credential at all, so a surface that shows where a key is stored cannot leak the key.

## Configuring a store

```java
SecretStore store = SecretStore.forApplication("YourApp")
        .inNamespace("com.example.yourapp")
        .withEnvironmentOverride()
        .withCredentialFilesIn(configDirectory)
        .open();
```

The two places that hold a credential in the clear are opt-in. Naming a directory turns the file
on, naming none turns it off, and `withEnvironmentOverride()` does the same for the environment. A
consumer under a policy against plaintext at rest refuses one or both. The directory and the file
place are one setting, so a consumer cannot name a directory nothing writes to, nor turn the file
on without saying where.

The operating system's own store has no such switch. It is used wherever the platform offers one,
and it is the one place that needs no such refusal. The builder's `onOperatingSystem` names which
platform's store to look for rather than asking this machine. A platform no binding is written
for leaves the store with no keyring place, which is how the tests compose a store without one.

A store naming neither plaintext place, on a machine with no credential store, reads nothing and
refuses every save. That is a configuration rather than a mistake, so `open()` builds it, and
`whereASaveWouldStoreIt()` is how a caller finds out in advance. A missing namespace throws at
`open()` naming what is absent.

## Native keys

The application name and the namespace decide every native key, by a fixed derivation. `<app>` is
the application name and `<name>` is the credential's.

| Place                      | Key it is written under                                                         |
|----------------------------|---------------------------------------------------------------------------------|
| Windows Credential Manager | target `<app>:<name>`, with `<name>` also the entry's account name              |
| macOS keychain             | service `<app>`, account `<name>`, label `<app>:<name>`                         |
| Secret Service             | schema `<namespace>.Credential`, attribute `name=<name>`, label `<app>:<name>`  |
| Protected file             | `<name>` plus the tier's suffix, in the directory the consumer named            |

Passing either input differently reaches different entries, so a consumer that changes one stops
finding what it stored. The tier tests pin the derivation against literals, and a round trip pins
the wiring above it under a synthetic name.

## The public surface

`SecretStore`, `SecretId`, `SecretStatus`, `SecretHolding`, `SecretStoreException` and
`StaleSecretNotClearedException`. Six top-level types, and the factory is a static on the interface
rather than a seventh. Nine more are nested inside them. Four are the `SecretStatus` variants a
consumer switches on, and the other five are `SecretStore.Builder`, `SecretHolding.Holding`,
`SecretStatus.Location`, `SecretStatus.StoredLocation` and `SecretStoreException.Tier`.

Version 1 is closed. A place is not an extension point. The tier interfaces are package-private,
and `SecretStatus` is sealed through `Location` and `StoredLocation` to four variants:
`InEnvironment`, `InKeyring`, `InFile` and `Absent`. Opening it would promise compatibility for a
fourth kind of place, which nothing has asked for.

`SecretId` names a credential and the environment variable that overrides it. Both halves are
required whether or not a given store consults the environment, so an id describes a credential
rather than one store's configuration.

## Packaging

Everything a consumer is meant to use lives in `photos.sluice.secrets`. The native bindings sit
under `photos.sluice.secrets.platform`, which publishes four types: three binding interfaces and one
factory that opens them. Each binding implementation stays package-private, so no native signature
is a compatibility promise. The three interfaces are public, and reachable on the classpath by
anyone who chooses to call them.

`module-info.java` exports only `photos.sluice.secrets`. On the module path that seals the
bindings, and a consumer grants native access by module name. On the classpath a module descriptor
is ignored for access, so the split is a matter of layout there rather than enforcement.

Bindings reach the OS through `java.lang.foreign`, so there is no runtime dependency beyond the
JDK and no binding layer to keep in step with it. JSpecify is compile-time only. Logging goes
through `System.Logger`.

## Java version

The floor is the current LTS, and the major version carries it. `1.0.0` targets Java 25, and a
later major version targets whichever LTS is current then. Raising a floor breaks a consumer
sitting below it, so it is a major bump rather than a quiet change. `java.lang.foreign` is final
from 22, which is the hard floor underneath the policy.

## Concurrency

A store holds no mutable state once opened and is safe to share between threads. A builder is not.

Two concurrent saves of one credential are last-writer-wins. The file place writes a uniquely named
temporary file and moves it atomically, so a credential file never holds a blend of two writes. The
operating system's own stores are relied on for the same, on their own guarantees rather than on
anything added here. Nothing in this repository tests one under concurrent writes.

A save racing a remove is the case to avoid. The remove can report success and the credential still
be there afterwards. No library mechanism prevents that across processes, so a consumer with more
than one writer serialises them.

A save is not refused merely because something else removed the entry while it ran. Replacing a
credential in the macOS place takes two calls, an add and then an update. A removal landing between
them starts the save over. It starts over once. An entry put back and removed again during that
second pass is reported as a failure.

## Refusals

A credential is at most 1,024 characters once stripped, refused here rather than by whatever the
platform says when it runs out of room. The ceiling counts characters against a byte cap, so a
credential near it and largely outside ASCII can still be refused by Windows instead. A blank one
is refused before any place is reached, since storing it would replace a working credential with
something no service accepts. Both refusals are an `IllegalArgumentException`, thrown before any
place is asked, rather than a `SecretStoreException` reporting what a place did.

A credential name may hold only lower-case letters, digits and hyphens, and may not be a reserved
Windows device name. The name becomes a path segment and part of a native entry name, so that
leaves nothing to escape a directory with.

A place that answers other questions and refuses this one throws, rather than reading as "no
credential". That reading would send a user to re-enter a key already stored. `holdings` is the
exception: it reports a place that refused the question, so one broken place cannot hide every
healthy one's answer.

A place that answers nothing at all is a different fact, and it is treated as unavailable rather
than as broken. A read passes it by and a save routes below it, so a machine whose keyring went
away keeps working on the place beneath. The cost is that a credential still sitting in that
keyring is invisible until it can be reached again, and a remove is told it succeeded. The commoner
machine is the one that never had a keyring, and this is the trade that keeps it working.

## Releases

A release is published from CI on a `v*` tag, signed with a GPG key from the repository secrets,
through the Central publishing plugin. The workflow refuses a tag whose name disagrees with the
POM's version, runs the suite, then signs and uploads in one invocation. The upload validates and
then waits. Putting a version on Central stays a person's button in the Portal rather than a tag's
side effect. Central is immutable, so a version number is a promise. `1.0.0` says the surface is
closed rather than settling, which the sealed `SecretStatus` and the package-private tier
interfaces already enforce. Its evidence is one consumer's suite migrated onto the library and
run, and one credential read back across the two under the same native key, on Windows alone.

`example/` is a separate Maven project rather than a module of this build. Its POM names the
library version in one property, which the release sets to the version it just published. Building
the example then proves the artifact rather than the tree it came from. Until `1.0.0` is published
that property names the snapshot.

## Licence

MIT, no contributor licence agreement, since MIT contributions need no relicensing right.

## Not decided

Whether the API is ever generalised past the three built-in places. The composition already
generalises, being one interface and an ordering each place declares. `SecretStatus` does not. It
is sealed to four variants, so a new kind of place has no variant to report through. A consumer's
exhaustive switch would have to grow a case. A config-driven place reporting through one generic
variant is the candidate answer, deferred until someone needs it.

Shipping `1.0.0` prices that deferral. A fifth variant is a breaking change from here, so answering
this question costs a major version rather than a minor one. That is the trade the sealed type was
chosen for, taken deliberately.
