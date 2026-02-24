package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.network.online.NettyUtils;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;

public class ConFileManager extends DefaultConnection {
    private FileOutputStream tempFileOutputStream;

    public ConFileManager() {
        super((byte) 5);
    }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().file_manager.asBoolean()) return false;
        super.open();

        channel.pipeline().addLast(new ReplayingDecoder<Void>() {
            int state = 0;
            // 0 = Opcode, 1 = FileDetails WantsContent, 2 = SaveFile Chunk, 3 = UploadFile Chunk
            File currentFile;

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> outList) {
                try {
                    if (state == 0) {
                        byte op = in.readByte();
                        if (op == 0) { // Get File Details
                            String path = NettyUtils.readUTF(in);
                            currentFile = path.isEmpty() ? GD.WORKING_DIR : new File(path);
                            Executors.newSingleThreadExecutor().submit(() -> sendFileDetailsAndChildren(currentFile));
                            if (!currentFile.isDirectory()) {
                                state = 1;
                            }
                        }
                        else if (op == 1) { // Create
                            String path = NettyUtils.readUTF(in);
                            boolean isDir = in.readBoolean();
                            Executors.newSingleThreadExecutor().submit(() -> doCreate(path, isDir));
                        }
                        else if (op == 2) { // Delete
                            int count = in.readInt();
                            String[] paths = new String[count];
                            for (int i = 0; i < count; i++) paths[i] = NettyUtils.readUTF(in);
                            Executors.newSingleThreadExecutor().submit(() -> doDelete(paths));
                        }
                        else if (op == 3) { // Rename
                            String path = NettyUtils.readUTF(in);
                            String newName = NettyUtils.readUTF(in);
                            Executors.newSingleThreadExecutor().submit(() -> doRename(path, newName));
                        }
                        else if (op == 4) { // Save File
                            currentFile = new File(NettyUtils.readUTF(in));
                            tempFileOutputStream = new FileOutputStream(currentFile);
                            state = 2;
                        }
                        else if (op == 5) { // Receive Upload
                            currentFile = new File(NettyUtils.readUTF(in));
                            if (!currentFile.exists()) currentFile.createNewFile();
                            tempFileOutputStream = new FileOutputStream(currentFile);
                            state = 3;
                        }
                        else if (op == 6) { // Copy/Cut
                            int count = in.readInt();
                            boolean isCopy = in.readBoolean();
                            String targetDir = NettyUtils.readUTF(in);
                            String[] paths = new String[count];
                            boolean[] isDirs = new boolean[count];
                            for (int i = 0; i < count; i++) {
                                paths[i] = NettyUtils.readUTF(in);
                                isDirs[i] = in.readBoolean();
                            }
                            Executors.newSingleThreadExecutor().submit(() -> doCopyCut(paths, isDirs, isCopy, targetDir));
                        }
                        else if (op == 7) { // Roots
                            Executors.newSingleThreadExecutor().submit(ConFileManager.this::sendRoots);
                        }
                        checkpoint();
                    }
                    else if (state == 1) { // Wait for Content Boolean
                        boolean wantsContent = in.readBoolean();
                        if (wantsContent) {
                            Executors.newSingleThreadExecutor().submit(() -> {
                                try {
                                    sendFileContent(currentFile);
                                } catch (IOException e) { AL.warn(e); }
                            });
                        }
                        state = 0;
                        checkpoint();
                    }
                    else if (state == 2) { // Save File Chunks
                        String line = NettyUtils.readUTF(in);
                        if (line.equals(NettyUtils.EOF)) {
                            tempFileOutputStream.close();
                            writeBool(true);
                            state = 0;
                        } else {
                            tempFileOutputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                        }
                        checkpoint();
                    }
                    else if (state == 3) { // Upload Chunks
                        String line = NettyUtils.readUTF(in);
                        if (line.equals(NettyUtils.EOF)) {
                            tempFileOutputStream.close();
                            writeBool(true);
                            state = 0;
                        } else {
                            byte[] decoded = Base64.getDecoder().decode(line.getBytes(StandardCharsets.UTF_8));
                            tempFileOutputStream.write(decoded);
                        }
                        checkpoint();
                    }
                } catch (Exception e) {
                    AL.warn(e);
                    writeBool(false);
                    state = 0;
                }
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) {
                try { if (tempFileOutputStream != null) tempFileOutputStream.close(); } catch (Exception ignored) {}
            }
        });
        return true;
    }

    private void writeBool(boolean val) {
        if (channel != null && channel.isActive()) {
            ByteBuf out = channel.alloc().buffer(1);
            out.writeBoolean(val);
            channel.writeAndFlush(out);
        }
    }

    // Disk I/O Methods mapping exactly to original functionality:
    private void sendRoots() {
        File[] roots = File.listRoots();
        ByteBuf buf = channel.alloc().buffer();
        if (roots == null || roots.length == 0) buf.writeInt(0);
        else {
            buf.writeInt(roots.length);
            for (File f : roots) NettyUtils.writeUTF(buf, f.getAbsolutePath());
        }
        channel.writeAndFlush(buf);
    }

    private void sendFileDetailsAndChildren(File file) {
        ByteBuf buf = channel.alloc().buffer();
        encodeDetails(buf, file);
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) buf.writeInt(0);
            else {
                buf.writeInt(files.length);
                for (File f : files) if (f.isDirectory()) encodeDetails(buf, f);
                for (File f : files) if (!f.isDirectory()) encodeDetails(buf, f);
            }
        }
        channel.writeAndFlush(buf);
    }

    private void encodeDetails(ByteBuf buf, File file) {
        NettyUtils.writeUTF(buf, file.getAbsolutePath());
        buf.writeBoolean(file.isDirectory());
        long length = file.length();
        buf.writeLong(length);
        if (length < 1000) NettyUtils.writeUTF(buf, length + "B");
        else if (length < 1000000) NettyUtils.writeUTF(buf, length / 1000 + "kB");
        else if (length < 1000000000) NettyUtils.writeUTF(buf, length / 1000000 + "MB");
        else NettyUtils.writeUTF(buf, length / 1000000000 + "GB");
        NettyUtils.writeUTF(buf, file.getName());
        buf.writeLong(file.lastModified());
        buf.writeBoolean(file.isHidden());
    }

    private void sendFileContent(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file)) {
            NettyUtils.writeStream(channel, in);
        }
    }

    private void doCreate(String path, boolean isDir) {
        try {
            File f = new File(path);
            if (f.exists()) { writeBool(false); return; }
            if (isDir) writeBool(f.mkdirs());
            else {
                if (f.getParentFile() != null) f.getParentFile().mkdirs();
                writeBool(f.createNewFile());
            }
        } catch (Exception e) { writeBool(false); }
    }

    private void doDelete(String[] paths) {
        try {
            for (String path : paths) {
                FileUtils.forceDelete(new File(path));
                writeBool(true);
            }
        } catch (Exception e) { writeBool(false); }
    }

    private void doRename(String path, String newName) {
        try {
            File f = new File(path);
            File renamed = new File(f.getParentFile() + "/" + newName);
            if (!f.renameTo(renamed)) {
                if (!renamed.exists()) renamed.createNewFile();
                Files.copy(f.toPath(), renamed.toPath(), StandardCopyOption.REPLACE_EXISTING);
                f.delete();
            }
        } catch (Exception e) { AL.warn(e); }
    }

    private void doCopyCut(String[] paths, boolean[] isDirs, boolean isCopy, String targetDir) {
        try {
            for (int i = 0; i < paths.length; i++) {
                File f = new File(paths[i]);
                File dest = new File(targetDir + "/" + f.getName());
                if (isCopy) {
                    if (isDirs[i]) FileUtils.copyDirectory(f, dest);
                    else Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    if (isDirs[i]) { FileUtils.copyDirectory(f, dest); FileUtils.deleteDirectory(f); }
                    else { Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING); f.delete(); }
                }
            }
            writeBool(true);
        } catch (Exception e) { writeBool(false); }
    }
}