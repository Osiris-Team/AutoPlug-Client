<div align="center">
   <img src="https://i.imgur.com/BMpvtWP.png">
</div>

 AutoPlug-Client is a standalone, executable Java program that has multiple execution modes: Server-Wrapper, CLI-Tool, Background-Service. Its main purpose is to automate updating of any sofware related to servers and simplify server maintenance in general. While all server types are compatible (including Steam game servers), the majority of its features cater specifically to Minecraft, which is renowned as the world's top-selling game and boasts an exceptionally extensive modding community.
 
## Links

- Download stable/beta versions at the [AutoPlug-Releases](https://github.com/Osiris-Team/AutoPlug-Releases) repo.
- Follow the [AutoPlug-Client-Development](https://bit.ly/acprogress) and see what we are currently working on.
- Support the development by [donating](https://www.paypal.com/donate?hosted_button_id=JNXQCWF2TF9W4).
- Issues or questions? Checkout the [FAQ](/docs/FAQ.md) and/or join our [Discord](https://discord.com/invite/GGNmtCC), for live chat/voice
support.

## What can it do?

- Automated self, server-software, plugins, mods and JRE (Java Runtime Enviornment) updating
- Scheduled server restarts
- Automated, modularized backup creation
- Fast, asynchronous, multithreaded task execution
- Have synchronized (shared) folders
- Web-Panel for starting, stopping restarting the server and viewing the last updating results summaries

Premium [AutoPlug-Web](https://autoplug.one) features:

- [Online Console](./docs/online-console.gif)
- [Online File-Manager](./docs/file-manager.gif)
- Staff Access
- Plugin details database

## Status
Stable and feature-complete, thus community driven development, meaning the lead developer will not develop new features and rely on pull requests by others, except for security issues and critical bugs.

## Community projects

- Docker support: https://github.com/lkkuma/AutoPlug-Client-Docker
- Pterodactly panel support: https://github.com/ImLunaUwU/PteroPlug

Developers: 

You can also use AutoPlug in your projects by adding it as a dependency
via [Maven/Gradle/Sbt/Leinigen](https://jitpack.io/#Osiris-Team/AutoPlug-Client/-SNAPSHOT). Some useful classes would be
the AutoPlugConsole for example, which lets you execute AutoPlug commands programmatically:

```java
AutoPlugConsole.executeCommand(".check plugins");
AutoPlugConsole.executeCommand(".check server");
// etc...
```

## Contribute
Thank you for considering to contribute, the community 💙 that! Just keep these things in mind:
- Create an issue first if you want to do big changes, so we can discuss it.
- Remember that big changes also require you to write tests.
- You can pick and work on any open issue. Let us know that you are working on it.
- Avoid using and writing static methods/fields/classes to ensure minimal memory usage.

Beginners:

- [contribute-to-projects](https://www.jetbrains.com/help/idea/contribute-to-projects.html)
- [clone-a-project-from-github](https://blog.jetbrains.com/idea/2020/10/clone-a-project-from-github/)

Details:

- Written in [Java](https://java.com/), with [JDK 8](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html), inside of [IntelliJ IDEA](https://www.jetbrains.com/idea/)
- Built with [1JPM](https://github.com/Osiris-Team/1JPM), command: `java ./src/main/java/JPM.java`
(if you want to directly run it too append `andRun` at the end).

### Bounties

* Some issues are labeled like `bounty: 50€` (show the [list](https://github.com/Osiris-Team/AutoPlug-Client/issues?q=state%3Aopen%20label%3A%22bounty%3A%2050%E2%82%AC%22)). The first accepted pull request that fully fixes/closes the issue receives the bounty.
* Your PR must include at least one test proving the fix or feature works.
* Payments are only made via PayPal.
* Your PayPal email must be visible somewhere on your public GitHub profile before the PR is merged.
* By submitting a PR for a bounty issue, you agree to these terms.
* Low-quality, incomplete, AI-spam, or copy-paste PRs may be closed without review.

### AI / LLM Generated Pull Requests

* AI/LLM-assisted code is allowed, but must be fully reviewed and understood by the person submitting the PR.
* Do not submit raw AI output without testing, cleanup, or verification.
* The PR author is fully responsible for all generated code.
* PRs generated mostly by AI must clearly state this in the description.
* AI-generated PRs require manual human review before merge.
* Massive AI-generated refactors, formatting-only changes, or unrelated rewrites will be rejected.
* PRs with hallucinated APIs, fake fixes, broken logic, missing context, or obvious generated spam may result in being blocked from future bounties.

### Code Quality Requirements

* Keep PRs focused and small. One issue/fix per PR.
* Keep changes close to the existing code structure and style.
* Reuse existing APIs, utility classes, helpers, and abstractions whenever possible.
* Integrate changes into the existing code flow instead of bypassing or replacing major parts of the system.
* Avoid unnecessary rewrites, duplicate logic, custom frameworks, or parallel implementations.
* Do not introduce new dependencies or architectural patterns unless absolutely necessary.
* Follow the existing naming conventions, file organization, and project architecture.
* Do not include unrelated changes, formatting noise, or drive-by edits.
* PR descriptions must explain:

  * what was changed
  * why it was changed
  * how it was tested
* Broken builds, failing tests, or untested changes will be rejected.
* If maintainers spend excessive time cleaning up, restructuring, or rewriting your PR, it may be closed.
* Repeated low-quality submissions may result in bounty disqualification or repository bans.

## Libraries

Note that this list may be outdated. Check the [build.gradle](/build.gradle) file for our current dependencies.
- [AutoPlug-Core](https://github.com/Osiris-Team/AutoPlug-Core) contains frequently used code accross all AutoPlug
  projects in one jar.
- [Dream-Yaml](https://github.com/Osiris-Team/Dream-Yaml) is used for handling YAML files.
- [Better-Thread](https://github.com/Osiris-Team/Better-Thread) enables display of 'live-tasks'.
- For console coloring and task display [Jansi](https://github.com/fusesource/jansi) and [Jline](https://github.com/jline/jline3) is used.
- [Zip4j](https://github.com/srikanth-lingala/zip4j) to unpack and handle jar/zips.
- Server restarts are scheduled with [Quartz](http://www.quartz-scheduler.org/).
