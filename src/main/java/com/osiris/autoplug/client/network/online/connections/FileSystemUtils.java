package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.utils.io.UFDataOut;

import java.io.File;
import java.io.IOException;

public class FileSystemUtils {

    public static void sendRoots(UFDataOut dos) throws IOException {
        File[] roots = File.listRoots();
        if (roots == null || roots.length == 0) {
            dos.writeInt(0);
        } else {
            dos.writeInt(roots.length);
            for (File f : roots) {
                dos.writeLine(f.getAbsolutePath()); // For example "C:\" or "D:\" etc. on Windows
            }
        }
    }

    public static void sendFileDetails(UFDataOut dos, File file) throws IOException {
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

    public static void sendParentDirDetails(UFDataOut dos, File file) throws IOException {
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