package com.osiris.autoplug.client.browser;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Bounded Minecraft status favicon decoder. Never fetches a URL or creates disk cache files. */
final class ServerIcon {
    private static final String PREFIX = "data:image/png;base64,";
    private static final int MAX_BYTES = 96 * 1024;
    private ServerIcon() { }

    static BufferedImage decode(String data) {
        if (data == null || !data.startsWith(PREFIX) || data.length() > PREFIX.length() + MAX_BYTES * 4 / 3) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(data.substring(PREFIX.length()));
            if (bytes.length < 24 || bytes.length > MAX_BYTES) return null;
            byte[] signature = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
            for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return null;
            try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
                if (!readers.hasNext()) return null;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    if (reader.getWidth(0) != 64 || reader.getHeight(0) != 64) return null;
                    return reader.read(0);
                } finally { reader.dispose(); }
            }
        } catch (IOException | RuntimeException invalid) { return null; }
    }
}
