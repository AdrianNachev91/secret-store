# secret-store

[![CI](https://github.com/AdrianNachev91/secret-store/actions/workflows/ci.yml/badge.svg)](https://github.com/AdrianNachev91/secret-store/actions/workflows/ci.yml)

A tiered secret store for Java. One call stores a credential in the strongest place a machine
offers, on Windows, macOS and Linux. This library is the only thing it puts on your classpath.

A credential is read from the first place that answers, saved to the strongest place that can be
written, and cleared from every place a save can reach. The places, in read order:

1. An environment variable.
2. The operating system's own store: Windows Credential Manager, the macOS keychain, or the
   freedesktop Secret Service on Linux.
3. A permission-restricted file in a directory you name.

No Java dependency beyond the JDK. The three OS stores are bound through `java.lang.foreign`
rather than JNA, and the only compile dependency is JSpecify, which is annotations and is not
present at runtime. On Linux the Secret Service place calls `libsecret`, which desktop
distributions ship and a minimal container image does not.

## Requirements

Java 25 or newer, plus a native-access flag. It does not run on 21, because `java.lang.foreign` is
only final from 22. A floor at 22 would reach no further in practice. Nobody deliberately sits on
22, 23 or 24, and the conservative population is on 21, which is out of reach either way. So the
floor is the current LTS. It moves only on a new major version, since raising it breaks every
consumer sitting below. A later major version would target whichever LTS is current then. CI runs
the tests at the floor on Windows, macOS and Linux.

Reaching the operating system's store needs a native-access grant, which a consumer passes:

```
--enable-native-access=ALL-UNNAMED           # on the classpath
--enable-native-access=photos.sluice.secrets # on the module path
```

## Using it

```xml
<dependency>
  <groupId>photos.sluice</groupId>
  <artifactId>secret-store</artifactId>
  <version>1.0.0</version>
</dependency>
```

Until `1.0.0` is published, build from source instead: clone this repository, `mvn install`, then
depend on `1.0.0-SNAPSHOT`.

The groupId is the domain the publisher verified with Central, not a statement about the subject.
The code was cut out of a photo organizer, and nothing photo-specific survived the cut.

```java
SecretStore store = SecretStore.forApplication("YourApp")
        .inNamespace("com.example.yourapp")
        .withEnvironmentOverride()
        .withCredentialFilesIn(configDirectory)
        .open();

// the credential's name, and the variable that overrides it
SecretId key = new SecretId("anthropic", "ANTHROPIC_API_KEY");

store.save(key, "sk-...");
store.secret(key);                  // Optional<String>, from whichever place answered
store.status(key);                  // which place is in force, carrying no credential
store.holdings(key);                // every place, and what each holds
store.whereASaveWouldStoreIt();     // where the next save would land
store.remove(key);                  // clears every place a save can write to
```

The application name and the namespace decide the native keys. The name is what the Windows and
macOS entries are filed under, and the namespace names the Linux Secret Service schema. Change
either afterwards and the store stops finding what it saved.

A place is on when you name it. `withEnvironmentOverride()` adds the environment variable, and
`withCredentialFilesIn(dir)` adds the file, taking a `Path`. Both hold a credential in the clear,
so a consumer under a policy against that leaves one or both out. The environment is read first
because someone setting a variable is making a deliberate override. The operating system's own
store has no such switch, and is used wherever the platform offers one.

Naming neither, on a machine with no credential store, is a store that reads nothing and refuses
every save. That is a configuration rather than a mistake, so it opens.
`whereASaveWouldStoreIt()` says where a save would land before a user types anything, and
`status(key)` says where the credential is afterwards.

The operating system's store falls away in two different ways. Where the platform's library will
not load at all, the place is absent from the listing entirely, and one line is logged at INFO. A
container image without `libsecret` lands here. Where the library loads and the store behind it
cannot be reached, the place stays in the listing and answers nothing, with nothing logged. A Linux
session with no D-Bus lands there. Either way a save falls through to the credential file. The
install works, and the credential ends up unencrypted rather than in a keyring.

Reading a credential is separated from reporting where it lives. `status` and `holdings` cannot
return a credential at all, so a screen that shows where a key is stored cannot leak the key it
was never handed.

A store holds no mutable state once opened and is safe to share between threads. The builder is
not.

## What it protects against

Each place is only as strong as the platform underneath it, and the stored places are not equally
strong.

The operating system's stores protect against another account on the same machine. They also
protect against anything that copies a file without running as you: a stray backup, a synced
folder, a config file someone pastes into an issue. They hand a credential back to a process
running as you, and may require an unlock first.

The credential file protects against another account and nothing more. It carries owner-only
permissions and is not encrypted, so a backup agent or a sync client running as you reads it.
Do not name a directory that something else syncs. A filesystem that cannot apply owner-only
permissions fails the write rather than storing the credential anyway.

Nothing here defends against code running as you, against root or an administrator, or against a
memory dump. The credential is an ordinary `String` on the heap for as long as you hold it.

It is built for a desktop or command-line application that runs as one person on their own
machine, not for a service.

## Limits

- A credential is at most 1,024 characters, measured after surrounding whitespace is removed, and
  anything longer is refused with this library's own message. The ceiling sits under the tightest
  store reached here, the Windows credential blob at 2,560 bytes. It counts characters against a
  byte cap, so a credential near the ceiling and largely outside ASCII can still be refused by
  Windows instead.
- A credential's name may hold only lower-case letters, digits and hyphens, and may not be a
  reserved Windows device name. It becomes a path segment and part of a native entry name. The same
  rule applies on every platform, so a name that stores on one machine stores on all three.
- Nothing enumerates what is stored. You read and write credentials you already name, so a screen
  listing them keeps its own list of names.
- A remove clears every place that can be written to, and reports any that refused. A save and a
  remove never touch an environment variable, so a credential named by one still answers
  afterwards.
- A credential this library refuses before any place is reached, blank or past the ceiling, is an
  `IllegalArgumentException`. Every failure a place produces is a `SecretStoreException`, naming
  the place it happened in. `StaleSecretNotClearedException` is the one worth catching separately.
  The credential was stored, and an older copy above it could not be cleared, so a read can still
  find the old one.
- Reading a credential can raise an unlock prompt where the keyring is locked. Asking where one
  lives is written not to. On Linux the search flag word is empty, so the call cannot take the
  unlock branch at all. On macOS only the unencrypted attributes are read, which rests on Apple's
  documentation rather than on a flag. Neither has been tested against a locked store, because a
  CI runner's keyring is unlocked.
- Two concurrent saves of one credential are last-writer-wins. The credential file cannot end up
  holding a blend of two, each write going to a uniquely named temporary file that is then moved
  into place atomically. The operating system's own stores are relied on for the same, on their
  guarantees rather than on anything added here.
- A save racing a remove is the one to avoid: the remove can report success and the credential
  still be there. Nothing in a library can prevent that across processes, so serialise writes to a
  credential if more than one thing writes it.

## Why it exists

The part that is easy to claim and hard to show is that these bindings actually work. Round-trip
tests write to, read back from and clear the real credential store of the machine they run on. CI
runs them on Windows, macOS and Linux on every push to `main` and every pull request that touches
the code. The runners are fresh machines whose keyrings are unlocked, so the unlock-prompt path is
not covered there. The whole native surface is eight files under `photos.sluice.secrets.platform`,
three of them the implementations. Each of those carries a credential to and from its platform's
store, and turns that platform's own answer into present, absent or failed. Which place wins a
read, where a save lands and what a remove reaches are ordinary Java above that, tested on all
three runners.

The two Java keyring libraries most people find are no longer moving. Checked September 2026,
`com.github.javakeyring:java-keyring` had no commit since September 2023 and pinned JNA 5.13.0, and
Microsoft's `credential-secure-storage-for-java` was archived read-only. Both bind the OS stores
through JNA, which needs re-fixing as the JDK moves, and in both cases that is the maintenance that
stopped.

These bindings are `java.lang.foreign` instead, so there is no third-party binding layer to keep in
step with the JDK, because the binding mechanism is part of it. What still moves is the
native-access grant, which is why this is revisited each JDK release. The APIs underneath have been
stable a long time. `advapi32`'s `Cred*` functions give Windows XP as their minimum supported
client. `libsecret`'s `secret_password_*` calls arrived with GNOME 3.6 in 2012.
Security.framework's `SecItem*` were already in the macOS 10.6 SDK.

Writing it yourself is a real option for one platform. For three it is eight native files, three
key derivations and three CI runners, which is what this is.

## Example

[`example/`](example/README.md) is a command-line CRUD over one credential. It is its own Maven
project rather than a module of this build, so at release it resolves the published artifact.
Building it then proves what was published, not the tree it was published from.

## Design

[`docs/design.md`](docs/design.md) covers the model, how a store is configured, the native keys it
derives, the public surface, packaging, concurrency and what it refuses.

## Reporting a security problem

Use this repository's private vulnerability reporting rather than a public issue.

## Status and maintenance

Central is immutable, so a version number is a promise. `1.0.0` says the surface is closed rather
than settling. The tier interfaces are package-private and `SecretStatus` is sealed, so a fourth
kind of place cannot arrive without a major version.

The evidence behind that is one consumer, the application this was cut out of. Its whole test suite
was migrated onto this library and run. A credential its own code had stored was then read back
through this one, under the same native key. That was on Windows. macOS and Linux key compatibility
is unproven, and a key that differs by one character reports a stored credential as absent.

Maintained best-effort, revisited each JDK release.

## Licence

MIT. No contributor licence agreement, since MIT contributions need no relicensing right.
