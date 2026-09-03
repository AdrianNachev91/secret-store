# secret-store - project context

A tiered secret store for Java. A credential is read from the first place that answers. The
places, in order, are an environment variable, the operating system's own credential store, and
a file protected by permissions in the app-data directory. The OS stores are Windows Credential
Manager, the macOS keychain and the freedesktop Secret Service on Linux. Saves go to the
strongest writable place, removes clear every place. The OS stores are bound through `java.lang.foreign`, so the library has no runtime
dependency beyond the JDK. Round-trip tests run against the real stores on all three platforms in
CI, on every push that touches the code.

The code was extracted from the Sluice photo organizer's `adapter/secrets` package, copied at
Sluice commit `1eeca60` into this clean repository. Sluice keeps its own copy and does not consume
this artifact yet. That is the constraint everything here is built under: **adopting this library
must cost Sluice no refactor**. So every deviation from Sluice's code is priced in
`tmp/sluice-usage.md` before it is made.

## Read first
- `docs/design.md` - the library's design, and public. The model, configuration, native keys, the
  public surface, packaging, the Java floor, concurrency, refusals, releases. It says what the
  library is, never how it got there, so keep it that way when you edit it.
- `tmp/working-notes.md` - the plan, the alternatives each decision beat, and the measurements.
- `tmp/sluice-usage.md` - how Sluice composes and uses the store at the copied sha, and the
  ledger of every deviation with its adoption cost. Written from Sluice's code, not from memory.

  `tmp/` is gitignored apart from `.gitkeep`, so both files are local to one machine and neither
  survives a clone. That is deliberate: none of it is public and all of it dies at the first
  release.
- When Sluice is checked out beside this repo, its source is at
  `../Sluice/app/src/main/java/photos/sluice/adapter/secrets/` and the six port types at
  `../Sluice/app/src/main/java/photos/sluice/application/port/out/Secret*.java` plus
  `StaleSecretNotClearedException.java`. Read them at `1eeca60`
  (`git -C ../Sluice show 1eeca60:<path>`), since Sluice's working copy moves on.

## Coordinates and build
- `photos.sluice:secret-store`, package `photos.sluice.secrets`. Maven, single module.
- `maven.compiler.release` is the current LTS, 25. Raising it breaks every consumer below, so it
  moves only on a major version.
- Dependencies: JSpecify (annotations, `provided` scope) and nothing else at runtime. Logging goes
  through `System.Logger`. Tests use JUnit 5 and AssertJ only.
- Build and test: `mvn test`. Surefire passes `--enable-native-access=ALL-UNNAMED`, and a
  consumer has to do the same; the README says so.
- CI runs `mvn test` at the floor JDK on `ubuntu-latest`, `windows-latest` and `macos-latest`.
  It runs on a push to `main` and on a pull request, in both cases only when the change touches
  `src/**`, `pom.xml` or the workflow itself. A docs-only change runs nothing, so a green tick on
  the previous commit is what stands. The Ubuntu runner starts a headless Secret Service first;
  the recipe is the "Start a Secret Service (Ubuntu)" step of Sluice's `.github/workflows/ci.yml`.
- `example.yml` is a second workflow, on Ubuntu alone. It installs the library, builds the example
  against it and runs the jar, which is the only place the example is compiled at all. It needs no
  Secret Service: with none running, that place drops out and `where` still answers.

## Conventions carried over from Sluice
- Every class under `src/main/java` gets a `/** */` block. Test files use `//` only.
- A comment says only what a reader cannot get from the finished code once. No change narration,
  no pointer at a neighbour to complete a thought, no explanation of how callers use a method.
- The tier and the native binding under it stay separate classes. The tier decides what an answer
  means and is tested on every runner. The binding only carries bytes and runs on its own
  platform.
- Fail loud at input boundaries. A store that answers neither yes nor no throws; it never reads
  as "no credential".
- Real collaborators in tests: the round-trip tests write to the real credential store of the
  runner they are on.

## Working method
- The work is the chunks in `tmp/working-notes.md`, L1 through L3. One chunk per session.
- Every chunk: suite green locally first. Then a fresh review agent and a comment sweep over the
  diff, spawned together on Opus at high effort. Then commit on `main` and wait for the CI
  conclusion on all three runners before calling it done. No chunk branches; there is nothing a
  branch would gate.
- The review brief leads with regression: could this change break behaviour Sluice's copy has
  today? The reference is Sluice's tests, which travel with the code.
- **Every commit here is public.** No personal paths, no real credentials in fixtures, no names
  of people in code, comments or docs. A test secret is a synthetic string.
- Conventional commit messages, `feat`, `fix`, `docs`, `chore`, `ci`.
- The README and any other public prose go through the `public-prose-review` skill before they
  are pushed for the first time.
- The README's usage snippet stays in a ```java fence. IntelliJ injects Java into it and reports
  a dozen parse errors, because a fragment is not a compilation unit. Decided 2026-09-03 to keep
  them: GitHub is what the audience reads, and it renders the fence highlighted. Do not silence
  them by dropping the language tag or by wrapping the snippet in a method.

## Only the repository owner can do these
Needed for L3, the first publish. Not delegable.
1. A Central Portal account and the `photos.sluice` namespace request.
2. The DNS TXT record on `sluice.photos` with the portal's verification key.
3. A GPG key pair: public half on a keyserver, private half and passphrase as repository secrets
   beside the portal token.
4. Repository secrets for the release workflow.

@.claude/memory/MEMORY.md
