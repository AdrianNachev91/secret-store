# secret-store example

A command-line CRUD over one credential. It stores, reads, locates and clears
`SecretId("anthropic", "ANTHROPIC_API_KEY")`, and prints which place answered without ever
printing the credential.

## Build and run it

This is its own Maven project rather than a module of the library's build. The library version it
resolves is the `secret-store.version` property in its POM. The release sets that to the version
it just published, so that building this proves the artifact as published rather than the tree it
came from.

Until `0.1.0` is on Central, that property names the snapshot, so build the library first:

```
git clone https://github.com/AdrianNachev91/secret-store.git
cd secret-store
mvn install
cd example
mvn package
```

Once `0.1.0` is published, only the last two lines are needed. Then:

```
java --enable-native-access=ALL-UNNAMED -jar target/secret-store-example.jar where
```

`mvn package` writes `target/secret-store-example.jar` and copies the library beside it into
`target/lib`, which the jar's manifest points at. `java -jar` ignores any classpath given on the
command line, which is why the manifest carries it instead.

The flag is not optional. The library reaches the operating system's credential store through
`java.lang.foreign`, and every consumer grants that access. On the classpath, as here, the grant
names the unnamed module.

## The four commands

Run on a Windows machine with no `ANTHROPIC_API_KEY` set:

```
$ java --enable-native-access=ALL-UNNAMED -jar target/secret-store-example.jar where
  the environment variable ANTHROPIC_API_KEY: EMPTY
  this machine's credential store: EMPTY
  the protected file: EMPTY
A save would go to this machine's credential store

$ java ... -jar target/secret-store-example.jar store sk-ant-api03-synthetic-demo
Stored. In force: this machine's credential store

$ java ... -jar target/secret-store-example.jar read
Found 27 characters, from this machine's credential store

$ java ... -jar target/secret-store-example.jar where
  the environment variable ANTHROPIC_API_KEY: EMPTY
  this machine's credential store: HOLDS
  the protected file: EMPTY
A save would go to this machine's credential store

$ java ... -jar target/secret-store-example.jar forget
Cleared. In force: nowhere

$ java ... -jar target/secret-store-example.jar read
No credential stored.
```

Only the keyring line changes after a store, because that is where the save landed on this
machine. A machine with no credential store would show the file line holding it instead.

`read` prints the credential's length rather than the credential. Nothing here ever prints one.

An environment variable wins every read, so `ANTHROPIC_API_KEY` set in the shell makes `store` and
`forget` look as though they did nothing. `where` is what shows the difference: the variable and
the stored copy appear as separate places.

Credential files go under `.secret-store-example` in the home directory. The entries this writes
into the machine's own credential store are named for `SecretStoreExample`. Nothing reserves that
name, so a real consumer could have picked it too. Run `where` first if that matters on your
machine, since `store` overwrites whatever the name already holds.
