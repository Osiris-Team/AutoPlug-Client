package me.hsgamer.mcserverupdater.util;

import java.io.*;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Utils {
    private Utils() {
        // EMPTY
    }

    public static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static boolean isFailedToCreateFile(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return true;
        }
        return !file.createNewFile();
    }

    public static String getString(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    public static void writeString(File file, String string) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(string);
        }
    }

    public static File getFile(String path) throws IOException {
        File file = new File(path);
        createFile(file);
        return file;
    }

    public static File getFile(File parent, String path) throws IOException {
        File file = new File(parent, path);
        createFile(file);
        return file;
    }

    private static void createFile(File file) throws IOException {
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Can't create file " + file.getAbsolutePath());
        }
    }

    public static InputStream getExtractedInputStream(InputStream zipStream, String fileToExtract) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipInputStream zipInputStream = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.getName().equals(fileToExtract)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = zipInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    break;
                }
            }
            return new ByteArrayInputStream(outputStream.toByteArray());
        }
    }

    public static Comparator<String> getVersionComparator() {
        return (o1, o2) -> {
            String[] split1 = o1.split("\\.");
            String[] split2 = o2.split("\\.");
            int length = Math.min(split1.length, split2.length);
            for (int i = 0; i < length; i++) {
                int i1 = Integer.parseInt(split1[i]);
                int i2 = Integer.parseInt(split2[i]);
                if (i1 != i2) {
                    return i1 - i2;
                }
            }
            return split1.length - split2.length;
        };
    }
}
