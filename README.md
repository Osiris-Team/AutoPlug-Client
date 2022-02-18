<div align="center">
   <img src="https://i.imgur.com/BMpvtWP.png">
</div>
  
## Links
- Download AutoPlug from [Spigot](https://www.spigotmc.org/resources/autoplug-automatic-plugin-updater.78414/).
- For beta versions, visit the [AutoPlug-Releases](https://github.com/Osiris-Team/AutoPlug-Releases) repo.
- Follow the [AutoPlug-Client-Development](https://bit.ly/acprogress) and see what we are currently working on.
- Support, chat, updates and news over at [Discord](https://discord.com/invite/GGNmtCC)
- Support the development by [donating](https://www.paypal.com/donate?hosted_button_id=JNXQCWF2TF9W4)

## What can it do?
- Automated self updating
- Automated JRE (Java Runtime Enviornment) updating
- Automated server software updating
- Automated plugins updating
- Automated, scheduled server restarting
- Automated, modularized backup creation
- Fast, asynchronous, multithreaded task execution
- Have synchronized (shared) folders
- Web-Panel for starting, stopping restarting the server and viewing the last updating results summaries

Premium [AutoPlug-Web](https://autoplug.one) features:

- [Online Console](online-console.gif)
- [Online File-Manager](file-manager.gif)

## Contribute ![Build test](https://github.com/Osiris-Team/AutoPlug-Client/workflows/build/badge.svg)

### Beginners
- [contribute-to-projects](https://www.jetbrains.com/help/idea/contribute-to-projects.html)
- [clone-a-project-from-github](https://blog.jetbrains.com/idea/2020/10/clone-a-project-from-github/)

### Bounties
Some open issues are marked with a tag/label like this: `bounty: 10€`, which means that the first person
creating a pull request for fixing that issue will receive the bounty (10€). Note that your code must have
at least one test demonstrating that the implemented fix/feature is working. Also note that payments are only made via PayPal, so make sure
that the public Github page of your profile, with which you commit the pull request, contains your PayPal email address somewhere.
By commiting to an issue with the bounty tag you agree to these terms.

### Important
- Create an issue first, if you want to do big changes.
- You can pick and work on any open issue. 
- Please comment on the issue you picked, that you are working on it.

Details:
- Written in [Java](https://java.com/),
  with [JDK 8](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html), inside
  of [IntelliJ IDEA](https://www.jetbrains.com/idea/)
- Built with [Maven](https://maven.apache.org/), profiles: `clean package` to create the `AutoPlug-Client.jar` 
- `AutoPlug-Client.jar` gets exported to the `AP-TEST-SERVER` folder, for further testing

## Libraries
Note that this list may be outdated. Check the [pom.xml](/pom.xml) file for our current dependencies.
- [AutoPlug-Core](https://github.com/Osiris-Team/AutoPlug-Core) contains frequently used code accross all AutoPlug
  projects in one jar.
- [Dream-Yaml](https://github.com/Osiris-Team/Dream-Yaml) is used for handling YAML files.
- [Better-Thread](https://github.com/Osiris-Team/Better-Thread) enables display of 'live-tasks'.
- For console coloring and task display [Jansi](https://github.com/fusesource/jansi) and [Jline](https://github.com/jline/jline3) is used.
- [Zip4j](https://github.com/srikanth-lingala/zip4j) to unpack and handle jar/zips.
- Server restarts are scheduled with [Quartz](http://www.quartz-scheduler.org/).
