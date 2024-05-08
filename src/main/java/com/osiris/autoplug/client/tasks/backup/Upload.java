/*
 * Copyright (c) 2021 Osiris-Team.
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
import java.io.PrintWriter;

/**
 * @author kastenklicker
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

    public void sftp(String rsaPathString) throws JSchException, SftpException {
        JSch jSch = new JSch();
        
        // Try to use RSA key
        if (rsaPathString != null && !rsaPathString.equals("")) {
            jSch.addIdentity(rsaPathString);
        }
    
        Session session = null;
        ChannelSftp channel = null;
    
        try {
            session = jSch.getSession(user, host, port);
            if (password != null && !password.equals("")) {
                session.setPassword(password);
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
    
            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect();
    
            channel.put(zipFile.getPath(), path + zipFile.getName());
        } finally {
            if (channel != null) {
                channel.exit();
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }
    
    public void ftps() throws Exception {

        FileInputStream zipFileStream = new FileInputStream(zipFile);

        FTPSClient ftps = new FTPSClient();
        ftps.setConnectTimeout(5000);

        //Connect
        ftps.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
        ftps.connect(host, port);
        ftps.execPBSZ(0);
        ftps.execPROT("P");
        int reply = ftps.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftps.disconnect();
            throw new Exception("Exception in connecting to FTPS Server.");
        }
        ftps.login(user, password);
        ftps.setFileType(FTP.BINARY_FILE_TYPE);
        ftps.enterLocalPassiveMode();

        //Upload
        if (!ftps.storeFile(path + zipFile.getName(), zipFileStream))
            throw new Exception("Exception in uploading to FTPS Server.");
        ftps.logout();
        ftps.disconnect();
    }
}
