// One binding per platform credential store, each a pair. An interface names the operations in
// this library's own terms. An implementation carries bytes to and from the operating system
// through java.lang.foreign.
//
// A binding decides nothing about what to store or what an answer means. Those live in the tier
// above. That split is what lets the tier be tested on every runner, while a binding only runs on
// the platform whose library it binds.
@NullMarked
package photos.sluice.secrets.platform;

import org.jspecify.annotations.NullMarked;
