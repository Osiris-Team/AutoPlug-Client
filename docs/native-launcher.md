# Native Minecraft launcher, profiles and virtual worlds

AutoPlug's desktop dashboard includes **Server Browser**, **Virtual Worlds**, **Profiles**, **Server Manager** and **Settings**. The launcher downloads Minecraft metadata, libraries, assets and loader components directly; it does not embed another launcher. The existing server wrapper and updater commands remain available.

AutoPlug targets Java 9 or later. Minecraft runs in a separate JVM selected from its version metadata. Settings supports Java 8, 17 and 21 executable overrides and additional `major=path` entries. If no matching runtime is configured or already installed, AutoPlug uses its Adoptium provider to download one. Runtime availability depends on the operating system and architecture.

## Desktop workflow

Enable the existing system-tray option in AutoPlug's general configuration and click the tray icon to open the dashboard. Closing the window hides it; it does not stop AutoPlug or its running worlds. When the operating system has no system tray, the dashboard opens as a normal window.

1. In **Profiles**, create a client profile with type `MODS`, a Minecraft version and a loader. A vanilla client also uses the `MODS` type, with loader `VANILLA` and an empty collection.
2. Add mod or plugin JARs to the profile's collection directory, available through **Open folder**, or use `.profiles add` below. Provider identities in `collection.json` allow version-aware update checks; `.profiles add --modrinth` records a Modrinth project ID or slug.
3. In **Settings**, select an offline player name or sign in with Microsoft. Configure runtime overrides only if automatic selection is unsuitable.
4. Use **Launch client**, or select a server in **Server Browser** and choose a compatible client profile. The browser shows Minecraft version, MOTD, player count and latency through AutoPlug's existing status ping. A server's reported version is not proof that every installed mod will be compatible; review the selected loader and collection.

The browser imports favorites from the standard Minecraft `servers.dat` when the dashboard starts. Importing reads the vanilla file and keeps AutoPlug favorites separately; it does not rewrite the vanilla file. Favorites can also be added through the dashboard or CLI.

## Profiles and migration

| Type | Purpose | Collection folder |
| --- | --- | --- |
| `MODS` | Native Minecraft client, vanilla or modded | `mods/` |
| `PLUGINS` | Dedicated server with plugins | `plugins/` |
| `MODS_SERVER` | Dedicated server with mods, or vanilla server | `mods/` |

Client loaders are `VANILLA`, `FABRIC`, `QUILT`, `FORGE` and `NEOFORGE`. Virtual-world server installation supports vanilla, Fabric, Quilt, Forge and NeoForge, and plugin servers such as Paper, Spigot and Purpur through AutoPlug's existing server updater. Bukkit selects the Spigot build path. Proxy software such as Velocity or BungeeCord cannot host a virtual world. Versions must exist in the selected provider's metadata; Spigot installation additionally runs BuildTools and requires its build prerequisites, including Git.

Mark reusable packs as **templates**, then clone them before playing. A clone has its own configuration and collection metadata. Changing its game version or loader marks migration as pending. AutoPlug checks the target version, including downgrades, and shows a summary before applying changes. A pending migration cannot be launched.

**Check** finds compatible releases without downloading or installing them. **Update** applies the reviewed plan. Downloads are staged and verified before installed JARs are replaced. During a confirmed migration, artifacts without a compatible release or sufficiently precise provider identity become `name.jar.disabled`; they are retained for recovery. A provider/network error is reported separately and blocks application rather than being interpreted as incompatibility. Configuration files are preserved.

Modrinth and CurseForge identities support target-version checks. A generic GitHub, Spigot or custom update URL may not describe game/loader compatibility, so it cannot by itself establish compatibility during migration. The existing provider integrations still handle ordinary updates where applicable.

Profiles in use are locked against concurrent update, clone or delete operations. Deleting a profile moves it into recoverable trash; profiles referenced by saved worlds must be detached or their worlds removed first.

## Commands

Commands can be entered in AutoPlug's interactive console. They can also be passed directly to the executable JAR:

```text
java -jar AutoPlug-Client.jar .profiles list
```

The examples below show the command portion. Replace `PROFILE_ID`, `CLONE_ID`, `SERVER_PROFILE_ID`, `CLIENT_PROFILE_ID`, `WORLD_ID` and `REGISTERED_CLIENT_ID` with actual values. Creation commands print generated IDs. Quote names and file paths containing spaces.

