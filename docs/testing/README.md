# Dashboard fixture previews

`FinalUiPreview.java` renders the actual Swing dashboard offscreen, using synthetic profiles, worlds, status responses, icons, console logs and progress. It does not connect to public servers, download Minecraft, launch a game, authenticate an account or open a router port. Screenshots demonstrate layout rather than live gameplay. The progress screenshot uses a fixed estimated bar position and a fictional current-file message; it is not a download benchmark. The chooser uses a fixed fixture clock to keep its two-second countdown visible.

Build the project package first, then run these commands from the repository root using a JDK with `javac` (Java 9 or later):

```powershell
javac --release 9 -encoding UTF-8 -cp "target/AutoPlug-Client.jar" -d target/ui-preview docs/testing/FinalUiPreview.java
java -Djava.awt.headless=true -cp "target/ui-preview;target/AutoPlug-Client.jar" com.osiris.autoplug.client.ui.FinalUiPreview target/ui-previews/light-1200 1200 820 light
java -Djava.awt.headless=true -cp "target/ui-preview;target/AutoPlug-Client.jar" com.osiris.autoplug.client.ui.FinalUiPreview target/ui-previews/dark-1200 1200 820 dark
java -Djava.awt.headless=true -cp "target/ui-preview;target/AutoPlug-Client.jar" com.osiris.autoplug.client.ui.FinalUiPreview target/ui-previews/light-950 950 620 light
java -Djava.awt.headless=true -cp "target/ui-preview;target/AutoPlug-Client.jar" com.osiris.autoplug.client.ui.FinalUiPreview target/ui-previews/dark-950 950 620 dark
```

On Linux or macOS, replace the semicolon in each runtime classpath with a colon. No test classes or additional dependencies are needed with the packaged JAR. Theme colors and fonts can vary slightly by platform.

Each output directory contains `server-browser.png`, `worlds.png`, `profiles.png`, `settings.png`, `managed-console.png`, `launch-chooser.png` and `overall-progress.png`. Compact runs also capture the scrolled fullscreen/sharing settings as `settings-defaults.png`. The chooser is a standalone component preview at 610 × 350 or 460 × 350; other screenshots use the requested dashboard size.

The helper uses an empty temporary directory for favorites and removes that exact directory afterward. Profile/world paths, the favicon and all `.invalid` addresses are fixture data. Review generated images before copying them into `docs/images/native-launcher`.
