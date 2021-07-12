package com.osiris.autoplug.client.tasks.backup;

import com.jcraft.jsch.*;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.Base64;

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

    public void sftp(String rsa) throws JSchException, SftpException {
        JSch jSch = new JSch();

        //HostKey verification
        byte[] key = Base64.getDecoder().decode(rsa);
        HostKey hostKey1 = new HostKey(host, key);
        jSch.getHostKeyRepository().add(hostKey1, null);

        //Connect
        Session session = jSch.getSession(user, host, port);
        session.setPassword(password);
        session.connect();
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect();

        //Upload
        channel.put(zipFile.getPath(), path + this.zipFile.getName());

        //Disconnect
        channel.exit();
        session.disconnect();
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
