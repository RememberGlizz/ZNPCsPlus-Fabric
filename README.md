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

## Runtime dependencies

Install the built ZNPCsPlus Fabric JAR and a compatible Fabric API on the **server only**. PacketEvents 2.11.1 is embedded inside the ZNPCsPlus JAR, so a separate PacketEvents JAR is not required.

For Velocity-backed Fabric servers, the packaged build disables PacketEvents' stock LOGIN-time Fabric mixins and attaches PacketEvents during Minecraft CONFIGURATION/PLAY instead. This prevents PacketEvents from decoding Velocity forwarding/login payloads while retaining its PLAY-state packet wrappers for NPCs.

## Status

This branch contains the current Fabric port source and Gradle wrapper. It is still being runtime validated and should not yet be treated as a production-ready full-parity release until the server-side NPC features have been exercised on the target network.

See `UPSTREAM-NOTICE.md` and `LICENSE` for upstream attribution and GPL-3.0 licensing.
