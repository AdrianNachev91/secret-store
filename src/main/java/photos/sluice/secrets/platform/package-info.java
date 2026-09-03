/**
 * The native bindings, one per platform, behind {@link PlatformBindings}.
 *
 * <p>Each carries a credential to and from its platform's credential store, and turns that
 * platform's own answer into present, absent or failed. What to store, what to call it and what a
 * refusal means to a caller are all decided a package up.
 *
 * <p>A binding's native calls only run on the platform whose library it binds, so the cases that
 * call out are gated to that operating system. What needs no library, a status-code message or a
 * derived name, runs on every runner.
 */
@NullMarked
package photos.sluice.secrets.platform;

import org.jspecify.annotations.NullMarked;
