package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.network.online.NettyUtils;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Client side of the file-manager connection: answers requests sent by the server.
 * Wire format: see the protocol notes in the server class.
 */
public class ConFileManager extends DefaultConnection {
    static final byte OP_GET = 0, OP_CREATE = 1, OP_DELETE = 2, OP_RENAME = 3,
            OP_WRITE = 4, OP_COPY_CUT = 6, OP_ROOTS = 7;
    static final byte STATUS_OK = 0, STATUS_ERR = 1;
    static final int CHUNK_SIZE = 8192;

    private enum Phase { OP, WAIT_CONTENT_FLAG, RECEIVE_WRITE }

    private interface Action { void run() throws Exception; }
    private interface Builder { void build(ByteBuf b) throws Exception; }

    public ConFileManager() {
        super((byte) 5);
    }

    @Override
    public boolean open() throws Exception {
        if (!new WebConfig().file_manager.asBoolean()) return false;
        super.open();
        channel.pipeline().addLast(new Decoder(channel));
        return true;
    }

    // ------------------------------------------------------------------
    // Decoder: one per channel. All protocol state lives here and is only
    // touched on the event-loop thread. Executor tasks only use `ch`.
    // ------------------------------------------------------------------
    private final class Decoder extends ReplayingDecoder<Void> {
        private final Channel ch;
        private Phase phase = Phase.OP;
        private File pendingFile;
        private WriteJob writeJob;

        Decoder(Channel ch) {
            this.ch = ch;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            switch (phase) {
                case OP:
                    handleOp(in);
                    break;
                case WAIT_CONTENT_FLAG:
                    handleContentFlag(in);
                    break;
                case RECEIVE_WRITE:
                    handleWriteChunk(in);
                    break;
            }
        }

        // Every branch reads all its arguments before any side effect, then checkpoints.
        // ReplayingDecoder re-runs from the last checkpoint if data is incomplete.
        private void handleOp(ByteBuf in) throws Exception {
            byte op = in.readByte();
            switch (op) {
                case OP_GET:
                    onGet(NettyUtils.readUTF(in));
                    break;
                case OP_CREATE: {
                    String path = NettyUtils.readUTF(in);
                    boolean isDir = in.readBoolean();
                    runStatus(() -> create(path, isDir));
                    break;
                }
                case OP_DELETE: {
                    String[] paths = readStrings(in, in.readInt());
                    runStatus(() -> delete(paths));
                    break;
                }
                case OP_RENAME: {
                    String path = NettyUtils.readUTF(in);
                    String newName = NettyUtils.readUTF(in);
                    runStatus(() -> rename(path, newName));
                    break;
                }
                case OP_WRITE:
                    beginWrite(NettyUtils.readUTF(in));
                    break;
                case OP_COPY_CUT: {
                    int count = in.readInt();
                    boolean isCopy = in.readBoolean();
                    String targetDir = NettyUtils.readUTF(in);
                    String[] paths = new String[count];
                    boolean[] isDirs = new boolean[count];
                    for (int i = 0; i < count; i++) {
                        paths[i] = NettyUtils.readUTF(in);
                        isDirs[i] = in.readBoolean();
                    }
                    runStatus(() -> copyOrCut(paths, isDirs, isCopy, targetDir));
                    break;
                }
                case OP_ROOTS: {
                    Channel c = ch;
                    exec.submit(() -> replyWith(c, ConFileManager::writeRoots));
                    break;
                }
                default:
                    throw new IOException("Unknown opcode: " + op);
            }
            checkpoint();
        }

        private void onGet(String path) {
            File f = path.isEmpty() ? GD.WORKING_DIR : new File(path);
            if (!f.exists()) {
                replyErr(ch, "Not found: " + path);
            } else if (f.isDirectory()) {
                Channel c = ch;
                exec.submit(() -> replyWith(c, reply -> writeDirectory(reply, f)));
            } else if (!f.isFile() || !f.canRead()) {
                replyErr(ch, "Cannot read: " + path);
            } else {
                // Built here, not in the executor, so the phase change and the reply stay in step.
                ByteBuf reply = ch.alloc().buffer();
                try {
                    reply.writeByte(STATUS_OK);
                    encodeDetails(reply, f);
                } catch (Throwable t) {
                    reply.release();
                    replyErr(ch, messageOf(t));
                    return;
                }
                ch.writeAndFlush(reply);
                pendingFile = f;
                phase = Phase.WAIT_CONTENT_FLAG;
            }
        }

