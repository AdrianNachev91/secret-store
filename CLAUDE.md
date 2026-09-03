# secret-store

A tiered secret store for Java. A credential is read from the first place that answers, saved to the
strongest place that can be written, and cleared from every place on a remove. The places, in read
order, are an environment variable, the operating system's own credential store, and a
permission-restricted file in a directory the consumer names. The OS stores are Windows Credential
Manager, the macOS keychain and the freedesktop Secret Service. They are bound through
`java.lang.foreign`, so the library has no runtime dependency beyond the JDK.

## Read first
- `README.md` - what the library is for, how a consumer configures it, what it protects against and
  what it does not.
- `docs/design.md` - the model, configuration, native keys, the public surface, packaging, the Java
  floor, concurrency, refusals and releases. It says what the library is, never how it got there, so
  keep it that way when you edit it.
- `tmp/` is the scratch folder for local working notes. The folder is tracked so it exists on a
  clone, and everything inside it is gitignored, so it arrives empty.

## Coordinates and build
- `photos.sluice:secret-store`, package `photos.sluice.secrets`. Maven, single module.
- Build and test: `mvn test`. Surefire passes `--enable-native-access=ALL-UNNAMED`. A consumer has
  to grant native access too, in whichever of the two forms the README gives.
- `maven.compiler.release` is the current LTS, 25. Raising it breaks every consumer below, so it
  moves only on a major version.
- Dependencies: JSpecify in `provided` scope and nothing else at runtime. Logging goes through
  `System.Logger`. Tests use JUnit 6 and AssertJ only.
- CI runs `mvn test` at the floor JDK on `ubuntu-latest`, `windows-latest` and `macos-latest`. It
  runs on a push to `main` and on a pull request, in both cases only when the change touches
  `src/**`, `pom.xml` or the workflow itself. A docs-only change outside `example/` runs nothing, so
  a green tick on the previous commit is what stands. The Ubuntu runner starts a headless Secret
  Service first.
- `example.yml` is a second workflow, on Ubuntu alone. It installs the library, builds the example
  against it and runs the jar. That is the only place the example is compiled at all, and it fires
  on a change under `example/` as well. It needs no Secret Service: with none running, that place
  drops out and `where` still answers.

## Conventions
- Every class under `src/main/java` gets a `/** */` block. Test files use `//` only.
- A comment says only what a reader cannot get from the finished code once. No change narration, no
  pointer at a neighbour to complete a thought, no explanation of how callers use a method.
- The tier and the native binding under it stay separate classes. The tier decides what an answer
  means and is tested on every runner. The binding only carries bytes and runs on its own platform.
- Fail loud at input boundaries. A store that answers neither yes nor no throws; it never reads as
  "no credential".
- Real collaborators in tests: the round-trip tests write to the real credential store of the runner
  they are on.
- A test secret is a synthetic string. No real credential, no personal path and no person's name in
  code, comments or docs, beyond the repository's own URL.
- Conventional commit messages: `feat`, `fix`, `docs`, `chore`, `ci`.
- The README's usage snippet stays in a ```java fence. IntelliJ injects Java into it and reports a
  dozen parse errors, because a fragment is not a compilation unit. The errors are expected and
  stay: GitHub is what the audience reads, and it renders the fence highlighted. Do not silence them
  by dropping the language tag or by wrapping the snippet in a method.

@.claude/memory/MEMORY.md
