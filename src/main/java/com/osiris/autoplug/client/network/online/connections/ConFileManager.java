/*
 * Copyright (c) 2021-2023 Osiris-Team.
 * All rights reserved.
 *
 * This software is copyrighted work, licensed under the terms
 * of the MIT-License. Consult the "LICENSE" file for details.
 */

package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.Main;
import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.autoplug.client.utils.io.UFDataIn;
import com.osiris.autoplug.client.utils.io.UFDataOut;
import com.osiris.jlib.logger.AL;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;


public class ConFileManager extends DefaultConnection {

    @Nullable
    private UFDataOut dos;
    private UFDataIn dis;

    public ConFileManager() {
        super((byte) 5);  // Each connection has its own auth_id.
    }

    @Override
    public boolean open() throws Exception {
        if (new WebConfig().file_manager.asBoolean()) {
            super.open();
            getSocket().setSoTimeout(0);
            dos = new UFDataOut(out);
            dis = new UFDataIn(in);

            setAndStartAsync(() -> {
                try {
                    while (true) {
                        byte requestType = dis.readByte(); // Blocks indefinitely
                        getSocket().setSoTimeout(60000);
                        if (requestType == 0) {
                            doProtocolForSendingFileDetails();
                        } else if (requestType == 1) {
                            doProtocolForCreatingNewFile();
                        } else if (requestType == 2) {
                            doProtocolForDeletingFile();
                        } else if (requestType == 3) {
                            doProtocolForRenamingFile();
                        } else if (requestType == 4) {
                            doProtocolForSavingFile();
                        } else if (requestType == 5) {
                            doProtocolForReceivingUploadedFile();
                        } else if (requestType == 6) {
                            doProtocolForCopyOrCutFiles();
                        } else if (requestType == 7) {
                            doProtocolForSendingRoots();
                        } else {
                            AL.warn("Unknown file operation / Unknown request type (" + requestType + ").");
                        }
                        getSocket().setSoTimeout(0);
                    }
                } catch (Exception e) {
                    if (!Main.CON.isUserActive.get()) return; // Ignore after logout
                    throw e;
                }
            });
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' connected.");
            return true;
        } else {
            AL.debug(this.getClass(), "Connection '" + this.getClass().getSimpleName() + "' not connected, because not enabled in the web-config.");
            return false;
        }
    }