        private void handleContentFlag(ByteBuf in) {
            boolean wantsContent = in.readBoolean();
            File f = pendingFile;
            pendingFile = null;
            phase = Phase.OP;
            checkpoint();
            if (wantsContent) {
                exec.submit(() -> streamFile(f));
            }
        }

        private void streamFile(File f) {
            try (InputStream in = new FileInputStream(f)) {
                writeChunks(ch, in);
            } catch (Exception e) {
                AL.warn("File transfer failed, closing connection: " + f, e);
                ch.close();
            }
        }

        private void beginWrite(String path) {
            discardWrite();
            writeJob = WriteJob.open(new File(path));
            phase = Phase.RECEIVE_WRITE;
        }

        private void handleWriteChunk(ByteBuf in) throws Exception {
            String chunk = NettyUtils.readUTF(in);
            checkpoint();
            if (chunk.equals(NettyUtils.EOF)) {
                WriteJob job = writeJob;
                writeJob = null;
                phase = Phase.OP;
                Channel c = ch;
                exec.submit(() -> {
                    try {
                        job.commit();
                        replyOk(c);
                    } catch (Throwable t) {
                        job.discard();
                        replyErr(c, messageOf(t));
                    }
                });
            } else if (writeJob.error == null) {
                writeJob.write(Base64.getDecoder().decode(chunk));
            }
        }

        private void discardWrite() {
            if (writeJob != null) {
                writeJob.discard();
                writeJob = null;
            }
        }

        private void runStatus(Action action) {
            Channel c = ch;
            exec.submit(() -> {
                try {
                    action.run();
                    replyOk(c);
                } catch (Throwable t) {
                    replyErr(c, messageOf(t));
                }
            });
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            discardWrite();
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            AL.warn("File manager protocol error, closing connection: " + cause);
            discardWrite();
            ctx.close();
        }
    }

