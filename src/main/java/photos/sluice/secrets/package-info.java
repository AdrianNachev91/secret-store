/**
 * A tiered secret store. A credential is read from the first place that answers, saved to the
 * strongest place that can be written, and cleared from every place a save can reach.
 *
 * <p>{@link SecretStore#forApplication} builds one. The places are an environment variable, the
 * operating system's own credential store, and a permission-restricted file in a directory the
 * consumer names. The two that hold a credential in the clear are named on the builder or left
 * out. The operating system's own store is used wherever the platform offers one.
 *
 * <p>Reaching that store needs native access. A consumer on the classpath passes
 * {@code --enable-native-access=ALL-UNNAMED}, and one on the module path passes
 * {@code --enable-native-access=photos.sluice.secrets}.
 */
@NullMarked
package photos.sluice.secrets;

import org.jspecify.annotations.NullMarked;