```text
.profiles list
.profiles list --type mods-server
.profiles create "Fabric base" 1.20.1 FABRIC --type mods --template
.profiles create "Local server" 1.20.1 PAPER --type plugins
.profiles add PROFILE_ID "path/to/example.jar" --modrinth PROJECT_ID_OR_SLUG
.profiles template PROFILE_ID
.profiles template PROFILE_ID --off
.profiles clone PROFILE_ID 1.20.4 --name "Updated pack" --loader FABRIC
.check mods --profile CLONE_ID
.update mods --profile CLONE_ID --yes
.check plugins --profile SERVER_PROFILE_ID
.update plugins --profile SERVER_PROFILE_ID
.profiles delete PROFILE_ID --yes
```

Use `mods` for both `MODS` and `MODS_SERVER`, and `plugins` for `PLUGINS`. A CLI update prints a fresh plan. Migration requires `--yes`, which explicitly authorizes the reported replacements and disabling of unresolved artifacts. Clone also accepts `--yes` to apply its migration immediately; omit it when you want to review first. The examples use `--profile` to select the isolated collection explicitly.

```text
.mc account offline LocalPlayer
.mc launch CLIENT_PROFILE_ID
.mc launch CLIENT_PROFILE_ID --server example.org:25565
.mc launch CLIENT_PROFILE_ID --server example.org --port 25566
.mc servers add "My server" example.org:25565
.mc servers import
.mc servers list
```

Server listing imports vanilla favorites and pings each saved server. Direct `.mc launch` selects the supplied profile; the dashboard provides the profile selection and clone/migration workflow.

## Microsoft accounts

Microsoft sign-in requires a **registered public application client ID** configured in Settings. The application must support the device authorization flow for personal Microsoft accounts and be permitted to access the Minecraft services it calls. AutoPlug does not ship a borrowed launcher ID or require a client secret. A missing or unapproved application registration cannot be repaired by entering an account password into AutoPlug.

Choose **Sign in with Microsoft**, then complete the displayed device-code instructions on Microsoft's verification page. AutoPlug exchanges the resulting authorization through Xbox services, checks Minecraft Java Edition entitlement and retrieves the Minecraft profile. Account policy, entitlement and application-registration failures are reported to the user.

**Remember Microsoft account on this computer** is optional and off by default. When enabled, account and refresh tokens are stored in the launcher's local `accounts.json` with owner-restricted permissions where the filesystem supports them. This is local credential storage, not an encrypted credential vault. Leave it unchecked for an in-memory session.

```text
.mc account microsoft REGISTERED_CLIENT_ID
.mc account microsoft REGISTERED_CLIENT_ID --remember
```

Use the first command in a running AutoPlug console to keep sign-in within that session. A standalone command exits after sign-in, so a later independent JAR invocation needs either a new sign-in or the explicit `--remember` option. Offline names provide a local test identity; they do not authenticate to Microsoft-backed multiplayer servers.

## Virtual worlds and sharing

In **Virtual Worlds**, create a world with a server profile and a playable client profile. They must use compatible game versions and mod loaders. Each world owns a separate save directory, even when multiple worlds use the same server profile. Mod/plugin JARs are materialized from the profile; world-specific configuration and saves remain separate. An existing world icon or server icon is shown as its thumbnail when available.

