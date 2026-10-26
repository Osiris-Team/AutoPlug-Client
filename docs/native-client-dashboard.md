# Native client dashboard and unified profiles

This implementation starts from AutoPlug 10.2.0 master and keeps AutoPlug's MIT license. No HMCL or other launcher source is imported. Minecraft and loader artifacts are downloaded from their published distribution endpoints into the end user's cache; they are not bundled with AutoPlug or this contribution.

## Dashboard

Click the tray icon to open the resizable FlatLaf dashboard. The application also opens its window when a desktop has no system tray. The five views are Server Browser, Singleplayer, Profiles, Server Manager and Settings. Server Manager retains the existing update/configuration panels, adds console output, and dispatches start, restart, stop, backup, task and server-console commands through the existing server services. Closing the window hides it; disabling/recreating the dashboard releases its console listener, cancels that window's pending account sign-in and releases the world view's refresh timer.

Server Browser uses the existing `MineStat` JSON status implementation, with a bounded response and read timeout. It displays MOTD, version/protocol, online/max players and status-query latency. Add or edit a server's **local loader choice** explicitly: vanilla status does not expose a server's required mods or loader. Proxy advertisements containing multiple versions require selecting a profile explicitly rather than guessing.

The dashboard automatically imports vanilla `servers.dat` when the default file exists, without pinging servers or modifying that file. Import adds addresses without overwriting an existing AutoPlug loader choice. Export is optional and user initiated. It replaces the chosen vanilla list, retains unknown root tags and retained servers' icons/settings, checks for changes during preparation and saves a uniquely named backup first. Close Minecraft before exporting to avoid concurrent edits by the game. Default paths cover Windows AppData, macOS Application Support and Linux `.minecraft`. NBT supports all standard tag types, raw and gzip streams, with byte, nesting and collection limits. Unicode strings use Java's NBT modified UTF encoding.

Profiles supports client MODS, server PLUGINS and server MODS_SERVER collections. Create, clone/migrate, delete, mark templates, select default update profiles, check/apply updates and launch client profiles. Each profile lives in `~/.autoplug/instances/<id>`; mutable configuration is copied, while JARs use the shared checksum cache. Server profiles cannot be launched as Minecraft clients. See [unified-profiles.md](unified-profiles.md) for update sources, explicit source identities, transaction/recovery behavior and provider limits.

Singleplayer creates virtual worlds from server profiles, with independent save/config directories, a thumbnail and play/share actions. After an explicit per-world Minecraft EULA choice, it prepares an official Paper/Purpur/Fabric server (or an explicitly supplied matching executable JAR), waits for its MineStat status ping, and launches a matching client at the local port. Closing the client stops its server gracefully. Server-mod worlds require an explicitly matching client profile rather than copying server-only mods into the client. See [the world protocol and lifecycle documentation](../src/main/java/com/osiris/autoplug/client/worlds/PROTOCOL.md) for supported server installation paths and cleanup behavior.

Play & share is an explicit request for a temporary TCP UPnP lease; ordinary local play binds only to loopback. Copy join address reads an existing mapping and never opens a port itself. Router discovery, SOAP control, finite-lease renewal and ownership checks are implemented without new dependencies. Existing mappings are not overwritten, and only an owned mapping can be deleted. A discovery, public-IP/CGNAT or mapping failure retains local play and gives forwarding/tunnel guidance. Lease renewal and explicit Stop cleanup failures remain visible. No real router was configured during contributor tests.

Play first checks the saved server. It selects a matching non-template client profile by exact Minecraft version and the saved local loader. Otherwise it requests confirmation to clone an appropriate client template, resolves releases for the target version using AutoPlug's update services, shows the complete plan, and applies it only after confirmation. Files with no compatible release become `.jar.disabled`. Network/provider errors stop the operation. A declined or failed new clone is removed; the source template remains intact.

Settings selects the default template, optional Java executables, offline/Microsoft account mode, a preferred world port (0 selects an available port) and whether explicit sharing may use UPnP. Invalid preferences do not partially change an account or persisted settings. When no Java path is configured, the launcher selects the major declared in Mojang metadata using AutoPlug's Adoptium API protocol and SHA-256 checked runtime downloads. Overrides are checked with `java -version` before use, including when an installer needs Java. Java 8, 17, 21 and the current Minecraft 26.3 Java 25 requirement are supported.

## Authentication

Offline mode is for local testing and offline-capable servers. It does not provide authentication to online-mode servers. Microsoft mode uses an end-user-initiated OAuth device flow, Xbox Live, XSTS, Minecraft entitlement and profile checks. Configure your own registered Microsoft public-client application ID; Minecraft Services may require application approval. The UI displays the verification URI and user code so the account owner can sign in in their browser. No password is collected. Account access and refresh tokens remain in memory; no token appears in preferences or command logs. Sign-out invalidates pending sign-in attempts. Restarting AutoPlug requires signing in again.

## CLI

The same commands work in the running AutoPlug console and as one-shot startup arguments. Client/profile-only startup commands return without starting the server, recurring tasks, SSH or the online console. Periods in versions and hostnames are preserved. Separate multiple commands by another dot-prefixed command token.

