/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.configs;

import java.io.IOException;

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

    public static SSHManager sshManager;
    
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
                SSHManager sshManager = SSHManager.getInstance(this);
                sshManager.stop();
                sshManager.start();
            } catch (IOException ex) {
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
                "Uses apache sshd 2.13.0 internally, make sure it doesn't contain vulnerabilities before using here: https://mvnrepository.com/artifact/org.apache.sshd/sshd-core",
                "Note: Changes to this file probably require you to enter '.con reload' to have affect.");

        enabled = put(name, "enabled").setDefValues("false")
            .setComments(
                "Enables a SSH console for remote access to AutoPlug.",
                "You can connect to it via any SSH client like Putty, a custom script, or the built-in SSH client in Windows, Linux distros, MacOS, etc.");

        port = put(name, "port").setDefValues("22")
            .setComments(
                "The port the SSH console listens on.",
                "The default port is 22. Change it if you have a different port setup in your network, are hosting one or more other SSH-based services on the same port, or are otherwise using the default port for something else.");

        auth_method = put(name, "auth-method").setDefValues("key-only")
            .setComments(
                "Select the authentication method for the SSH console.",
                "Available options: 'user-pass-only', 'key-only', 'user-pass-key'.",
                "user-pass-only: Requires a username and password to connect. Requires the username and password fields below.",
                "key-only: Requires a public key to connect. Requires the allowed-keys-path field below.",
                "user-pass-key: Requires either a username and password or a public key to connect. Requires all fields below.");

        allowed_keys_path = put(name, "allowed-keys-path")
            .setComments(
                "The .txt file containing the public keys that are allowed to connect to the SSH console.",
                "The file must contain one public key per line.",
                "The file must be in the OpenSSH format.",
                "Exaple:",
                "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDf6... user@host",
                "Create keys with 'ssh-keygen -t rsa -b 4096' and add them to the file.",
                "The genereated file can be found in the .ssh directory of the user that created the key, unless a different path was specified.",
                "The generated file will be a ____.pub file, which contains the public key.");
        
        server_private_key = put(name, "server-private-key").setDefValues("./autoplug/key.pem")
            .setComments(
                "The private key used by the server to authenticate itself to the SSH console.",
                "The file must be in the OpenSSH format.",
                "Create keys with 'ssh-keygen -t rsa -b 4096' and add them to the file.",
                "The genereated file can be found in the .ssh directory of the user that created the key, unless a different path was specified.",
                "The generated file will be a file with no extension, which contains the private key.");

        username = put(name, "username").setDefValues("autoplug")
            .setComments("The username required to connect to the SSH console.");

        password = put(name, "password")
            .setComments(
                "The password is not encrypted and can be seen in plain text in this file.",
                "It is recommended to change it to a secure password.");

        save();
        unlockFile();
    }

    @Override
    public Yaml validateValues() {
        // Validate 'enabled' field
        String enabledValue = enabled.asString();
        if (!enabledValue.equals("true") && !enabledValue.equals("false")) {
            String correction = enabled.getDefValue().asString();
            AL.warn("Config error -> " + enabled.getKeys() + " must be: true or false. Applied default!");
            enabled.setValues(correction);
        }

        // Validate 'port' field
        try {
            int portValue = Integer.parseInt(port.asString());
            if (portValue < 1 || portValue > 65535) {
                int correction = Integer.parseInt(port.getDefValue().asString());
                AL.warn("Config error -> " + port.getKeys() + " must be between 1 and 65535. Applied default!");
                port.setValues(String.valueOf(correction));
            }
        } catch (NumberFormatException e) {
            int correction = Integer.parseInt(port.getDefValue().asString());
            AL.warn("Config error -> " + port.getKeys() + " must be a valid integer. Applied default!");
            port.setValues(String.valueOf(correction));
        }

        // Validate 'auth_method' field
        String authMethodValue = auth_method.asString();
        if (!authMethodValue.equals("user-pass-only") && !authMethodValue.equals("key-only") && !authMethodValue.equals("user-pass-key")) {
            String correction = auth_method.getDefValue().asString();
            AL.warn("Config error -> " + auth_method.getKeys() + " must be: user-pass-only, key-only, or user-pass-key. Applied default!");
            auth_method.setValues(correction);
        }

        return this;
    }
}
