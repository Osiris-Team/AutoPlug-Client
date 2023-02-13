# How To: Install AutoPlug Client on a Server Host

### **Can I use AutoPlug Client with my host?**
Yes! AutoPlug Client is designed to work ready out-of-the-box and should be compatible with the majority of Minecraft server hosts.

### **How can I install AutoPlug Client on my host?**
The overall process is very simple and straightforward.
This guide will explain everything from start to finish and works whether you have an existing server or are starting fresh.

1. Go to the AutoPlug Client [installer page](https://autoplug.one/installer) and make sure these options are set:
    * Target-OS: `Linux/MacOS (or distro)`
        > *Majority of Minecraft server hosts operate off of a Linux distro of some kind due to their lightweight structure*
    * Add screen support: ***`Unless you know what you're doing, leave this unchecked`***
    * Mode: `Server-Wrapper`
    * Start on boot: `Unchecked`
    * System-Tray: `Unchecked`
2. Click `Generate Files` and download the generated `AutoPlug-Files.zip` file
3. Extract the files from `AutoPlug-Files.zip` somewhere easy for you to find
4. Using an FTP client (i.e. FileZilla) or your host's built-in file manager, upload the extracted files to the root directory of your host so that it looks similar to this:
    ```text
    ./autoplug/...
    ...
    ./AutoPlug-Client.jar
    ...
    ./server.jar
    ./server.properties
    ...
    ./start.sh
    ...
    ```
    > *If you're not sure how to upload files to your host, search their support pages on how to do so.*  
    > *Almost every single Minecraft server host should have this function available.*

5. In your host's server settings, change your server type to `Custom Server`,  `Custom Server Jar`, or something else that is similarly worded
    > *Almost all hosts should offer the option to let you use a custom server jar in your instance.*  
    > *If you're not sure how to find this setting, search your host's support pages on `How to use a custom server/jar` and follow their instructions.*

6. In the `Custom Jar`/`Jar Name`/`File Name` field, type in "`AutoPlug-Client.jar`"

7. Save your new settings

8. Start the server using your host's dashboard

9. Go through AutoPlug's first-time setup in your host's server console

10. You're done!

### **I'm having problems installing AutoPlug Client on my host!**
Please make either a Github Issue or a post in the Discord's #help channel including the host you're using and the problems you're having.