```text
java -jar AutoPlug-Client.jar ".profiles create survival 1.20.4 fabric"
java -jar AutoPlug-Client.jar ".profiles create hub 1.20.4 paper --type plugins"
java -jar AutoPlug-Client.jar ".profiles create servermods 1.20.4 fabric --type mods-server"
java -jar AutoPlug-Client.jar ".profiles list --type mods"
java -jar AutoPlug-Client.jar ".profiles activate survival"
java -jar AutoPlug-Client.jar ".check mods"
java -jar AutoPlug-Client.jar ".update mods --profile survival --confirm-disabling"
java -jar AutoPlug-Client.jar ".profiles clone survival 1.21.1 --confirm-disabling"
java -jar AutoPlug-Client.jar ".client launch survival --server play.example.org:25566"
java -jar AutoPlug-Client.jar ".client servers list"
java -jar AutoPlug-Client.jar ".mc worlds create Workshop hub"
java -jar AutoPlug-Client.jar ".mc worlds list"
# After reviewing the Minecraft EULA, explicitly accept for this world:
java -jar AutoPlug-Client.jar ".mc worlds accept-eula workshop"
java -jar AutoPlug-Client.jar ".mc worlds launch workshop"
# Sharing additionally requires online Microsoft authentication in the same process:
.mc worlds launch workshop --share
```

Default MODS, PLUGINS and MODS_SERVER update profiles are selected independently using `.profiles activate <id>` or the profile view's default-updates button. `.check/.update mods|plugins|mods-server [--profile <id>]` uses that type's active profile when the flag is omitted. Without an active profile, interactive `.check mods/plugins` retains the existing current-directory server behavior; one-shot checks, server-mod checks and `.update` request an explicit profile selection. They do not invent or mutate a current server profile. Deleting an active profile clears its default selection.

Additional commands: `.profiles template <id> true|false`, `.profiles delete <id>`, `.client servers import|export`, `.client auth offline <name>`, `.client auth microsoft <registered-client-id>` and `.client auth signout`. Preferences persist but authentication sessions do not. A one-shot Microsoft auth command ends its session on process exit; use the running console/UI to sign in and then launch in the same process, or supply both commands in one invocation.

## Launch protocols and verification

Mojang version manifests determine client, libraries, assets, native libraries, logging config and Java major. Fabric/Quilt use official loader profile APIs. Forge/NeoForge use their official installers, embedded Maven artifacts and declared client processors, with processor output checks and separate installer working directories. The native engine handles OS/rules, argument substitution and classpaths without using a shell. Direct join uses legacy `--server/--port` before Minecraft 1.20 and the official `--quickPlayMultiplayer` flow from 1.20 onward (introduced in 23w14a).

The contribution contains comprehensive unit/fixture tests plus explicit opt-in public-API probes. `OfflineClientLaunchTest` prepares and starts an original compiled fixture client through the actual `NativeLauncher` and `ProcessBuilder` paths. World lifecycle tests start original dedicated-server/client child JVMs and use the existing MineStat wire protocol. UPnP tests exercise SOAP against an original localhost HTTP fixture, including mapping ownership, renewal and cleanup. These prove the execution paths without claiming that Minecraft was launched or a real router was mapped. `DashboardLayoutTest` renders the actual four client Swing panels at two sizes offscreen; it excludes the server-manager view and is not native desktop/tray acceptance. Real Forge/NeoForge metadata is checked, but fixture processors execute original test code. The actual installers have not been run against the real Minecraft JAR. Final Microsoft-authenticated game joining, public-world connectivity and desktop acceptance require the maintainers' real game/router environment.

Run the deterministic contribution suites on a JDK with Maven:

```text
mvn test -Djava.awt.headless=true -Dtest=NativeLauncherTest,MicrosoftOAuthTest,LoaderInstallerTest,ManagedJavaRuntimeTest,OfflineClientLaunchTest,LauncherCancellationTest,UnifiedProfilesTest,ProfileProviderTest,ServerModProfilesTest,ServerBrowserTest,NativeClientServiceTest,DashboardLayoutTest,WorldStoreTest,WorldServerPreparerTest,VirtualWorldServiceTest,UpnpPortMapperTest,UtilsEnvironmentTest,UtilsStringTest,UtilsFileTest,UtilsMapTest
```

The inherited repository also has live-network/download tests (Google reachability, SteamCMD and server/provider downloads). The command above selects the deterministic contribution and related utility suites explicitly rather than claiming those external integrations were exercised offline. Public metadata probes are separate opt-in programs and never use an owner account.

Primary protocol references:

- [Official Mojang manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Minecraft 23w14a Quick Play changes](https://www.minecraft.net/en-us/article/minecraft-snapshot-23w14a)
- [Fabric Meta](https://meta.fabricmc.net/)
- [Quilt Meta](https://meta.quiltmc.org/)
- [NeoForge version format and Java 25](https://neoforged.net/news/26.1release/)
- [Microsoft device authorization](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code)
- [NBT author's format/reader reference](https://github.com/Querz/NBT)
- [UPnP WANIPConnection service](https://upnp.org/specs/gw/UPnP-gw-WANIPConnection-v1-Service.pdf)
- [UPnP device discovery architecture](https://openconnectivity.org/upnp-specs/UPnP-arch-DeviceArchitecture-v2.0-20200417.pdf)

Fixture metadata sources and published archive hashes are recorded in `src/test/resources/native-launcher/sources.json` and `src/test/resources/virtual-worlds/sources.json`. New implementation and verification were performed by AI coding agents, including direct source review and actual test execution; no separate human technical review or live account test is claimed.