    // ------------------------------------------------------------------
    // Operations (run on the executor, except where noted)
    // ------------------------------------------------------------------
    private static void create(String path, boolean isDir) throws IOException {
        File f = new File(path);
        if (f.exists()) throw new IOException("Already exists: " + f.getName());
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory()) throw new IOException("Parent folder does not exist.");
        if (isDir) Files.createDirectory(f.toPath());
        else Files.createFile(f.toPath());
    }

    private static void delete(String[] paths) throws IOException {
        List<String> failed = new java.util.ArrayList<>();
        for (String p : paths) {
            try {
                FileUtils.forceDelete(new File(p));
            } catch (IOException e) {
                failed.add(new File(p).getName() + " (" + messageOf(e) + ")");
            }
        }
        if (!failed.isEmpty()) throw new IOException("Could not delete: " + String.join(", ", failed));
    }

    private static void rename(String path, String newName) throws IOException {
        checkName(newName);
        File src = new File(path);
        if (!src.exists()) throw new IOException("Not found: " + src.getName());
        File parent = src.getAbsoluteFile().getParentFile();
        if (parent == null) throw new IOException("Cannot rename a root.");
        File dest = new File(parent, newName);
        if (dest.exists()) throw new IOException("Already exists: " + newName);
        Files.move(src.toPath(), dest.toPath());
    }

    private static void copyOrCut(String[] paths, boolean[] isDirs, boolean isCopy, String targetDirPath)
            throws IOException {
        File targetDir = new File(targetDirPath);
        if (!targetDir.isDirectory()) throw new IOException("Target is not a folder: " + targetDirPath);

        for (int i = 0; i < paths.length; i++) {
            File src = new File(paths[i]);
            if (!src.exists()) throw new IOException("Not found: " + src.getName());
            File dest = new File(targetDir, src.getName());

            Path srcPath = src.toPath().toAbsolutePath().normalize();
            Path destPath = dest.toPath().toAbsolutePath().normalize();
            // Also covers "target == source" for folders.
            if (isDirs[i] && destPath.startsWith(srcPath))
                throw new IOException("Cannot move or copy a folder into itself: " + src.getName());
            // Covers "cut into the same folder" for files, which would otherwise delete the file.
            if (dest.exists()) throw new IOException("Already exists in target: " + src.getName());

            if (isCopy) {
                if (isDirs[i]) FileUtils.copyDirectory(src, dest);
                else Files.copy(src.toPath(), dest.toPath());
            } else {
                try {
                    Files.move(src.toPath(), dest.toPath());
                } catch (IOException moveFailed) {
                    // Typically a cross-file-system move: copy first, then remove the original.
                    if (isDirs[i]) {
                        FileUtils.copyDirectory(src, dest);
                        FileUtils.deleteDirectory(src);
                    } else {
                        Files.copy(src.toPath(), dest.toPath());
                        Files.delete(src.toPath());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Replies and transfer helpers
    // ------------------------------------------------------------------
    private static void replyWith(Channel ch, Builder builder) {
        ByteBuf b = ch.alloc().buffer();
        try {
            b.writeByte(STATUS_OK);
            builder.build(b);
        } catch (Throwable t) {
            b.release();
            replyErr(ch, messageOf(t));
            return;
        }
        ch.writeAndFlush(b);
    }

    private static void replyOk(Channel ch) {
        ByteBuf b = ch.alloc().buffer(1);
        b.writeByte(STATUS_OK);
        ch.writeAndFlush(b);
    }

    private static void replyErr(Channel ch, String message) {
        ByteBuf b = ch.alloc().buffer();
        b.writeByte(STATUS_ERR);
        NettyUtils.writeUTF(b, message);
        ch.writeAndFlush(b);
    }

    private static void writeRoots(ByteBuf b) {
        File[] roots = File.listRoots();
        b.writeInt(roots.length);
        for (File r : roots) NettyUtils.writeUTF(b, r.getAbsolutePath());
    }

    private static void writeDirectory(ByteBuf b, File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("Cannot list folder: " + dir.getName());
        encodeDetails(b, dir);
        b.writeInt(children.length);
        for (File f : children) if (f.isDirectory()) encodeDetails(b, f);
        for (File f : children) if (!f.isDirectory()) encodeDetails(b, f);
    }

    private static void encodeDetails(ByteBuf buf, File file) {
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

    private static void writeChunks(Channel ch, InputStream in) throws IOException {
        byte[] buffer = new byte[CHUNK_SIZE];
        int n;
        while ((n = in.read(buffer)) != -1) {
            byte[] slice = n == buffer.length ? buffer : Arrays.copyOf(buffer, n);
            ByteBuf b = ch.alloc().buffer();
            NettyUtils.writeUTF(b, Base64.getEncoder().encodeToString(slice));
            ch.writeAndFlush(b);
            awaitWritable(ch);
        }
        ByteBuf eof = ch.alloc().buffer();
        NettyUtils.writeUTF(eof, NettyUtils.EOF);
        ch.writeAndFlush(eof);
    }

    // Backpressure: keeps large transfers from piling up in memory.
    private static void awaitWritable(Channel ch) throws IOException {
        while (!ch.isWritable()) {
            if (!ch.isActive()) throw new IOException("Connection closed during transfer.");
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during transfer.");
            }
        }
    }

    private static String[] readStrings(ByteBuf in, int count) {
        if (count < 0) throw new IllegalStateException("Negative count: " + count);
        String[] result = new String[count];
        for (int i = 0; i < count; i++) result[i] = NettyUtils.readUTF(in);
        return result;
    }

    private static void checkName(String name) throws IOException {
        if (name == null || name.trim().isEmpty() || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0)
            throw new IOException("Invalid name: '" + name + "'");
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    // ------------------------------------------------------------------
    // Write job: writes to a temp file in the target folder and moves it
    // into place only after the full transfer succeeded.
    // ------------------------------------------------------------------
    private static final class WriteJob {
        final File target;
        File temp;
        OutputStream out;
        String error;

        private WriteJob(File target) {
            this.target = target;
        }

        static WriteJob open(File target) {
            WriteJob job = new WriteJob(target);
            try {
                if (target.isDirectory()) throw new IOException("Target is a folder: " + target.getName());
                File parent = target.getAbsoluteFile().getParentFile();
                if (parent == null || !parent.isDirectory()) throw new IOException("Parent folder does not exist.");
                job.temp = Files.createTempFile(parent.toPath(), "." + target.getName(), ".part").toFile();
                job.out = new BufferedOutputStream(new FileOutputStream(job.temp));
            } catch (Exception e) {
                job.fail(messageOf(e));
            }
            return job;
        }

        // Errors are recorded instead of thrown, so the decoder keeps consuming chunks until EOF.
        void write(byte[] data) {
            if (error != null) return;
            try {
                out.write(data);
            } catch (IOException e) {
                fail(messageOf(e));
            }
        }

        void commit() throws IOException {
            if (error != null) throw new IOException(error);
            out.close();
            out = null;
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        }

        void fail(String message) {
            error = message;
            discard();
        }

        void discard() {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
                out = null;
            }
            if (temp != null) {
                try { Files.deleteIfExists(temp.toPath()); } catch (IOException ignored) {}
                temp = null;
            }
        }
    }
}