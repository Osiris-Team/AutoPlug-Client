# Native launch protocols

This package is original MIT code written against the official metadata and public service protocols. No launcher project code or binaries are linked into AutoPlug. Official loader installer JARs are downloaded into the user cache only when the user prepares that loader. They are not bundled in AutoPlug.

## Interface

Use NativeLauncher.prepare(gameVersion, loader, gameDirectory, LaunchAccount) to install a version and receive a LaunchPlan. An overload accepts a pinned loaderVersion. Configure a JavaResolver through the constructor when application settings contain a Java path override: installer processors need the same resolver as the final game process. LaunchPlan.buildCommand returns a ProcessBuilder argument vector; it can contain an in-memory access token and must never be logged or persisted. LaunchPlan.launch inherits process output and uses the isolated profile directory.

MicrosoftOAuth requires the application's own registered Microsoft public-client ID. Device authorization begins only after an explicit account-owner sign-in action. The device code, access token and refresh token remain in memory. Minecraft authentication exchanges the Microsoft access token for Xbox Live and XSTS tokens, checks Java Edition entitlements and reads the Minecraft profile. A registered app may also need Minecraft Services approval; no shared third-party application ID is supplied. Final authenticated game joining is a maintainer validation step.

## Direct connection

Mojang removed the server and port launch arguments in snapshot 23w14a, shipped as Minecraft 1.20. The engine uses quickPlayMultiplayer for metadata that advertises that option and Minecraft 1.20 or newer; older clients receive server and port arguments. The original specification's intent (direct joining) is preserved using the applicable protocol. Java 21 begins in Mojang metadata for 1.20.5, which is separate from the direct-connection change.

Primary source: https://www.minecraft.net/en-us/article/minecraft-snapshot-23w14a (Technical Changes and Quick Play).
Java 21 release: https://www.minecraft.net/en-us/article/minecraft-java-edition-1-20-5 .

The public Mojang manifest read on 2026-10-03 reported release 26.3, whose metadata requires Java 25. Runtime selection follows metadata rather than limiting current releases to the original 8/17/21 examples. NeoForge's year-based versions include the Minecraft hotfix component: 26.3 selects 26.3.0.*, while 26.3.1 selects 26.3.1.*. Primary NeoForge announcement: https://neoforged.net/news/26.1release/ .

## Download and loader processing

Mojang SHA-1 values verify the client, assets, logging configuration and library artifacts. Loader library metadata hashes or repository SHA-1 sidecars verify Maven downloads. Adoptium archives use their published SHA-256 values, with isolated per-major runtime directories and atomic installation staging. Archive and metadata paths cannot leave the selected cache/profile roots. Native extraction respects Mojang OS/architecture/feature rules. Legacy natives classifiers are selected separately; modern natives remain on the classpath for LWJGL resource loading.

Fabric and Quilt JSON profiles merge with the inherited Mojang metadata. Forge and NeoForge install_profile.json supplies client-side processors, embedded Maven libraries, data expressions and required output checksums. Only client-side processors run. Successful generated files receive a local SHA-256 installation receipt so a later preparation can reuse them; corruption invalidates that receipt. Inherited client JAR aliases use the loader version name so NeoForge's module ignore list applies correctly. No processor is given any account credentials.

Installer ROOT denotes the shared cache installation root; processors run from a separate work directory. This makes NeoForge's declared embedded-library extraction directory agree with the libraryDirectory launch property. The official NeoForge 26.3 metadata supplies its loader startup class, version arguments and libraryDirectory property without declaring the patched Minecraft JAR as a classpath library. The engine keeps that official launch metadata intact; successful real loader startup remains part of maintainer validation.

Official endpoints:
- https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
- https://meta.fabricmc.net/
- https://meta.quiltmc.org/
- https://maven.minecraftforge.net/
- https://maven.neoforged.net/releases/
- https://api.adoptium.net/q/swagger-ui
- https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code
- https://learn.microsoft.com/en-us/gaming/gdk/docs/services/fundamentals/s2s-auth-calls/service-authentication/live-website-authentication

## Verification boundary

The tests include frozen official Mojang metadata (1.12.2, 1.19.4, 1.20.4, 1.20.5 and 26.3), Fabric/Quilt profiles and official Forge/NeoForge installer manifests for both 1.20.4 and 26.3. Their provenance is in src/test/resources/native-launcher/sources.json. Additional isolated fixtures exercise network failures, checksum failures, asset/native extraction, OS rules, processor subprocesses, Java version probes and an actual ProcessBuilder launch of an original tiny offline test JAR. These fixtures are labelled as fixtures: they are not evidence of a live Minecraft game, a Microsoft account login or server joining.

Public official metadata and checksum endpoints were also read during development. No paid account, owner credentials, client connection to a public game server or real game launch was used. Maintainers perform the final authenticated game join according to the issue's acceptance plan.
