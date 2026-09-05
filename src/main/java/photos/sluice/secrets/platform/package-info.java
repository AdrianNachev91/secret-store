/**
 * The native bindings, one per platform, behind {@link PlatformBindings}.
 *
 * <p>Each carries a credential to and from its platform's credential store, and turns that
 * platform's own answer into present, absent or failed.
 *
 * <p>A binding's native calls only work on the platform whose library it binds, so the test cases
 * that run them are gated to that operating system. What needs no library, a status-code message
 * or a derived name, is tested on every runner.
 */
@NullMarked
package photos.sluice.secrets.platform;

import org.jspecify.annotations.NullMarked;
