# Native Minecraft dashboard

The dashboard implements the native launcher/profile workflow from issue #296 on master 10.2.0. It uses the existing FlatLaf Swing theme, MineStat status query and AutoPlug resource search/update providers. It does not include HMCL or third-party launcher implementations. Existing Server, console, tasks and backup controls remain available; closing the dashboard hides it rather than stopping AutoPlug.

## First use

Open the tray icon, then Client → Minecraft. The Servers, Profiles, Virtual worlds, and Account & settings views share one runtime and profile-use locks with console commands. Minecraft's standard `servers.dat` is imported automatically only when AutoPlug's own list is empty. Importing never rewrites the original file. Add/Edit/Remove changes only AutoPlug's saved list. A ping does not identify the mod loader: choose that explicitly for each server.

Create client MODS profiles, server PLUGINS profiles (Paper/Spigot/Purpur/etc.) or MODS_SERVER profiles. Each has separate `mods/` or `plugins/`, configuration and game directory. Select a template in settings for automatic version migration. Cloning a template creates a normal launchable profile. Check/update shows compatible replacements, confirmed incompatibilities and unresolved provider errors separately. Errors block applying; disabled files require explicit approval. Failed downloads leave the old assets intact. Immutable cached JARs are shared where supported; mutable configurations are independent copies. Stop a profile before changing it.

On Join, the existing ping utility supplies the version. Ambiguous/absent versions need an explicit override. An exact local version/loader match is used automatically; otherwise a template clone and migration are proposed. Assets without a compatible release are renamed `.jar.disabled` only after approval. A provider outage is an error, never proof that a mod is incompatible. Unknown provider mappings need configuration; the legacy Bukkit/Spigot latest-only search cannot prove a target version and is not installed blindly. Profile provider mappings use that profile's `config/mods.yml` or `config/plugins.yml`, not global server configuration.

## Java, accounts and loaders

Configure Java home directories, one per line. Each must contain `release` metadata and `bin/java` (or `java.exe`). The launcher chooses the exact Minecraft-required Java major version and compatible architecture; having only a newer Java is not enough. AutoPlug's already-installed managed JRE is also considered. A missing runtime produces a visible error rather than installing or guessing an incompatible Java. Legacy Intel-only native releases cannot run on an ARM JVM; use a compatible installation/environment instead.

Use a legitimately registered Microsoft application client ID with the necessary Minecraft/Xbox access. No borrowed application ID or embedded secret is supplied. Device authorization shows Microsoft's verification URL and user code. Access/refresh tokens stay in memory; never paste them into settings, logs or issue comments. Sign-out/exit clears the session. Offline fixture mode is explicit and does not provide ownership or access to authenticated servers. Microsoft authentication and ownership still have to succeed for online play.

The launcher resolves official Mojang metadata, hashed client/libraries/assets, OS rules and natives. Fabric and Quilt use their official metadata. Forge/NeoForge client installers are obtained from official repositories with their SHA1 sidecar, run with the selected Java and checked for a resulting profile; this runs vendor installer code when the user requests installation. Installing a client loader is not installing a dedicated server. Do not print `PreparedLaunch.getCommand()` because it contains authentication arguments. Cancellation and Stop release only AutoPlug-owned processes and temporary natives.

## Virtual worlds and sharing

1. Create a non-template server profile, then a world attached to it.
2. Configure that world's matching client profile and an official installed **executable server JAR**. Review and explicitly accept Minecraft's EULA in the world UI (or edit its own `eula.txt` manually). Creating a world leaves `eula=false`.
3. Play starts the dedicated server, waits for MineStat readiness, then connects the client. Further plays reuse saved configuration. Server mods/plugins are snapshotted per world; saves remain independent. Client exit requests graceful server stop, followed by bounded termination if necessary. Server logs are kept in that world's `server.log`.
4. Play & share explicitly binds the server to the LAN and requests a temporary UPnP mapping. Share address copies the actual reported WAN endpoint only after a mapping succeeds. The finite mapping expires; Share can renew it. Stop removes the mapping only if it still belongs to this session.

