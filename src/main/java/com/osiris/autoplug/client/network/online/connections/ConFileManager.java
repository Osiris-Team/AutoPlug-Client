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

// Client code
public class ConFileManager extends DefaultConnection {
    private FileOutputStream tempFileOutputStream;

    // --- Debug enums ---
    public enum DecoderState {
        OPERATION(0),
        WAIT_CONTENT_BOOL(1),
        SAVE_FILE_CHUNKS(2),
        UPLOAD_CHUNKS(3),
        UNKNOWN(-1);

        public final int id;

        DecoderState(int id) {
            this.id = id;
        }

        public static DecoderState fromId(int id) {
            for (DecoderState s : values()) {
                if (s.id == id) return s;
            }
            return UNKNOWN;
        }
    }

    public enum Operation {
        LIST_FILE(0),
        CREATE(1),
        DELETE(2),
        RENAME(3),
        SAVE_FILE(4),
        RECEIVE_UPLOAD(5),
        COPY_CUT(6),
        ROOTS(7),
        UNKNOWN(-1);

        public final byte id;

        Operation(int id) {
            this.id = (byte) id;
        }

        public static Operation fromId(byte id) {
            for (Operation o : values()) {
                if (o.id == id) return o;
            }
            return UNKNOWN;
        }
    }

    // --- Debug fields ---
    private volatile DecoderState decoderState = DecoderState.UNKNOWN;
    private volatile Operation lastOperation = Operation.UNKNOWN;
    private volatile File debugCurrentFile = null;
    private volatile long bytesTransferred = 0;

    public ConFileManager() {
        super((byte) 5);
    }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().file_manager.asBoolean()) return false;
        super.open();

        channel.pipeline().addLast(new ReplayingDecoder<Void>() {
            int state = 0;
            File currentFile;

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> outList) {
                try {
                    if (state == 0) {
                        byte op = in.readByte();
                        lastOperation = Operation.fromId(op);

                        if (op == 0) {
                            String path = NettyUtils.readUTF(in);
                            currentFile = path.isEmpty() ? GD.WORKING_DIR : new File(path);
                            debugCurrentFile = currentFile;

                            DefaultConnection.exec.submit(() -> sendFileDetailsAndChildren(currentFile));
                            if (!currentFile.isDirectory()) {
                                state = 1;
                                decoderState = DecoderState.fromId(state);
                            }
                        }
                        else if (op == 1) {
                            String path = NettyUtils.readUTF(in);
                            boolean isDir = in.readBoolean();
                            DefaultConnection.exec.submit(() -> doCreate(path, isDir));
                        }
                        else if (op == 2) {
                            int count = in.readInt();
                            String[] paths = new String[count];
                            for (int i = 0; i < count; i++) paths[i] = NettyUtils.readUTF(in);
                            DefaultConnection.exec.submit(() -> doDelete(paths));
                        }
                        else if (op == 3) {
                            String path = NettyUtils.readUTF(in);
                            String newName = NettyUtils.readUTF(in);
                            DefaultConnection.exec.submit(() -> doRename(path, newName));
                        }
                        else if (op == 4) { // Save File
                            currentFile = new File(NettyUtils.readUTF(in));
                            debugCurrentFile = currentFile;

                            if (currentFile.getParentFile() != null) currentFile.getParentFile().mkdirs();
                            if (!currentFile.exists()) currentFile.createNewFile();
                            tempFileOutputStream = new FileOutputStream(currentFile);

                            writeBool(true);
                            state = 2;
                            decoderState = DecoderState.fromId(state);
                            bytesTransferred = 0;
                        }
                        else if (op == 5) { // Receive Upload
                            currentFile = new File(NettyUtils.readUTF(in));
                            debugCurrentFile = currentFile;

                            if (currentFile.getParentFile() != null) currentFile.getParentFile().mkdirs();
                            if (!currentFile.exists()) currentFile.createNewFile();
                            tempFileOutputStream = new FileOutputStream(currentFile);

                            writeBool(true);
                            state = 3;
                            decoderState = DecoderState.fromId(state);
                            bytesTransferred = 0;
                        }
                        else if (op == 6) {
                            int count = in.readInt();
                            boolean isCopy = in.readBoolean();
                            String targetDir = NettyUtils.readUTF(in);
                            String[] paths = new String[count];
                            boolean[] isDirs = new boolean[count];
                            for (int i = 0; i < count; i++) {
                                paths[i] = NettyUtils.readUTF(in);
                                isDirs[i] = in.readBoolean();
                            }
                            DefaultConnection.exec.submit(() -> doCopyCut(paths, isDirs, isCopy, targetDir));
                        }
                        else if (op == 7) {
                            DefaultConnection.exec.submit(ConFileManager.this::sendRoots);
                        }

                        decoderState = DecoderState.fromId(state);
                        checkpoint();
                    }
                    else if (state == 1) { // Wait for Content Boolean
                        boolean wantsContent = in.readBoolean();
                        if (wantsContent) {
                            DefaultConnection.exec.submit(() -> {
                                try {
                                    sendFileContent(currentFile);
                                } catch (Exception e) {
                                    AL.warn("Failed to send file content:", e);
                                    if (channel != null && channel.isActive()) {
                                        ByteBuf buf = channel.alloc().buffer();
                                        NettyUtils.writeUTF(buf, NettyUtils.EOF);
                                        channel.writeAndFlush(buf);
                                    }
                                }
                            });
                        }
                        state = 0;
                        decoderState = DecoderState.fromId(state);
                        checkpoint();
                    }
                    else if (state == 2) { // Save File Chunks
                        String line = NettyUtils.readUTF(in);
                        if (line.equals(NettyUtils.EOF)) {
                            tempFileOutputStream.close();
                            writeBool(true);
                            state = 0;
                            decoderState = DecoderState.fromId(state);
                        } else {
                            tempFileOutputStream.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                            bytesTransferred += line.length();
                        }
                        checkpoint();
                    }
                    else if (state == 3) { // Upload Chunks
                        String line = NettyUtils.readUTF(in);
                        if (line.equals(NettyUtils.EOF)) {
                            tempFileOutputStream.close();
                            writeBool(true);
                            state = 0;
                            decoderState = DecoderState.fromId(state);
                        } else {
                            byte[] decoded = Base64.getDecoder().decode(line.getBytes(StandardCharsets.UTF_8));
                            tempFileOutputStream.write(decoded);
                            bytesTransferred += decoded.length;
                        }
                        checkpoint();
                    }
                } catch (Exception e) {
                    AL.warn(e);
                    writeBool(false);
                    state = 0;
                    decoderState = DecoderState.fromId(state);
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
            if (files == null || files.length == 0) buf.writeInt(0);
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

    @Override
    public String toString() {
        boolean tmp = tempFileOutputStream != null;
        String file = debugCurrentFile != null ? debugCurrentFile.getAbsolutePath() : "null";

        return super.toString() +
                " " + (tmp ? "tmpStream" : "!tmpStream") +
                " state=" + decoderState +
                " op=" + lastOperation +
                " bytes=" + bytesTransferred +
                " file=" + file;
    }
}