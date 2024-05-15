/*
 * Copyright (c) 2024 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.tasks.backup;

import com.jcraft.jsch.*;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Handles file uploads via SFTP and FTPS.
 *
 * Usage:
 * - To upload via SFTP, call the {@link #sftp(String)} method with the RSA key path.
 * - To upload via FTPS, call the {@link #ftps()} method.
 *
 * Ensure that the host, port, user, password, path, and zipFile are properly set via the constructor.
 *
 * Note: Password handling in this code is basic and should be improved for production use.
 *
 * Author: kastenklicker
 */
public class Upload {
    private final String host, user, password, path;
    private final int port;
    private final File zipFile;

    public Upload(String host, int port, String user, String password, String path, File zipFile) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        if (!path.endsWith("/")) path += "/";
        this.path = path;
        this.zipFile = zipFile;
    }

    public void sftp(String rsaKeyPath) throws JSchException, SftpException, IOException {
        JSch jsch = new JSch();

        // Load the private key from the file
        jsch.addIdentity(rsaKeyPath);

        // HostKey verification
        // This is a basic implementation. Improve as needed.
        //jsch.setKnownHosts("/path/to/known_hosts");

        // Connect
        Session session = jsch.getSession(user, host, port);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setPassword(password);
        session.connect();
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();

        try {
            // Upload
            channel.put(zipFile.getPath(), path + zipFile.getName());
        } catch (SftpException e) {
            throw e;
        } finally {
            // Disconnect
            channel.disconnect();
            session.disconnect();
        }
    }

    public void ftps() throws IOException {
        FTPSClient ftps = new FTPSClient();
        ftps.setConnectTimeout(5000);

        // Connect
        ftps.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
        ftps.connect(host, port);
        ftps.execPBSZ(0);
        ftps.execPROT("P");
        int reply = ftps.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftps.disconnect();
            throw new IOException("Exception in connecting to FTPS Server.");
        }
        ftps.login(user, password);
        ftps.setFileType(FTP.BINARY_FILE_TYPE);
        ftps.enterLocalPassiveMode();

        try (FileInputStream zipFileStream = new FileInputStream(zipFile)) {
            // Upload
            if (!ftps.storeFile(path + zipFile.getName(), zipFileStream)) {
                throw new IOException("Exception in uploading to FTPS Server.");
            }

        } catch (IOException e) {
            throw e;
        } finally {
            ftps.logout();
            ftps.disconnect();
        }
    }
}
