# Security policy

## Reporting a vulnerability

Use this repository's private vulnerability reporting, under the Security tab, rather than a public
issue. That keeps the report between you and the maintainer until there is something to publish.

One person maintains this. Expect an acknowledgement rather than a fix on any particular schedule,
and an advisory naming the first release that carries the fix.

## Which versions get a fix

The latest release. Maven Central is immutable, so a fix ships as a new version rather than as a
replacement for the one carrying the defect.

## What is in scope

The library's own behaviour. Where a credential is stored, which place answers a read, what a
remove clears, and what a refusal reports. Also whether a credential ever reaches a log or an
exception message.

## What is not

The limits each place inherits from the platform under it. `README.md` sets these out in full, and
they are design rather than defects:

- The credential file carries owner-only permissions and is not encrypted. Anything running as you
  reads it, including a backup agent or a sync client.
- An environment variable is plaintext to every process that can see the environment.
- Nothing here defends against code running as you, against root or an administrator, or against a
  memory dump. A credential is an ordinary `String` on the heap for as long as a caller holds it.

A report that one of those is a vulnerability will be closed with a pointer to this section. A
report that the library fails to deliver one of them, such as a credential file written without
owner-only permissions, is in scope and worth sending.
