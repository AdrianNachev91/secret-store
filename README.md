# secret-store

[![CI](https://github.com/AdrianNachev91/secret-store/actions/workflows/ci.yml/badge.svg)](https://github.com/AdrianNachev91/secret-store/actions/workflows/ci.yml)

A tiered secret store for Java. One call replaces a per-OS credential path on Windows, macOS and
Linux, and the JNA dependency underneath it.

A credential is read from the first place that answers, saved to the strongest place that can be
written, and cleared from every place on a remove. The places, in read order:

1. An environment variable.
2. The operating system's own store: Windows Credential Manager, the macOS keychain, or the
   freedesktop Secret Service on Linux.
3. A permission-restricted file in a directory you name.

No Java dependency beyond the JDK. The three OS stores are bound through `java.lang.foreign`
rather than JNA, and the only compile dependency is JSpecify, which is annotations and is not
present at runtime. On Linux the Secret Service place calls `libsecret`, which desktop
distributions ship and a minimal container image does not.

## Requirements

Java 25 or newer, plus a native-access flag. Does not work on 21. The floor is the current
LTS. It changes only on a new major version: `1.0.0` targets 25, and `2.0.0` would target the next
LTS, 29. Raising the floor breaks every consumer sitting below it, which is why it never moves
inside a major version. CI runs the tests at the floor on Windows, macOS and Linux.

Reaching the operating system's store needs a native-access grant, which a consumer passes:

```
--enable-native-access=ALL-UNNAMED           # on the classpath
--enable-native-access=photos.sluice.secrets # on the module path
```

## Using it

Not on Central yet. Until `0.1.0` is published, clone this repository and `mvn install`, then
depend on `0.1.0-SNAPSHOT`.

```xml
<dependency>
  <groupId>photos.sluice</groupId>
  <artifactId>secret-store</artifactId>
  <version>0.1.0</version>
</dependency>
```

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
store.remove(key);                  // clears every place that can hold one
```

A place is on when you name it. `withEnvironmentOverride()` adds the environment variable, and
`withCredentialFilesIn(dir)` adds the file. Both hold a credential in the clear, so a consumer
under a policy against that leaves one or both out. The environment is read first because someone
setting a variable is making a deliberate override. The operating system's own store is used
wherever the platform offers one, and it has no switch.

Where the platform offers no store, or its own store cannot be reached, that place drops out. One
line is logged at INFO, and a save falls through to the credential file. A Linux session with no
D-Bus and a container image without libsecret both land here. The install still works, and the
credential ends up unencrypted rather than in a keyring.
`whereASaveWouldStoreIt()` answers that before anything is typed, and `status(key)` answers it
afterwards.

Reading a credential is separated from reporting where it lives. `status` and `holdings` cannot
return a credential at all, so a screen that shows where a key is stored cannot leak the key it
was never handed.

## What it protects against

Another account on the same machine, and anything that account cannot already read: a stray
backup, a synced folder, a config file someone pastes into an issue.

Each place is only as strong as the platform underneath it. The credential file carries owner-only
permissions and is not encrypted, and a filesystem that cannot apply those permissions fails the
write rather than storing the credential anyway. The operating system's stores hand a credential
back to a process running as you, and may require an unlock first.

Nothing here defends against code running as you, against root or an administrator, or against a
memory dump. The credential is an ordinary `String` on the heap for as long as you hold it.

It is built for a desktop or command-line application that runs as one person on their own
machine, not for a service.

## Limits

- A credential is at most 1,024 characters once trimmed. Longer is refused here, with this
  library's own message rather than the platform's.
- Nothing enumerates what is stored. You read and write credentials you already name.
- Reading a credential can raise an unlock prompt where the keyring is locked. Asking where one
  lives never does.
- A store is safe to share between threads, holding no mutable state once opened. The builder is
  not.
- Two concurrent saves of one credential are last-writer-wins. Neither corrupts what is stored,
  on any place. A save racing a remove is the one to avoid: the remove can report success and the
  credential still be there. Nothing in a library can prevent that across processes, so serialise
  writes to a credential if more than one thing writes it.

## Why it exists

The two Java keyring libraries most people find are no longer moving.
`com.github.javakeyring:java-keyring` has had no commit since September 2023 and pins JNA 5.13.0.
Microsoft's `credential-secure-storage-for-java` was archived read-only in March 2025. Both bind
the OS stores through JNA, which needs re-fixing as the JDK moves, and in both cases that is the
maintenance that stopped.

These bindings are `java.lang.foreign` instead, so there is no binding layer to keep in step with
the JDK. The APIs underneath have been stable a long time. `advapi32`'s `Cred*` functions give
Windows XP as their minimum supported client. GNOME 3.6 shipped libsecret's password API in 2012.
Security.framework's `SecItem*` were already in the macOS 10.6 SDK.

The cost of that is the requirement above. `java.lang.foreign` is only final from JDK 22, so 21 is
out of reach whatever floor this library picks.

The part that is easy to claim and hard to show is that these bindings actually work. Round-trip
tests write to, read back from and clear the real credential store of the machine they run on. CI
runs them on Windows, macOS and Linux on every push that touches the code. The whole native
surface is eight files under `photos.sluice.secrets.platform`, and it only carries bytes. Every
decision about what an answer means lives in ordinary Java above it, tested on all three runners.

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

Central is immutable, so a first version nobody has run does not promise stability. `1.0.0`
follows a real consumer running on it.

Maintained best-effort, revisited each JDK release.

## Licence

MIT. No contributor licence agreement, since MIT contributions need no relicensing right.
