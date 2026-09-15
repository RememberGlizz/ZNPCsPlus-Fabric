# ZNPCsPlus Fabric 1.21.1

Fabric 1.21.1 server-side port work based on [ZNPCsPlus](https://github.com/Pyrbu/ZNPCsPlus), preserving the packet-backed NPC architecture rather than spawning ordinary server entities.

## Build

Requires **Java 21**. On Windows:

```bat
gradlew.bat clean build
```

On Linux/macOS:

```bash
./gradlew clean build
```

After a successful Loom build, the remapped mod JAR is under `build/libs/`.

## Runtime dependency

The server also needs the compatible **PacketEvents Fabric** mod installed.

## Status

This branch contains the current Fabric port source and Gradle wrapper. It is still being compile/runtime validated and should not yet be treated as a production-ready full-parity release until the GitHub Actions build and server testing pass.

See `UPSTREAM-NOTICE.md` and `LICENSE` for upstream attribution and GPL-3.0 licensing.
