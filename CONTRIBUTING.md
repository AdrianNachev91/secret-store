# Contributing

`CLAUDE.md` in the repository root carries the conventions in full. This file names the few that
will send a pull request back, because none of them is guessable from the code.

## The native key derivation is frozen

`docs/design.md` gives the key each place writes a credential under. Changing the application name,
the namespace or the shape of a key orphans every credential a consumer has already stored, and it
does so silently. The old entry stays where it is, and the new key finds nothing. That reads as no
credential rather than as an error.

The tier tests pin those keys against string literals. They are written to fail if a key moves.
**Do not edit them to match a change.** A failure there means the change is wrong, not the test.

## Build and test

`mvn test`. Surefire passes `--enable-native-access=ALL-UNNAMED`, which the bindings need.

CI runs the suite at the floor JDK on Ubuntu, Windows and macOS. The round-trip tests write to the
real credential store of whichever runner they are on. So a change to a binding is proved only by
the leg for its own platform.

## Comments

Every class, interface, enum, record and nested type gets a `/** */` block, as does every method and
every enum constant. A method carrying `@Override` is the exception. Test files use `//` only.

A comment says only what a reader cannot get from the finished code once. No narration of what
changed, no pointer at a neighbouring class to complete a thought, and no explanation of how callers
use a method.

## What must not appear

No real credential, no personal path, and no person's name in code, comments or docs. A test secret
is a synthetic string. The name in `LICENSE` and in the POM's `developers` block is the exception,
being what a publisher signs.

## Transcripts are real runs

`example/README.md` carries pasted output from an actual machine and from the Example workflow.
Changing what the CLI prints means running those again and pasting the new output, not editing the
old to match.

## Licence

MIT, and there is no contributor licence agreement. MIT contributions need no relicensing right, so
opening a pull request is all that is asked.
