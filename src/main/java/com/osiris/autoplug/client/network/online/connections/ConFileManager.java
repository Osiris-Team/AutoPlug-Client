package com.osiris.autoplug.client.network.online.connections;

import com.osiris.autoplug.client.configs.WebConfig;
import com.osiris.autoplug.client.network.online.DefaultConnection;
import com.osiris.autoplug.client.network.online.NettyUtils;
import com.osiris.autoplug.client.utils.GD;
import com.osiris.jlib.logger.AL;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ReplayingDecoder;
import org.apache.commons.io.FileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class ConFileManager extends DefaultConnection {
    public static boolean DEBUG = false;
    
    static final byte OP_GET = 0, OP_CREATE = 1, OP_DELETE = 2,
            OP_RENAME = 3, OP_WRITE = 4, OP_COPY_CUT = 6, OP_ROOTS = 7;

    static final byte STATUS_OK = 0, STATUS_ERR = 1;
    static final int CHUNK_SIZE = 8192;

    private enum Phase { OP, WAIT_CONTENT_FLAG, RECEIVE_WRITE }

    private interface Action {
        void run() throws Exception;
    }

    private interface Builder {
        void build(ByteBuf b) throws Exception;
    }

    public ConFileManager() {
        super((byte) 5);
    }

    @Override
    public boolean open() throws Exception {
        boolean enabled = new WebConfig().file_manager.asBoolean();
        if(DEBUG) AL.info("[FM CLIENT] open() called; enabled=" + enabled);

        if (!enabled) {
            if(DEBUG) AL.info("[FM CLIENT] open() aborted: file manager disabled");
            return false;
        }

        super.open();

        if(DEBUG) AL.info("[FM CLIENT] super.open() completed; channel=" + channel);
        if(DEBUG) AL.info("[FM CLIENT] Channel active=" + channel.isActive());
        return true;
    }

    @Override
    protected void initConnectionPipeline(SocketChannel ch) {
        if(DEBUG) AL.info("[FM CLIENT] Installing decoder during pipeline initialization");

        Decoder decoder = new Decoder(ch);
        ch.pipeline().addLast(decoder);

        if(DEBUG) AL.info("[FM CLIENT] Decoder installed; pipeline=" + ch.pipeline().names());
    }

    private final class Decoder extends ReplayingDecoder<Void> {
        private final Channel ch;
        private Phase phase = Phase.OP;
        private File pendingFile;
        private WriteJob writeJob;

        Decoder(Channel ch) {
            this.ch = ch;
            if(DEBUG) AL.info("[FM CLIENT] Decoder constructed; channel=" + ch);
        }

        @Override
        protected void decode(
                ChannelHandlerContext ctx,
                ByteBuf in,
                List<Object> out
        ) throws Exception {
            if(DEBUG) AL.info("[FM CLIENT] decode() entered"
                    + "; phase=" + phase
                    + "; readableBytes=" + in.readableBytes()
                    + "; active=" + ch.isActive()
                    + "; remote=" + ch.remoteAddress());

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
                default:
                    if(DEBUG) AL.info("[FM CLIENT] Unknown decoder phase=" + phase);
                    throw new IOException("Unknown decoder phase: " + phase);
            }
        }

        private void handleOp(ByteBuf in) throws Exception {
            if(DEBUG) AL.info("[FM CLIENT] handleOp() entered; readableBytes="
                    + in.readableBytes());

            byte op = in.readByte();

            if(DEBUG) AL.info("[FM CLIENT] Opcode received: signed=" + op
                    + "; unsigned=" + (op & 0xFF)
                    + "; remainingBytes=" + in.readableBytes());

            switch (op) {
                case OP_GET: {
                    String path = NettyUtils.readUTF(in);
                    if(DEBUG) AL.info("[FM CLIENT] OP_GET; path=" + path);
                    onGet(path);
                    break;
                }

                case OP_CREATE: {
                    String path = NettyUtils.readUTF(in);
                    boolean isDir = in.readBoolean();

                    if(DEBUG) AL.info("[FM CLIENT] OP_CREATE; path=" + path
                            + "; isDir=" + isDir);

                    runStatus("CREATE " + path, () -> create(path, isDir));
                    break;
                }

                case OP_DELETE: {
                    int count = in.readInt();
                    if(DEBUG) AL.info("[FM CLIENT] OP_DELETE; count=" + count);

                    String[] paths = readStrings(in, count);

                    if(DEBUG) AL.info("[FM CLIENT] OP_DELETE; paths="
                            + Arrays.toString(paths));

                    runStatus("DELETE", () -> delete(paths));
                    break;
                }

                case OP_RENAME: {
                    String path = NettyUtils.readUTF(in);
                    String newName = NettyUtils.readUTF(in);

                    if(DEBUG) AL.info("[FM CLIENT] OP_RENAME; path=" + path
                            + "; newName=" + newName);

                    runStatus("RENAME " + path, () -> rename(path, newName));
                    break;
                }

                case OP_WRITE: {
                    String path = NettyUtils.readUTF(in);
                    if(DEBUG) AL.info("[FM CLIENT] OP_WRITE; path=" + path);
                    beginWrite(path);
                    break;
                }

                case OP_COPY_CUT: {
                    int count = in.readInt();
                    boolean isCopy = in.readBoolean();
                    String targetDir = NettyUtils.readUTF(in);

                    if(DEBUG) AL.info("[FM CLIENT] OP_COPY_CUT; count=" + count
                            + "; isCopy=" + isCopy
                            + "; targetDir=" + targetDir);

                    if (count < 0) {
                        throw new IOException("Negative path count: " + count);
                    }

                    String[] paths = new String[count];
                    boolean[] isDirs = new boolean[count];

                    for (int i = 0; i < count; i++) {
                        paths[i] = NettyUtils.readUTF(in);
                        isDirs[i] = in.readBoolean();
                    }

                    if(DEBUG) AL.info("[FM CLIENT] OP_COPY_CUT; paths="
                            + Arrays.toString(paths)
                            + "; isDirs=" + Arrays.toString(isDirs));

                    runStatus("COPY_CUT",
                            () -> copyOrCut(paths, isDirs, isCopy, targetDir));
                    break;
                }

                case OP_ROOTS: {
                    Channel c = ch;

                    if(DEBUG) AL.info("[FM CLIENT] OP_ROOTS received"
                            + "; active=" + c.isActive()
                            + "; writable=" + c.isWritable()
                            + "; remote=" + c.remoteAddress());

                    try {
                        exec.submit(() -> {
                            if(DEBUG) AL.info("[FM CLIENT] ROOTS task started"
                                    + "; active=" + c.isActive());

                            replyWith(c, "ROOTS", ConFileManager::writeRoots);

                            if(DEBUG) AL.info("[FM CLIENT] ROOTS replyWith() returned");
                        });

                        if(DEBUG) AL.info("[FM CLIENT] ROOTS task submitted to executor");
                    } catch (Throwable t) {
                        if(DEBUG) AL.info("[FM CLIENT] Failed to submit ROOTS task: " + t);
                        throw t;
                    }

                    break;
                }

                default:
                    if(DEBUG) AL.info("[FM CLIENT] Unknown opcode=" + op
                            + "; remainingBytes=" + in.readableBytes()
                            + "; remote=" + ch.remoteAddress());

                    throw new IOException("Unknown opcode: " + op);
            }

            checkpoint();

            if(DEBUG) AL.info("[FM CLIENT] Opcode handled; opcode=" + op
                    + "; phase=" + phase
                    + "; remainingBytes=" + in.readableBytes());
        }

        private void onGet(String path) {
            File f = path.isEmpty() ? GD.WORKING_DIR : new File(path);

            if(DEBUG) AL.info("[FM CLIENT] GET path=" + f.getAbsolutePath()
                    + "; exists=" + f.exists()
                    + "; directory=" + f.isDirectory()
                    + "; readable=" + f.canRead());

            if (!f.exists()) {
                if(DEBUG) AL.info("[FM CLIENT] GET failed: file does not exist");
                replyErr(ch, "Not found: " + path);

            } else if (f.isDirectory()) {
                Channel c = ch;

                try {
                    exec.submit(() -> {
                        if(DEBUG) AL.info("[FM CLIENT] Directory listing task started: " + f);
                        replyWith(c, "GET directory " + f,
                                reply -> writeDirectory(reply, f));
                    });

                    if(DEBUG) AL.info("[FM CLIENT] Directory listing task submitted: " + f);
                } catch (Throwable t) {
                    if(DEBUG) AL.info("[FM CLIENT] Failed to submit directory listing: " + t);
                    replyErr(c, messageOf(t));
                }

            } else if (!f.isFile() || !f.canRead()) {
                if(DEBUG) AL.info("[FM CLIENT] GET failed: not a readable file");
                replyErr(ch, "Cannot read: " + path);

            } else {
                ByteBuf reply = ch.alloc().buffer();

                try {
                    reply.writeByte(STATUS_OK);
                    encodeDetails(reply, f);
                } catch (Throwable t) {
                    reply.release();
                    if(DEBUG) AL.info("[FM CLIENT] Failed to encode file details: " + t);
                    replyErr(ch, messageOf(t));
                    return;
                }

                ch.writeAndFlush(reply).addListener(future -> {
                    if (future.isSuccess()) {
                        if(DEBUG) AL.info("[FM CLIENT] GET details write succeeded: " + f);
                    } else {
                        if(DEBUG) AL.info("[FM CLIENT] GET details write failed: "
                                + future.cause());
                    }
                });

                pendingFile = f;
                phase = Phase.WAIT_CONTENT_FLAG;

                if(DEBUG) AL.info("[FM CLIENT] Waiting for content flag; file=" + f);
            }
        }

        private void handleContentFlag(ByteBuf in) {
            if(DEBUG) AL.info("[FM CLIENT] Reading content flag; readableBytes="
                    + in.readableBytes());

            boolean wantsContent = in.readBoolean();
            File f = pendingFile;

            if(DEBUG) AL.info("[FM CLIENT] Content flag received; wantsContent="
                    + wantsContent + "; file=" + f);

            pendingFile = null;
            phase = Phase.OP;
            checkpoint();

            if (wantsContent) {
                try {
                    exec.submit(() -> {
                        if(DEBUG) AL.info("[FM CLIENT] File streaming task started: " + f);
                        streamFile(f);
                    });
                    if(DEBUG) AL.info("[FM CLIENT] File streaming task submitted: " + f);
                } catch (Throwable t) {
                    if(DEBUG) AL.info("[FM CLIENT] Failed to submit file streaming task: " + t);
                    ch.close();
                }
            }
        }

        private void streamFile(File f) {
            try (InputStream in = new FileInputStream(f)) {
                if(DEBUG) AL.info("[FM CLIENT] Streaming file=" + f
                        + "; size=" + f.length());

                writeChunks(ch, in);

                if(DEBUG) AL.info("[FM CLIENT] File streaming completed: " + f);
            } catch (Exception e) {
                if(DEBUG) AL.info("[FM CLIENT] File transfer failed: " + f + "; error=" + e);
                ch.close();
            }
        }

        private void beginWrite(String path) {
            if(DEBUG) AL.info("[FM CLIENT] Starting write; path=" + path);

            discardWrite();
            writeJob = WriteJob.open(new File(path));
            phase = Phase.RECEIVE_WRITE;

            if(DEBUG) AL.info("[FM CLIENT] Write phase entered; path=" + path
                    + "; error=" + writeJob.error);
        }

        private void handleWriteChunk(ByteBuf in) throws Exception {
            String chunk = NettyUtils.readUTF(in);
            boolean eof = chunk.equals(NettyUtils.EOF);

            if(DEBUG) AL.info("[FM CLIENT] Write chunk received; encodedLength="
                    + chunk.length() + "; eof=" + eof);

            checkpoint();

            if (eof) {
                WriteJob job = writeJob;
                writeJob = null;
                phase = Phase.OP;
                Channel c = ch;

                try {
                    exec.submit(() -> {
                        try {
                            if(DEBUG) AL.info("[FM CLIENT] Committing write: " + job.target);
                            job.commit();
                            if(DEBUG) AL.info("[FM CLIENT] Write committed: " + job.target);
                            replyOk(c);
                        } catch (Throwable t) {
                            if(DEBUG) AL.info("[FM CLIENT] Write commit failed: " + t);
                            job.discard();
                            replyErr(c, messageOf(t));
                        }
                    });

                    if(DEBUG) AL.info("[FM CLIENT] Write commit task submitted");
                } catch (Throwable t) {
                    if(DEBUG) AL.info("[FM CLIENT] Failed to submit write commit: " + t);
                    job.discard();
                    replyErr(c, messageOf(t));
                }

            } else if (writeJob.error == null) {
                try {
                    byte[] data = Base64.getDecoder().decode(chunk);
                    writeJob.write(data);
                } catch (Throwable t) {
                    if(DEBUG) AL.info("[FM CLIENT] Failed to decode/write chunk: " + t);
                    throw t;
                }

            } else {
                if(DEBUG) AL.info("[FM CLIENT] Ignoring chunk; write error="
                        + writeJob.error);
            }
        }

        private void discardWrite() {
            if (writeJob != null) {
                if(DEBUG) AL.info("[FM CLIENT] Discarding incomplete write: "
                        + writeJob.target);

                writeJob.discard();
                writeJob = null;
            }
        }

        private void runStatus(String operation, Action action) {
            Channel c = ch;

            if(DEBUG) AL.info("[FM CLIENT] Submitting operation: " + operation);

            try {
                exec.submit(() -> {
                    if(DEBUG) AL.info("[FM CLIENT] Operation started: " + operation);

                    try {
                        action.run();
                        if(DEBUG) AL.info("[FM CLIENT] Operation succeeded: " + operation);
                        replyOk(c);
                    } catch (Throwable t) {
                        if(DEBUG) AL.info("[FM CLIENT] Operation failed: "
                                + operation + "; error=" + t);
                        replyErr(c, messageOf(t));
                    }
                });
            } catch (Throwable t) {
                if(DEBUG) AL.info("[FM CLIENT] Failed to submit operation: "
                        + operation + "; error=" + t);
                replyErr(c, messageOf(t));
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if(DEBUG) AL.info("[FM CLIENT] channelInactive; remote=" + ch.remoteAddress()
                    + "; phase=" + phase);

            discardWrite();
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if(DEBUG) AL.info("[FM CLIENT] Protocol exception; phase=" + phase
                    + "; remote=" + ch.remoteAddress()
                    + "; error=" + cause);

            discardWrite();
            ctx.close();
        }
    }

    private static void create(String path, boolean isDir) throws IOException {
        File f = new File(path);

        if(DEBUG) AL.info("[FM CLIENT] CREATE path=" + path + "; isDir=" + isDir);

        if (f.exists()) throw new IOException("Already exists: " + f.getName());

        File parent = f.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IOException("Parent folder does not exist.");
        }

        if (isDir) Files.createDirectory(f.toPath());
        else Files.createFile(f.toPath());

        if(DEBUG) AL.info("[FM CLIENT] CREATE completed: " + f);
    }

    private static void delete(String[] paths) throws IOException {
        if(DEBUG) AL.info("[FM CLIENT] DELETE started; count=" + paths.length);

        List<String> failed = new ArrayList<>();

        for (String p : paths) {
            try {
                FileUtils.forceDelete(new File(p));
                if(DEBUG) AL.info("[FM CLIENT] DELETE succeeded: " + p);
            } catch (IOException e) {
                if(DEBUG) AL.info("[FM CLIENT] DELETE failed: " + p + "; error=" + e);
                failed.add(new File(p).getName() + " (" + messageOf(e) + ")");
            }
        }

        if (!failed.isEmpty()) {
            throw new IOException("Could not delete: " + String.join(", ", failed));
        }
    }

    private static void rename(String path, String newName) throws IOException {
        if(DEBUG) AL.info("[FM CLIENT] RENAME path=" + path + "; newName=" + newName);

        checkName(newName);

        File src = new File(path);
        if (!src.exists()) throw new IOException("Not found: " + src.getName());

        File parent = src.getAbsoluteFile().getParentFile();
        if (parent == null) throw new IOException("Cannot rename a root.");

        File dest = new File(parent, newName);
        if (dest.exists()) throw new IOException("Already exists: " + newName);

        Files.move(src.toPath(), dest.toPath());

        if(DEBUG) AL.info("[FM CLIENT] RENAME completed: " + dest);
    }

    private static void copyOrCut(
            String[] paths,
            boolean[] isDirs,
            boolean isCopy,
            String targetDirPath
    ) throws IOException {
        if(DEBUG) AL.info("[FM CLIENT] COPY_CUT started; count=" + paths.length
                + "; isCopy=" + isCopy + "; target=" + targetDirPath);

        File targetDir = new File(targetDirPath);
        if (!targetDir.isDirectory()) {
            throw new IOException("Target is not a folder: " + targetDirPath);
        }

        for (int i = 0; i < paths.length; i++) {
            File src = new File(paths[i]);
            if (!src.exists()) throw new IOException("Not found: " + src.getName());

            File dest = new File(targetDir, src.getName());

            Path srcPath = src.toPath().toAbsolutePath().normalize();
            Path destPath = dest.toPath().toAbsolutePath().normalize();

            if (isDirs[i] && destPath.startsWith(srcPath)) {
                throw new IOException(
                        "Cannot move or copy a folder into itself: " + src.getName());
            }

            if (dest.exists()) {
                throw new IOException("Already exists in target: " + src.getName());
            }

            if(DEBUG) AL.info("[FM CLIENT] COPY_CUT item; source=" + src + "; dest=" + dest);

            if (isCopy) {
                if (isDirs[i]) FileUtils.copyDirectory(src, dest);
                else Files.copy(src.toPath(), dest.toPath());
            } else {
                try {
                    Files.move(src.toPath(), dest.toPath());
                } catch (IOException moveFailed) {
                    if(DEBUG) AL.info("[FM CLIENT] Move failed; attempting copy/delete: "
                            + moveFailed);

                    if (isDirs[i]) {
                        FileUtils.copyDirectory(src, dest);
                        FileUtils.deleteDirectory(src);
                    } else {
                        Files.copy(src.toPath(), dest.toPath());
                        Files.delete(src.toPath());
                    }
                }
            }

            if(DEBUG) AL.info("[FM CLIENT] COPY_CUT item completed: " + src);
        }
    }

    private static void replyWith(Channel ch, String operation, Builder builder) {
        if(DEBUG) AL.info("[FM CLIENT] Building reply: " + operation
                + "; active=" + ch.isActive()
                + "; writable=" + ch.isWritable());

        ByteBuf b = ch.alloc().buffer();

        try {
            b.writeByte(STATUS_OK);
            builder.build(b);

            if(DEBUG) AL.info("[FM CLIENT] Reply built: " + operation
                    + "; bytes=" + b.readableBytes());
        } catch (Throwable t) {
            b.release();

            if(DEBUG) AL.info("[FM CLIENT] Reply build failed: "
                    + operation + "; error=" + t);

            replyErr(ch, messageOf(t));
            return;
        }

        ch.writeAndFlush(b).addListener(future -> {
            if (future.isSuccess()) {
                if(DEBUG) AL.info("[FM CLIENT] Reply write succeeded: " + operation
                        + "; active=" + ch.isActive());
            } else {
                if(DEBUG) AL.info("[FM CLIENT] Reply write failed: "
                        + operation + "; error=" + future.cause());
            }
        });
    }

    private static void replyOk(Channel ch) {
        ByteBuf b = ch.alloc().buffer(1);
        b.writeByte(STATUS_OK);

        if(DEBUG) AL.info("[FM CLIENT] Sending STATUS_OK; active=" + ch.isActive());

        ch.writeAndFlush(b).addListener(future -> {
            if (future.isSuccess()) {
                if(DEBUG) AL.info("[FM CLIENT] STATUS_OK write succeeded");
            } else {
                if(DEBUG) AL.info("[FM CLIENT] STATUS_OK write failed: " + future.cause());
            }
        });
    }

    private static void replyErr(Channel ch, String message) {
        if(DEBUG) AL.info("[FM CLIENT] Sending STATUS_ERR; message=" + message);

        ByteBuf b = ch.alloc().buffer();
        b.writeByte(STATUS_ERR);
        NettyUtils.writeUTF(b, message);

        ch.writeAndFlush(b).addListener(future -> {
            if (future.isSuccess()) {
                if(DEBUG) AL.info("[FM CLIENT] STATUS_ERR write succeeded");
            } else {
                if(DEBUG) AL.info("[FM CLIENT] STATUS_ERR write failed: " + future.cause());
            }
        });
    }

    private static void writeRoots(ByteBuf b) {
        if(DEBUG) AL.info("[FM CLIENT] writeRoots() entered");

        File[] roots = File.listRoots();

        if(DEBUG) AL.info("[FM CLIENT] File.listRoots() result="
                + (roots == null ? "null" : roots.length + " roots"));

        if (roots == null) {
            throw new IllegalStateException("File.listRoots() returned null");
        }

        b.writeInt(roots.length);

        for (File r : roots) {
            if(DEBUG) AL.info("[FM CLIENT] Encoding root: " + r.getAbsolutePath());
            NettyUtils.writeUTF(b, r.getAbsolutePath());
        }

        if(DEBUG) AL.info("[FM CLIENT] writeRoots() completed");
    }

    private static void writeDirectory(ByteBuf b, File dir) throws IOException {
        if(DEBUG) AL.info("[FM CLIENT] writeDirectory() entered: " + dir.getAbsolutePath());

        File[] children = dir.listFiles();
        if (children == null) {
            throw new IOException("Cannot list folder: " + dir.getName());
        }

        encodeDetails(b, dir);
        b.writeInt(children.length);

        for (File f : children) {
            if (f.isDirectory()) encodeDetails(b, f);
        }

        for (File f : children) {
            if (!f.isDirectory()) encodeDetails(b, f);
        }

        if(DEBUG) AL.info("[FM CLIENT] writeDirectory() completed; children="
                + children.length + "; dir=" + dir);
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

            ch.writeAndFlush(b).addListener(future -> {
                if (!future.isSuccess()) {
                    if(DEBUG) AL.info("[FM CLIENT] File chunk write failed: " + future.cause());
                }
            });

            if(DEBUG) AL.info("[FM CLIENT] File chunk queued; bytes=" + n);

            awaitWritable(ch);
        }

        ByteBuf eof = ch.alloc().buffer();
        NettyUtils.writeUTF(eof, NettyUtils.EOF);

        ch.writeAndFlush(eof).addListener(future -> {
            if (future.isSuccess()) {
                if(DEBUG) AL.info("[FM CLIENT] EOF chunk write succeeded");
            } else {
                if(DEBUG) AL.info("[FM CLIENT] EOF chunk write failed: " + future.cause());
            }
        });
    }

    private static void awaitWritable(Channel ch) throws IOException {
        while (!ch.isWritable()) {
            if (!ch.isActive()) {
                throw new IOException("Connection closed during transfer.");
            }

            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during transfer.");
            }
        }
    }

    private static String[] readStrings(ByteBuf in, int count) {
        if (count < 0) {
            throw new IllegalStateException("Negative count: " + count);
        }

        String[] result = new String[count];

        for (int i = 0; i < count; i++) {
            result[i] = NettyUtils.readUTF(in);
        }

        return result;
    }

    private static void checkName(String name) throws IOException {
        if (name == null || name.trim().isEmpty()
                || name.equals(".") || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new IOException("Invalid name: '" + name + "'");
        }
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() != null
                ? t.getMessage()
                : t.getClass().getSimpleName();
    }

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
                if (target.isDirectory()) {
                    throw new IOException("Target is a folder: " + target.getName());
                }

                File parent = target.getAbsoluteFile().getParentFile();
                if (parent == null || !parent.isDirectory()) {
                    throw new IOException("Parent folder does not exist.");
                }

                job.temp = Files.createTempFile(
                        parent.toPath(), "." + target.getName(), ".part").toFile();

                job.out = new BufferedOutputStream(new FileOutputStream(job.temp));

                if(DEBUG) AL.info("[FM CLIENT] Write temp file created: " + job.temp);
            } catch (Exception e) {
                if(DEBUG) AL.info("[FM CLIENT] Failed to open write target: "
                        + target + "; error=" + e);
                job.fail(messageOf(e));
            }

            return job;
        }

        void write(byte[] data) {
            if (error != null) return;

            try {
                out.write(data);
            } catch (IOException e) {
                if(DEBUG) AL.info("[FM CLIENT] Temp file write failed: " + e);
                fail(messageOf(e));
            }
        }

        void commit() throws IOException {
            if (error != null) {
                throw new IOException(error);
            }

            out.close();
            out = null;

            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                if(DEBUG) AL.info("[FM CLIENT] Atomic move failed; retrying normal move: "
                        + atomicFailed);

                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            temp = null;
        }

        void fail(String message) {
            error = message;
            discard();
        }

        void discard() {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    if(DEBUG) AL.info("[FM CLIENT] Failed closing temp output: " + ignored);
                }

                out = null;
            }

            if (temp != null) {
                try {
                    Files.deleteIfExists(temp.toPath());
                } catch (IOException ignored) {
                    if(DEBUG) AL.info("[FM CLIENT] Failed deleting temp file: " + ignored);
                }

                temp = null;
            }
        }
    }
}