The runtime command currently uses `java -jar <serverJar> nogui`: it is intended for executable Paper/Spigot/Fabric-style server JARs already installed by the user. Modern Forge/NeoForge dedicated-server distributions requiring `@unix_args.txt`/`@win_args.txt`, their installed libraries or platform scripts are not provisioned by this path; client loader support must not be described as universal server-runtime support. Configure a supported runtime and matching mods. This implementation does not download or accept a server EULA automatically.

Ordinary Play binds to loopback. To share a world initially launched without sharing, stop it and use Play & share. UPnP is optional and may fail due to router support, upstream NAT or CGNAT; a private/invalid WAN address is not presented as public connectivity. Failure is visible and suggests manual forwarding/tunnel alternatives. A router accepting a mapping is not proof that an external friend successfully joined. No real mapping is opened by tests.

## CLI

Commands use the same services as the UI. Quote paths containing spaces. See `.profiles help` and `.mc help` for current syntax. Existing commands without `--profile` retain legacy behavior.

```text
.profiles create base 1.20.4 FABRIC --template
.profiles create plugins 1.20.4 PAPER --type PLUGINS
.profiles list --type MODS
.profiles clone base survival --version 1.21
.check mods --profile survival --version 1.21
.update mods --profile survival --version 1.21 --confirm-disable
.mc settings set defaultTemplate base
.mc settings set javaHomes "/path/to/java-home"
.mc settings set clientId "your-registered-application-id"
.mc signin
.mc signin cancel
.mc loader survival
.mc servers add "My server" "example.invalid:25565" FABRIC
.mc servers import "/path/to/servers.dat" VANILLA
.mc servers ping <saved-id-or-address>
.mc servers join <saved-id> --clone new-client --version 1.21 --confirm-disable
.mc launch survival --server example.invalid:25565
.mc stop survival
.mc worlds create local plugins
.mc worlds configure local --client client-1.20.4 --server-jar "/path/to/paper.jar"
.mc worlds launch local
.mc worlds stop local
```

The profile version in an example must be a real supported release and the matching Java/loader installed; these commands are documentation, not commands automatically executed on the user's system. The explicit update/clone command authorizes its normal replacements; `--confirm-disable` additionally approves the displayed incompatible files. To review first, use `.check`.

## Validation and desktop review

Build uses the existing Maven POM/JPM dependency declarations and Java 9 language target. Tests use JUnit Jupiter 5.9.2. New tests exercise real service composition using synthetic transports, process doubles and temporary directories; recorded Mojang 1.20.4 metadata is public primary data verified against its official SHA1. No Minecraft game code, real credentials or private account responses are test fixtures.

```sh
mvn -Djava.awt.headless=true '-Dtest=**/mc/**/*Test,NativeMinecraftPanelTest,UtilsStringTest,UtilsEnvironmentTest,UtilsFileTest,UtilsMapTest' package
```

This runs all added native tests plus the selected existing offline utility regressions. It deliberately excludes older integration tests that contact Google/Modrinth/Bukkit/Spiget/Jenkins or install/run Steam/Minecraft servers. Compiling all production and test sources is not a claim that the full network-dependent suite passed. Actual measured counts and historical failures are recorded in the contribution's validation report.

`NativeMinecraftPreview` in test sources opens a disposable desktop populated with clearly labeled synthetic data. It uses no real Minecraft account or downloads, and does not start a game/server or router mapping. Its data path is printed. It is for reviewing add/edit, profiles/templates and world configuration, not proof of live gameplay. After building, run its main class with the test/runtime dependency classpath and a parent temporary directory. Do not accept EULAs or enter credentials merely to review the fixture.

Before submitting, the contributor should inspect the changes and verify the desktop behavior personally. Maintainers said they will perform final live Microsoft-authenticated game/join acceptance. Automated fixtures, desktop renders, CI success and a submitted PR are not a bounty award or a payment.
