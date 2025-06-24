package javafx.scene.image;

import java.nio.IntBuffer;

/**
 * Simplified pixel reader similar to JavaFX PixelReader
 */
public class PixelReader {
    private final Image image;
    private final IntBuffer pixelBuffer;

    public PixelReader(Image image) {
        this.image = image;
        this.pixelBuffer = image.getPixelBuffer();
    }

    /**
     * Get ARGB pixel color at (x,y)
     */
    public int getArgb(int x, int y) {
        if (x < 0 || x >= image.getWidth() || y < 0 || y >= image.getHeight()) {
            throw new IndexOutOfBoundsException(
                    String.format("Coordinates (%d,%d) out of bounds for image %dx%d",
                            x, y, image.getWidth(), image.getHeight()));
        }

        int pos = y * image.getWidth() + x;
        return pixelBuffer.get(pos);
    }

    /**
     * Get red component at (x,y) [0-255]
     */
    public int getRed(int x, int y) {
        return (getArgb(x, y) >> 16) & 0xFF;
    }

    /**
     * Get green component at (x,y) [0-255]
     */
    public int getGreen(int x, int y) {
        return (getArgb(x, y) >> 8) & 0xFF;
    }

    /**
     * Get blue component at (x,y) [0-255]
     */
    public int getBlue(int x, int y) {
        return getArgb(x, y) & 0xFF;
    }

    /**
     * Get alpha component at (x,y) [0-255]
     */
    public int getAlpha(int x, int y) {
        return (getArgb(x, y) >> 24) & 0xFF;
    }

    /**
     * Get pixel colors for a rectangular region
     */
    public int[] getPixels(int x, int y, int w, int h, int[] buffer, int offset, int scanlineStride) {
        if (buffer == null) {
            buffer = new int[offset + h * scanlineStride];
        }

        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int pos = offset + row * scanlineStride + col;
                buffer[pos] = getArgb(x + col, y + row);
            }
        }
        return buffer;
    }
}