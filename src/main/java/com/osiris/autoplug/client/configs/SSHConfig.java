/*
 * Copyright (c) 2021-2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import java.io.IOException;

import org.jline.utils.OSUtils;

import com.osiris.autoplug.client.tasks.SSHManager;
import com.osiris.dyml.Yaml;
import com.osiris.dyml.YamlSection;
import com.osiris.dyml.exceptions.DuplicateKeyException;
import com.osiris.dyml.exceptions.IllegalKeyException;
import com.osiris.dyml.exceptions.IllegalListException;
import com.osiris.dyml.exceptions.NotLoadedException;
import com.osiris.dyml.exceptions.YamlReaderException;
import com.osiris.dyml.exceptions.YamlWriterException;
import com.osiris.jlib.logger.AL;

public class SSHConfig extends MyYaml {
    
    public YamlSection enabled;
    public YamlSection port;
    public YamlSection auth_method;
    public YamlSection allowed_keys_path;
    public YamlSection server_private_key;
    public YamlSection username;
    public YamlSection password;

    public SSHConfig() throws IOException, DuplicateKeyException, YamlReaderException, IllegalListException, NotLoadedException, IllegalKeyException, YamlWriterException {
        super(System.getProperty("user.dir") + "/autoplug/ssh.yml");

        addSingletonConfigFileEventListener(e -> {
            try {
                SSHManager.stop();
                SSHManager.start(false);
            } catch (Exception ex) {
                AL.warn("Failed to start SSHManager!", ex);
            }
        });
        
        lockFile();
        load();
        String name = getFileNameWithoutExt();
        put(name).setComments(
                "#######################################################################################################################",
                "    ___       __       ___  __",
                "   / _ |__ __/ /____  / _ \\/ /_ _____ _",
                "  / __ / // / __/ _ \\/ ___/ / // / _ `/",
                " /_/ |_\\_,_/\\__/\\___/_/  /_/\\_,_/\\_, /",
                "                                /___/ SSH-Config",
                "Thank you for using AutoPlug!",
                "You can find detailed installation instructions here: https://autoplug.one/installer",
                "If there are any questions or you just want chat, join our Discord: https://discord.gg/GGNmtCC",
                " ",
                "#######################################################################################################################",
                "Uses apache sshd 2.13.0 internally, make sure it doesn't contain vulnerabilities before using here: https://mvnrepository.com/artifact/org.apache.sshd/sshd-core");

        enabled = put(name, "enabled").setDefValues("false")
            .setComments(
                "Enables a SSH console for remote access to AutoPlug.",
                "You can connect to it via any SSH client like Putty, Terminus, a custom script, or the built-in SSH client in Windows, Linux distros, MacOS, etc.");

        port = put(name, "port").setDefValues("22")
            .setComments(
                "The port the SSH console listens on.",
                "The default port is 22. Change it if you have a different port setup in your network, are hosting one or more other services on the same port, or are otherwise wanting to use the SSH service on a different port.",
                "Notice: You will likely need to open this port in your firewall settings to allow incoming connections, as well as port-forward it in your router settings if you are hosting the server on a local network.");

        auth_method = put(name, "auth-method").setDefValues("key-only")
            .setComments(
                "Select the authentication method for the SSH console.",
                "Available options: 'user-pass-only', 'key-only', 'user-pass-key'.",
                "user-pass-only: Requires a username and password to connect. Requires the username and password fields below.",
                "key-only: Requires a public key to connect. Requires the allowed-keys-path field below.",
                "user-pass-key: Requires either a username and password or a public key to connect. Requires all fields below.");

        allowed_keys_path = put(name, "allowed-keys-path").setDefValues("./autoplug/allowed_ssh_keys.txt")
            .setComments(
                "The .txt file containing the public keys that are allowed to connect to the SSH console.",
                "The file must contain one public key per line.",
                "The file must be in the OpenSSH format.",
                "Exaple:",
                "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDf6... user@host",
                "Create keys with 'ssh-keygen -t rsa -b 4096' and add them to the file.",
                "The generated file can be found in the .ssh directory of the user that created the key, unless a different path was specified.",
                "The generated file will be a .pub file, which contains the public key.",
                "Example connection command: `ssh -i /path/to/private/key username@server-ip-address`");
        

        String sshPath = OSUtils.IS_WINDOWS ? "%USERPROFILE%\\.ssh\\id_rsa" : "~/.ssh/id_rsa";
        server_private_key = put("server-private-key")
            .setComments(
                "The private key used by the server to authenticate itself to the SSH console.",
                "The file must be in the OpenSSH format.",
                "Create keys with 'ssh-keygen -t rsa -b 4096' and add them to the file.",
                "The generated file can be found in the .ssh directory of the user that created the key, unless a different path was specified.",
                "The generated file will be a file with no extension, which contains the private key.",
                "In the same directory as the private key, there will also need to be a file with the same name and a .pub extension, which contains the public key.",
                "NOTICE: The .ssh directory is not present by default, and must be created via the usage of the 'ssh-keygen' command.",
                "Example:",
                "server-private-key: " + sshPath);

        username = put(name, "username").setDefValues("autoplug")
            .setComments(
                "The username required to connect to the SSH console.",
                "This username must be unique and not used by any other SSH-based services on the host machine.",
                "This will be the username used to connect to the SSH console (`ssh username@host`).");

        password = put(name, "password")
            .setComments(
                "WARNING: The password is not encrypted and can be seen in plain text in this file.",
                "For this reason, it is recommended to use a public/private keypair instead of a password for authentication.",
                "Example:",
                "password: MyT0pSecretP@ssw0rd");

        save();
        unlockFile();
    }

    @Override
    public Yaml validateValues() {
        return this;
    }
}
