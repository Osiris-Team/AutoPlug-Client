# Frequently Asked Questions
- [AutoPlug-Client - Common Questions](#autoplug-client---common-questions)
- [AutoPlug-Web - Common Questions](#autoplug-web---common-questions)
- [AutoPlug-Web (self-host) - Common Questions](#autoplug-web-self-host---common-questions)

## AutoPlug-Client - Common Questions

---

## **What is it? Purpose?**  
AutoPlug Client is a standalone, executable Java program that has multiple execution modes: Server-Wrapper, CLI-Tool, Background-Service.
Its main purpose is to automate updating of any sofware related to servers and simplify server maintenance in general.
While all server types are compatible, the majority of its features cater specifically to Minecraft,
which is renowned as the world's top-selling game and boasts an exceptionally extensive modding community.

## **How do I install it?** 

**I am self-hosting**: Go to the AutoPlug Client [installer page](https://autoplug.one/installer) and follow the steps.

**I am using a server hoster**:
If using server hosts, please read [How To - Install AutoPlug Client on a Server Host](How%20To%20-%20Install%20AP%20Client%20on%20a%20Server.md).

**I want it on my Minecraft client:**
1. Go to https://autoplug.one/installer (select Minecraft, Client and set your version) and download the generated zip file.
1. Open your Minecraft installation root folder (contains /mods), unpack the zip, and move its files into the root folder.
1. Run the start script.
1. Ignore warnings related to the server or connection problems.
1. Enter `.help` for a list of all commands.
1. Enter `.backup` then `.check mods` to update your mods.

## **How does it work? Server-Wrapper? CLI-Tool? Background-Service?**  
AutoPlug performs all its tasks when your server is offline, thus its recommended
to run AutoPlug as a Server-Wrapper so that it can start/stop/restart your server.
In this case AutoPlug will start your server automatically when it launches and show its console output.

To achieve full automation the daily server restarter is used (enabled by default),
which restarts your server at specific times in the day. AutoPlug runs its tasks when your server restarts.

If you don't want AutoPlug to have access to your servers' console, you can install AutoPlug as CLI-Tool or
Background-Service:

- CLI-Tool (command-line interface tool): Manually run the AutoPlug commands/tasks once in a while (remember that your
  server must be offline).
- Background-Service: Runs the tasks periodically but does not know if the server is offline, thus you would have to
  take care of that.

Note that these "execution modes" are nothing more that simple configuration presets.

## **Is it a plugin or mod?**

No! AutoPlug Client is neither a plugin nor mod and should not be treated as such.  
It runs entirely independently of your Minecraft server.

## **Set server RAM? Change startup flags and tune performance?**

The start command inside `/autoplug/general.yml` is used
to start your server, thus this is where you should put your flags/arguments
to modify the min/max RAM usage of your server, as well as any other flags/arguments.

Many Minecraft servers use [Aikars' Flags](https://docs.papermc.io/paper/aikars-flags)
in their start command. Check them out if you want to learn more about this topic.

## **Can it update my paid/premium plugins?**

No. Due to the nature of how the paid plugins are hosted, as well as the hosts' API limitations and Terms of Service,
AutoPlug Client will never contain functionality for downloading paid/premium plugins.

As an alternative, SPPU [[Link](https://github.com/Osiris-Team/SPPU)] may be better suited.  
*It is highly advised to read the entire readme before using this tool.*

## **How do I change the settings? Live-Settings?**  
All settings are kept in their own respective `.yml` config files that can be found in the `autoplug` folder in your Minecraft server directory.
Almost all settings can be changed without needing to restart AutoPlug to have effect (Live-Settings), since
related settings are freshly loaded before executing a task.

## **How do I know what some settings do?**  
Every `.yml` config file has explanations with examples written both at the very top of the file and next to each setting.

## **I still have questions!**  
Please make either a Github Issue or a post in the Discord's #help channel.

---

## AutoPlug-Client - Troubleshooting

---

## **Just installed and it's taking forever to start!**  
By default backup creation is enabled, so before executing its tasks a backup will be created that
can take up to 3 - 5 minutes depending on system resources and size of the directory.
Besides that there may be many updates/files that have to be downloaded, so do not abort
this process to ensure a correct installation.

## **I get the message `Starting server: xxx.jar` but nothing happens!**  
This usually means that something is wrong with your Java installation.  
By default, AutoPlug Client installs its own standalone Java runtime in `./autoplug/system`.

To make AutoPlug-Client reinstall Java:
1. Delete the `system` directory in `./autoplug`.
2. In the Updater config (default: `./autoplug/updater.yml`), set `updater: java-updater: build-id:` to `0` and save the file.
3. Restart AutoPlug Client.

## **Some of my plugins/mods aren't being detected or updated!**  
This can happen sometimes if the plugin/mod author did not include critical metadata, and
fixed quite easily by opening either `/autoplug/plugins.yml` or `/autoplug/mods.yml`,
finding your plugin/mod in that file, and providing either a `spigot-id` or `bukkit-id` for the plugin, or
in case of a mod a `modrinth-id` or `curseforge-id`.

If the downloads on those pages do not return a .jar/.zip file, but for example forward to another page,
you must use either use the `github` or `jenkins` alternative. If the plugin/mod developer has none
of those available (which pretty much is never the case) its simply not possible to auto-update that plugin/mod.  
Please see the top of the `plugins.yml`/`mods.yml` file for instructions and examples on filling these out.

If you have a premium account and your server connected, AutoPlug will additionally check
our database for additional info and fill it in, if that info is used by over 50 other servers too.

## **Download of 'plugin/mod.jar' failed because of invalid content type: text**
The download fails due to the download link redirecting to another page (see question above for details). 
In this situation, use an alternative updating method for for that plugin. You can set it in `/autoplug/plugins.yml`.

## **My host keeps telling me that my server is crashing!**  
This usually happens when AutoPlug Client tries to restart when updating itself.  
Some hosts don't like it when the server manages itself and will read any close not caused by them as a crash.

The recommended workaround is setting the `self-updater` profile in your `updater.yml` config file from `AUTOMATIC` to `NOTIFY` or `MANUAL`.  
Alternatively, you can disable the `self-updater` by setting `enable` to `false`, but this highly not recommended.

## **AutoPlug keeps making huge backups!**  
Please review your settings in the `backup.yml` config file and make sure your inclusions and exclusions are formatted correctly.

## **I still need help!**  
Please make either a Github Issue or a post in the Discord's #help channel and include the `latest.log` file from `./autoplug/logs`.

---

## AutoPlug-Web - Common Questions

---

## What is the group permissions json file?
This file allows you to define permissions for a group and it could look something like this:
```json
{
  "console":{
    "fullRead": true,
    "fullWrite": false,
    "allowedCommands": [
      "help",
      ".help"
    ]
  },
  "systemConsole":{
    "fullRead": true,
    "fullWrite": false,
    "allowedCommands": []
  },
  "files":{
    "fullRead": false,
    "fullWrite": false,
    "allowedFilesToRead": [],
    "allowedFilesToReadAndWrite": []
  }
}
```
### Commands
As you can see both the console and system console have lists of allowed commands 
which are only relevant if `fullWrite` is set to `false`. 
If it's set to `true` this group will have 
full write access and thus be able to send any command to the console.

Keep in mind that the console is able to execute AutoPlug-Client commands and
server-software specific commands. The system console is able to execute any system-specific
command. Thus it's **not** recommended to give full write access to **any** of your groups.

AutoPlug-Web will compare
the sent staff command against each command in the `allowedCommands` list and only
allow it, if there is a 100% match. There are however two wildcards, namely `*` and `*->`
since sometimes commands want arguments. So instead of having to add each command-argument combination
you can use these wildcards.
- `*` allows any word (which can include numbers and any special chars).
  Example: `ban *`, now this group will be able to execute `ban peter` or `ban john`, but **not**
  `ban peter 10`, for that to work you would need to add `ban * *` or `ban*->` instead.
- `*->` allows anything from this point onwards until the end of the line.
- Note that you can **not** use both in one command, its either `*` or `*->`.

<details>
<summary>Show Java code used to check commands.</summary>

```java
class Demo {
  public static boolean isCommandAllowed(String command, List<String> allowedCommands) {

    // First check for an exact match with the allowed commands
    if (allowedCommands.contains(command)) return true;

    // Next compare with allowed commands that contain *->
    // Everything before *-> must match the command
    for (String allowedCommand : allowedCommands) {

      if (allowedCommand.equals("*->")) return true; // Only *-> with nothing preceding then allow everything.

      int i = allowedCommand.indexOf("*->");
      if (i >= 0) {
        String precedingStuff = allowedCommand.substring(0, i);
        if (command.startsWith(precedingStuff)) return true;
      }
    }

    // Next compare with allowed commands that contain * or multiple like "command * * p * p2"
    String[] commandWords = command.split(" ");
    for (String allowedCommand : allowedCommands) {

      if (allowedCommand.contains("*->")) continue;

      // The amount of words must match
      String[] allowedCommandWords = allowedCommand.split(" ");
      if (commandWords.length != allowedCommandWords.length) continue;

      // All words must match except the ones where the allowed command word is *
      boolean allMatch = true;
      for (int wordI = 0; wordI < allowedCommandWords.length; wordI++) {
        String allowedWord = allowedCommandWords[wordI];

        if (allowedWord.contains("*")) continue;

        if (!allowedWord.equals(commandWords[wordI])) {
          allMatch = false;
          break;
        }
      }
      if (allMatch) return true;
    }
    return false;
  }
}
```
</details>

### Files and Directories
You can add file and directory paths in Windows or Linux format to the `allowedFilesToRead` and
`allowedFilesToReadAndWrite` lists to allow your staff access.
Note that adding a directory allows access to its files, but not its directories (their contents can only be viewed, not read).
Similar wildcards are available for the files manager, but with a slightly different meaning:
- `./` or `.\` is the current working directory of the AutoPlug-Client.
- `*->` to allow access to all sub-directories too. For example `./*->` would allow access
to all files and directories and all sub-directories in AutoPlugs current working directory.
- Note that Windows `\` and Linux `/` file separators are treated as equal.

The `allowedFilesToRead` list contains paths to files or directories
that the staff is allowed to open, list and read.

The `allowedFilesToReadAndWrite` list contains paths to files or directories 
that the staff is allowed to open, list, read, modify, create or delete.
This is probably the list you will use the most.

<details>
<summary>Show Java code used to check paths.</summary>

```java
class Demo {
  public static boolean isFileAccessAllowed(FileDetails workingDir, String path, List<String> list) {
    if (path.isEmpty()) return false;

    // Make sure allowedPath and requestedPath both use no separator
    // This makes comparing simpler
    path = path.replace("\\", "").replace("/", "");

    for (String allowedPath : list) {
      if (allowedPath.isEmpty()) continue;

      if (allowedPath.contains("./")) allowedPath = allowedPath.replace("./", workingDir.getAbsolutePath() + "/");
      allowedPath = allowedPath.replace("\\", "").replace("/", "");

      // Check for exact match
      if (allowedPath.equals(path)) return true;

      // Check if allowedPath contains *->
      // Then check path names until that point
      int i = allowedPath.indexOf("*->");
      if (i >= 0) {
        allowedPath = allowedPath.substring(0, i); // Remove *->
        // path must be same length as allowedPath or longer (meaning a sub-directory)
        if (path.length() >= allowedPath.length()) {
          if (path.substring(0, i).equals(allowedPath)) return true;
        }
      }
    }
    return false;
  }
}
```
</details>

---

## AutoPlug-Web (self-host) - Common Questions

---

## Installation
1. After buying a license, head over to https://autoplug.one/installer
and scroll down to the AutoPlug-Web installer section. 
2. Fill in the empty fields and download the generated zip file.
3. Unpack it in an empty directory, on the device you want to run it (note that latest Java is required).
4. Open your terminal, `cd` in that directory and execute `java -jar AutoPlug-Web-selfhost.jar` to start it (or run the start script).
5. Access the website under http://localhost or https://localhost (if running on same device, otherwise https://INSERT_DEVICE_PUBLIC_IP).
6. Register and make your account premium by entering `make premium <email>` in the AutoPlug-Web terminal.
7. Enjoy AutoPlug-Web!

Keep in mind that you must change the AutoPlug-Web ip/domain of all your AutoPlug-Clients:
1. Go to the directory where your AutoPlug-Client.jar is located.
2. Open `./autoplug/system/config.yml`.
3. Replace the `autoplug-web-ip` with the public ip/domain your AutoPlug-Web server is running from, for example `my-autoplug-web.com`, or `localhost` if webserver and client on same device.
4. Restart the client, or enter `.con reload`.
5. Voila. Now the client connects to your AutoPlug-Web, instead of https://autoplug.one. 

## What are the differences to autoplug.one?
- Daily license check, that requires internet connection. Without a valid license you cannot run it.
- Self-signed certificate is always generated and used for SSL, instead of [ACME](https://github.com/shred/acme4j).
- Payment services initialisation is optional.
- Sent mails are not encrypted.
- /store is blank.
- May be a few versions behind from autoplug.one.