Read and accept the [Minecraft EULA](https://aka.ms/MinecraftEULA) for the world before launching it. AutoPlug does not silently accept it during profile creation or server installation. The CLI flag below records that explicit choice:

```text
.mc worlds create "Local world" SERVER_PROFILE_ID CLIENT_PROFILE_ID
.mc worlds launch WORLD_ID --accept-eula
.mc worlds list
.mc worlds share WORLD_ID
.mc worlds stop WORLD_ID
```

`--accept-eula` is also supported by `worlds create`. After acceptance is recorded, subsequent launches need no flag. `worlds launch WORLD_ID --share` additionally requests sharing, subject to the settings and account requirements below.

Launching starts a background dedicated server bound to `127.0.0.1`, waits for Minecraft status readiness, then connects the native client to it. The preferred port from Settings is used when available; an occupied port causes selection of another free loopback port. Closing the client sends the server a graceful `stop`; startup failures and application shutdown also clean up owned processes.

Sharing requires all of the following:

- A Microsoft-authenticated world session. Offline worlds use local authentication and remain loopback-only; sign in and relaunch before sharing.
- **Use UPnP when I explicitly share a world** enabled in Settings. This permission is off by default and does not itself start sharing. Turning it off releases existing owned mappings.
- An explicit **Share** action or `--share` launch flag, plus a compatible gateway with a public IPv4 address.

For sharing, AutoPlug opens a LAN TCP forwarding endpoint to the loopback server and requests a leased UPnP mapping. It reports the external address for friends to use. The Minecraft server itself remains bound to loopback. Closing the world removes the owned forwarding endpoint and mapping; AutoPlug does not overwrite or delete another application's mapping.

If automatic sharing is unavailable, the local world continues and the result explains the failure. A TCP tunnel can target the displayed `127.0.0.1:port`. Manual router forwarding alone cannot reach a loopback-bound server; it also needs an appropriate LAN forwarding endpoint. AutoPlug does not silently alter firewall rules, and a successful mapping is not proof that every upstream firewall permits the connection.

Running sessions belong to the AutoPlug process that launched them. Use its dashboard or interactive console for `share` and `stop`; a second standalone JAR invocation cannot control the first process's in-memory sessions. A standalone world launch keeps AutoPlug running until the session ends. Closing only the dashboard window leaves the session running in the tray.

## Storage and isolation

The default data root is `~/.autoplug`, where `~` means the operating-system user's home directory. To isolate a test installation, set `-Dautoplug.home=PATH` **before** `-jar` and use the same value for every command.

| Location under the data root | Contents |
| --- | --- |
| `profiles/<id>/` | Profile identity, collection metadata, configuration and mod/plugin collection |
| `worlds/<id>/world.json` | World identity, profile references and recorded EULA choice |
| `worlds/<id>/server/` | Dedicated server files, world saves and `autoplug-server.log` |
| `cache/` | Shared game downloads, libraries, assets, installer artifacts, content-addressed JARs and runtimes |
| `servers.json` | AutoPlug server favorites |
| `settings.json` | Launcher preferences and account selection |
| `accounts.json` | Credentials only when account persistence is explicitly requested |
| `trash/`, `worlds/.trash/` | Recoverable removed profiles/worlds |

Artifacts use hard links when possible, with symbolic-link or copy fallback. AutoPlug replaces an artifact link when updating instead of overwriting shared bytes. Treat cached JARs and their linked copies as immutable; do not edit their contents in place. Mutable settings and world saves are copied into independent directories. The legacy server's working directory and global updater configuration are not repointed to implement profile operations.

## Verification and optional live smoke checks

The focused automated tests use provider fixtures, real local child JVMs, loopback Minecraft status responses and a simulated UPnP SOAP gateway. They exercise profile isolation, migration, launch arguments, account exchanges, process shutdown and mapping ownership without using a real account or changing a router.

From a checkout with Maven and a suitable JDK, run:

```text
mvn "-Dtest=ProfileStoreTest,ProfileUpdatesTest,MinecraftLauncherTest,MicrosoftAccountServiceTest,JavaRuntimeManagerTest,WorldStoreTest,WorldServiceTest,MinecraftServerInstallerTest,UpnpSharingServiceTest,ServerBrowserTest,MineStatJsonTest,DashboardPanelTest" test
mvn -DskipTests package
```

The dependency-inclusive executable is `target/AutoPlug-Client.jar`. A live download/client smoke check is optional and contacts the official game/runtime providers; it may download substantial assets and opens a Minecraft window:

```text
java -Dautoplug.home=./launcher-smoke -jar target/AutoPlug-Client.jar .profiles create "Vanilla smoke" 1.20.1 VANILLA --type mods
java -Dautoplug.home=./launcher-smoke -jar target/AutoPlug-Client.jar .mc launch CLIENT_PROFILE_ID
```

Replace `CLIENT_PROFILE_ID` with the ID from the first command. Check that the selected runtime starts and the client reaches its title screen, then close it. To test Fabric, Quilt, Forge or NeoForge, create a separate profile with that loader and a version the provider supports. A successful metadata download or fixture test alone does not establish that the actual game started.

For a world smoke check, create a matching server profile and world in the same isolated data root. Review the EULA, explicitly accept it if appropriate, launch without `--share`, confirm the client joins its local world and verify that closing the client stops the server. Router sharing should be tested separately only by an operator who explicitly enables it.

Maintainer live validation still includes a registered Microsoft application, real device-code sign-in, entitlement verification, token refresh and a join to an authenticated server. Those outcomes depend on external account/application authorization and are not claimed by fixture-based tests. A real network sharing check also needs a suitable gateway and another client outside the LAN.
