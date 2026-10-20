# Unified client/server profiles

Profiles are stored under `~/.autoplug/profiles` with independent instances under
`~/.autoplug/instances/<id>`. A profile records its id, `MODS` (client), `PLUGINS`
(server plugins), or `MODS_SERVER` (server mods) type, game version, loader and
template status. Client and server mods live in the instance's `mods` directory;
plugins live in `plugins`. Each of the three types has its own independent active
default for update commands. `mods-server` and `mods_server` are supported CLI
aliases for `MODS_SERVER`. Changes never replace the global server updater
configuration or change the process working directory.

Examples:

```text
.profiles create fabric-base 1.20.1 fabric --template
.profiles create paper-base 1.20.1 paper --type plugins --template
.profiles create fabric-server 1.20.1 fabric --type mods-server
.profiles activate fabric-server
.check mods-server
.update mods-server
.profiles list --type plugins
.profiles clone fabric-base 1.21.1 fabric-new
.check mods --profile fabric-new
.update mods --profile fabric-new
.profiles template fabric-new true
.profiles delete fabric-new
```

Clone copies mutable settings and imports jars into the shared content-addressed
cache. It then checks the target release set, including downgrades, required
dependencies and incompatible projects. Unavailable/unknown artifacts appear in
the review summary. CLI migration that would disable them requires
`--confirm-disabling`; without it, an unsuccessful new clone is removed and the
source is preserved. The dashboard presents the same review before applying.

Network or malformed-response failures are errors, not compatibility results.
They stop the entire update and preserve the instance. Artifacts are staged and
their provider hashes and jar format verified before replacement. Original
hashes are checked before and after staging. Per-profile locks, original-file
backups and a journal support rollback and recovery of an interrupted prepared
transaction. Names and paths are checked before installing or deleting files.

Modrinth lookup identifies exact local bytes and resolves releases for the
explicit Minecraft version and loader. Required dependencies are included and
pinned dependency versions are validated. Plugin loader compatibility is
explicit: Bukkit and Spigot profiles do not accept Paper-only artifacts.

Explicit provider project IDs can be recorded in the instance's
`asset-sources.properties` when an artifact is not identifiable by Modrinth hash:

```properties
some-mod.jar.modrinth=project-id-or-slug
another-mod.jar.curseforge=12345
plugin.jar.spigot=12345
worldedit.jar.bukkit=31043
```

Successful migrations preserve provider identities under their new filenames.
CurseForge resolution reuses AutoPlug's configured API client; unavailable keys,
missing authorized download URLs or transport errors stop the migration.
CurseForge loader/game filters, pagination and declared file metadata are checked.
Bukkit files are selected by declared Minecraft version and their supplied MD5
is verified. Spiget only exposes tested versions for its current resource; the
updater does not invent historical version compatibility. If that current
resource does not declare support, the artifact needs explicit disabling or a
separately configured compatible project.

`UnifiedProfilesTest` exercises isolation, clone migration, explicit disabling,
re-enabling, dependency installation, collisions, changed files, failed downloads,
checksums, cache corruption and transaction recovery. `ProfileProviderTest`
uses real local HTTP sockets for transport failures and exercises target-loader
selection and resource-service reuse. `ProfileProviderLiveSmoke` is an opt-in
main class for actual public Modrinth/Bukkit metadata GETs. It downloads no jars,
uses no account and launches no Minecraft process. Live Microsoft-authenticated
launch/join acceptance remains with maintainers, as specified for the bounty.
