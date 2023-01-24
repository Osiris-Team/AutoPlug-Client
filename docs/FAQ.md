# Frequently Asked Questions

## AutoPlug-Client - Common Questions

---

### **What is AutoPlug Client?**  
AutoPlug Client is a standalone, executable java program that runs in place of your Minecraft server.

### **What does AutoPlug Client do?**  
The main purpose of AutoPlug Client is to automatically identify and update any plugins or mods you use with your Minecraft server.

### **Is AutoPlug Client a plugin or mod?**  
No! AutoPlug Client is neither a plugin nor mod and should not be treated as such.  
It runs entirely independently of your Minecraft server.

### **What features does AutoPlug Client have?**  
AutoPlug Client currently has the following features:
- AutoPlug Client self-updating
- Standalone Java installation and updating
- Automatic plugin identification and updating
- Automatic mod identification and updating
- Automatic Minecraft server identification and updating
- Adjustable updater profiles
- And much more

For a full list of features, please visit <https://autoplug.one/features>

### **Can AutoPlug Client update my paid/premium plugins?**  
No. Due to the nature of how the paid plugins are hosted, as well as the hosts' API limitations and Terms of Service, AutoPlug Client will never contain functionality for downloading paid/premium plugins.

As an alternative, SPPU [[Link](https://github.com/Osiris-Team/SPPU)] may be better suited.  
*It is highly advised to read the entire readme before using this tool.*

### **How do I install AutoPlug Client?**  
Go to the AutoPlug Client [installer page](https://autoplug.one/installer) and follow the steps carefully.

### **Can I install AutoPlug Client on a server host?**  
Yes! For instructions on how to do so, please read [How To - Install AutoPlug Client on a Server Host](How%20To%20-%20Install%20AP%20Client%20on%20a%20Server.md).

### **How do I change the settings?**  
All settings are kept in their own respective `.yml` config files that can be found in the `autoplug` folder in your Minecraft server directory.

### **How do I know what some settings do?**  
Every `.yml` config file has explanations with examples written both at the very top of the file and next to each setting.

### **I still have questions!**  
Please make either a Github Issue or a post in the Discord's #help channel.

---

## AutoPlug-Client - Troubleshooting

---

### **I just installed AutoPlug Client and it's taking forever to start!**  
This is normal for first-time installations and can take up to 3 - 5 minutes depending on system resources, network availability, and number of plugins/mods.

### **I get the message `Starting server: xxx.jar` but nothing happens!**  
This usually means that something is wrong with your Java installation.  
By default, AutoPlug Client installs its own standalone Java runtime in `./autoplug/system`.

To make AutoPlug Client reinstall Java:
1. Delete the `system` directory in `./autoplug`.
2. In the Updater config (default: `./autoplug/updater.yml`), set `updater: java-updater: build-id:` to `0` and save the file.
3. Restart AutoPlug Client.

### **Some of my plugins/mods aren't being detected or updated!**  
This can happen sometimes if the plugin/mod author did not include critical metadata in the file and AutoPlug may exclude it from future checks.

For plugins, you will have to manually enter the plugin author's username in the `author` field as well as either the `spigot-id` or `bukkit-id` for the plugin in your `plugins.yml` config file.  
For mods, you will have to manually enter the mod author's username in the `author` field as well as either the `modrinth-id` or `curseforge-id` for the mod in your `mods.yml` config file.

As a last resort, you can use either the `github` or `jenkins` custom fields if the plugin/mod developer has it available.  
Please see the top of the `plugins.yml`/`mods.yml` file for instructions and examples on filling these out.

### **My host keeps telling me that my server is crashing!**  
This usually happens when AutoPlug Client tries to restart when updating itself.  
Some hosts don't like it when the server manages itself and will read any close not caused by them as a crash.

The recommended workaround is setting the `self-updater` profile in your `updater.yml` config file from `AUTOMATIC` to `NOTIFY` or `MANUAL`.  
Alternatively, you can disable the `self-updater` by setting `enable` to `false`, but this highly not recommended.

### **AutoPlug keeps making huge backups!**  
Please review your settings in the `backup.yml` config file and make sure your inclusions and exclusions are formatted correctly.

### **I still need help!**  
Please make either a Github Issue or a post in the Discord's #help channel and include the `latest.log` file from `./autoplug/logs`.

---

## AutoPlug-Web - Common Questions

---

### What is the group permissions json file?
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
    "allowedFilesToWrite": []
  }
}
```
As you can see both the console and system console have lists of allowed commands 
which are only relevant if `fullWrite` is set to `false`. 
If its set to `true` this group will have 
full write access and thus be able to send any command to the console.

Keep in mind that the console is able able to execute AutoPlug-Client commands and
server-software specific commands. The system console is able to execute any system-specific
command. Thus its highly recommended **not** to give full write access to **any** of your groups.

AutoPlug-Web will compare
the sent staff command against each command in the `allowedCommands` list and only
allow it, if there is a 100% match. There are however two wildcards, namely `*` and `*->`
since sometimes commands want arguments. So instead of having to add each command-argument combination
you can use these wildcards.
- `*` allows any word or number.
Example: `ban *`, now this group will be able to execute `ban peter` or `ban john`, but **not**
`ban peter 10`, for that to work you would need to add `ban * *` or `ban *->` instead.
- `*->` allows anything from this point onwards until the end of the line.

Similar wildcards are available for the files manager, but with a slightly different meaning:
- `./` is the current working directory of the AutoPlug-Client.
- `*` allows any character sequence (including spaces). Example: `./*.exe`
would only allow access to files in the current working directory ending with `.exe`.
If you wanted to also allow access to the `.exe` files in sub-directories just append `+sub`, so
the permission would look like this: `./*.exe +sub`.
- `+sub` allows access to all sub-directories.

---

## AutoPlug-Web (self-host) - Common Questions

---

### Installation
1. After buying a license, head over to https://autoplug.one/installer
and scroll down to the AutoPlug-Web installer section. 
2. Fill in the empty fields and download the generated zip file.
3. Unpack it in an empty directory, on the device you want to run it (note that latest Java is required).
4. Open your terminal, `cd` in that directory and execute `java -jar AutoPlug-Web.jar` to start it.
5. Enjoy AutoPlug-Web!