    private void doProtocolForSendingRoots() throws IOException {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            dos.write(0);
        } else {
            dos.writeInt(roots.length);
            for (File f :
                    roots) {
                dos.writeLine(f.getAbsolutePath()); // For example "C:\" or "D:\" etc. on Windows
            }
        }
    }

    private void doProtocolForCopyOrCutFiles() throws IOException {
        int filesCount = dis.readInt();
        boolean isCopy = dis.readBoolean();
        File targetDir = new File(dis.readLine());
        try {
            for (int i = 0; i < filesCount; i++) {
                File f = new File(dis.readLine());
                boolean isDir = dis.readBoolean();
                if (isCopy) // Make regular "copy" operation
                    if (isDir)
                        FileUtils.copyDirectory(f, new File(targetDir + "/" + f.getName()));
                    else
                        Files.copy(f.toPath(), new File(targetDir + "/" + f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                else {
                    // Make "cut" operation, which means that the previous file gets deleted after copying to its new destination
                    if (isDir) {
                        FileUtils.copyDirectory(f, new File(targetDir + "/" + f.getName()));
                        FileUtils.deleteDirectory(f);
                    } else {
                        Files.copy(f.toPath(), new File(targetDir + "/" + f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        f.delete();
                    }
                }
            }
        } catch (Exception e) {
            AL.warn(e);
            dos.writeBoolean(false);
            dos.writeLine("Critical error while copying/cutting a file! Check your servers log for further details: " + e.getMessage());
        }
        dos.writeBoolean(true);
    }

    private void doProtocolForReceivingUploadedFile() throws IOException {
        File file = new File(dis.readLine());
        if (!file.exists()) file.createNewFile();
        try (BufferedWriter fw = new BufferedWriter(new FileWriter(file))) {
            String line;
            while ((line = dis.readLine()) != null && !line.equals("\u001a")) {
                fw.write(line + "\n");
                fw.flush();
            }
        } catch (Exception e) {
            AL.warn(e);
            dos.writeBoolean(false);
            dos.writeLine("Critical error while saving uploaded file! Check your servers log for further details: " + e.getMessage());
        }
        dos.writeBoolean(true);
    }

    private void doProtocolForSavingFile() throws IOException {
        File file = new File(dis.readLine());
        try (BufferedWriter fw = new BufferedWriter(new FileWriter(file))) {
            String line;
            while ((line = dis.readLine()) != null && !line.equals("\u001a")) {
                fw.write(line + "\n");
                fw.flush();
            }
        } catch (Exception e) {
            AL.warn(e);
            dos.writeBoolean(false);
            dos.writeLine("Critical error while saving a file! Check your servers log for further details: " + e.getMessage());
        }
        dos.writeBoolean(true);
    }

    private void doProtocolForRenamingFile() throws IOException {
        File file = new File(dis.readLine());
        File renamedFile = new File(file.getParentFile() + "/" + dis.readLine());
        if (!file.renameTo(renamedFile)) { // If this fails try the hardcore way
            if (!renamedFile.exists()) renamedFile.createNewFile();
            Files.copy(file.toPath(), renamedFile.toPath());
            file.delete();
        }
    }

    private void doProtocolForDeletingFile() throws IOException {
        try {
            int filesCount = dis.readInt();
            for (int i = 0; i < filesCount; i++) {
                FileUtils.forceDelete(new File(dis.readLine()));
                dos.writeBoolean(true);
            }
        } catch (Exception e) {
            AL.warn(e);
            dos.writeBoolean(false);
            dos.writeLine("Critical error during file delete! Check your servers log for further details: " + e.getMessage());
        }
    }

    private void doProtocolForCreatingNewFile() throws IOException {
        File f = new File(dis.readLine());
        boolean isDir = dis.readBoolean();
        try {
            if (f.exists()) {
                dos.writeBoolean(false);
                dos.writeLine("File/Directory already exists! Nothing changed.");
                return;
            }
            if (isDir) {
                if (f.mkdirs())
                    dos.writeBoolean(true);
                else {
                    dos.writeBoolean(false);
                    dos.writeLine("Couldn't create directory '" + f.getName() + "'!");
                }
            } else { // File
                if (f.getParentFile() != null) f.getParentFile().mkdirs(); // Create non-existent parent dirs if needed
                if (f.createNewFile())
                    dos.writeBoolean(true);
                else {
                    dos.writeBoolean(false);
                    dos.writeLine("Couldn't create file '" + f.getName() + "'!");
                }
            }
        } catch (Exception e) {
            AL.warn(e);
            dos.writeBoolean(false);
            dos.writeLine(e.getMessage());
        }
    }

    private void doProtocolForSendingFileDetails() throws IOException {
        String filePath = null;
        File requestedFile = null;
        filePath = dis.readLine(); // Wait until we receive the files path
        if (filePath.isEmpty()) requestedFile = GD.WORKING_DIR;
        else requestedFile = new File(filePath);
        sendFileDetails(requestedFile);
        if (requestedFile.isDirectory()) {
            File[] files = requestedFile.listFiles();
            if (files == null) dos.writeInt(0);
            else {
                dos.writeInt(files.length);

                for (File f :
                        files) { // Send directories first and then files
                    if (f.isDirectory())
                        sendFileDetails(f);
                }
                for (File f :
                        files) {
                    if (!f.isDirectory())
                        sendFileDetails(f);
                }
            }
        } else { // Is not a dir
            if (dis.readBoolean()) // Web checks the files size and responds with true if it wants its content
                sendFileContent(requestedFile);
        }
    }

    private void sendFileContent(File file) throws IOException {
        //System.out.println("Sending file "+file);
        dos.writeFile(file);
        //ConMain.CON_FILE_CONTENT.sendFile(file);


             /*
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(getOut()));
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line = null;
                while ((line = br.readLine()) != null) {
                    writer.write(line + "\n");
                    writer.flush();
                }
                writer.write("\u001a\n");// EOF
                writer.flush();
            }
            */
        //System.out.println("Sent file!");
    }

    private void sendFileDetails(File file) throws IOException {
        dos.writeLine(file.getAbsolutePath());
        dos.writeBoolean(file.isDirectory());
        long length = file.length(); // In bytes
        dos.writeLong(length);
        if (length < 1000) // Smaller than 1kb
            dos.writeLine(length + "B");
        else if (length < 1000000) // Smaller than 1mb
            dos.writeLine(length / 1000 + "kB");
        else if (length < 1000000000) // Smaller than 1 gb
            dos.writeLine(length / 1000000 + "MB");
        else // Bigger than 1 gb
            dos.writeLine(length / 1000000000 + "GB");
        dos.writeLine(file.getName());
        dos.writeLong(file.lastModified());
        dos.writeBoolean(file.isHidden());
    }

    private void sendParentDirDetails(File file) throws IOException {
        dos.writeLine(file.getAbsolutePath());
        dos.writeBoolean(file.isDirectory());
        long length = file.length(); // In bytes
        dos.writeLong(length);
        if (length < 1000) // Smaller than 1kb
            dos.writeLine(length + "B");
        else if (length < 1000000) // Smaller than 1mb
            dos.writeLine(length / 1000 + "kB");
        else if (length < 1000000000) // Smaller than 1 gb
            dos.writeLine(length / 1000000 + "MB");
        else // Bigger than 1 gb
            dos.writeLine(length / 1000000000 + "GB");
        dos.writeLine("...");
        dos.writeLong(file.lastModified());
        dos.writeBoolean(file.isHidden());
    }
}
