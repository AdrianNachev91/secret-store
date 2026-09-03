/**
 * A tiered secret store, reading a credential from the first place that answers.
 *
 * <p>The native bindings under {@code photos.sluice.secrets.platform} are reachable from inside
 * this module and from nowhere else on the module path. On the classpath this descriptor is
 * ignored for access, and every public type in the jar is reachable.
 *
 * <p>A module-path consumer grants native access to this module by name, with
 * {@code --enable-native-access=photos.sluice.secrets}. On the classpath the library sits in the
 * unnamed module instead, and the grant is {@code --enable-native-access=ALL-UNNAMED}.
 */
module photos.sluice.secrets {

    requires static org.jspecify;

    exports photos.sluice.secrets;
}
