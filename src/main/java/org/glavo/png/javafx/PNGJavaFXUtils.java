package org.glavo.png.javafx;


import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PNGJavaFXUtils {

    /**
     * Converts a fake JavaFX Image to a PNG byte array.
     */
    public static byte[] writeImageToArray(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getPixel(x, y);
                bufferedImage.setRGB(x, y, argb);
            }
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(bufferedImage, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode image to PNG", e);
        }
    }
}
