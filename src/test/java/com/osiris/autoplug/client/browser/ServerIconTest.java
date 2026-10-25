package com.osiris.autoplug.client.browser;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ServerIconTest {
    @Test void decodesStatusPngAndPreservesItsPixels() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(7, 11, 0xFF23875A);
        BufferedImage decoded = ServerIcon.decode(data(image, "png"));
        assertNotNull(decoded); assertEquals(64, decoded.getWidth()); assertEquals(64, decoded.getHeight());
        assertEquals(0xFF23875A, decoded.getRGB(7, 11));
    }

    @Test void rejectsUrlsNonPngWrongDimensionsOversizeAndMalformedContent() throws Exception {
        assertNull(ServerIcon.decode("https://example.invalid/server-icon.png"));
        assertNull(ServerIcon.decode(null)); assertNull(ServerIcon.decode("data:image/png;base64,!!!"));
        assertNull(ServerIcon.decode(data(new BufferedImage(65, 64, BufferedImage.TYPE_INT_RGB), "png")));
        assertNull(ServerIcon.decode(data(new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB), "gif").replace("image/gif", "image/png")));
        assertNull(ServerIcon.decode("data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[96 * 1024 + 1])));
        assertNull(ServerIcon.decode("data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[]{(byte)137,80,78,71,13,10,26,10})));
    }

    private static String data(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); assertTrue(ImageIO.write(image, format, bytes));
        return "data:image/" + format + ";base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
