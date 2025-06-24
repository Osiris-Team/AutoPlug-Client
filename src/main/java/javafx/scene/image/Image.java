package javafx.scene.image;

import java.io.InputStream;
import java.nio.IntBuffer;

/**
 * Fake class simulating javafx.scene.image.Image for testing.
 */
public class Image {
    private final int width;
    private final int height;
    private final int[][] pixels; // Stored as ARGB integers

    public Image(String resource) {
        // Simulate loading an image from a resource path
        // This is a dummy constructor for mocking purposes
        this.width = 1;
        this.height = 1;
        this.pixels = new int[1][1];
        pixels[0][0] = 0xFF000000; // black
    }

    public Image(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[height][width];
    }

    public Image(InputStream is) {
        // Simulate reading from an InputStream
        this.width = 1;
        this.height = 1;
        this.pixels = new int[1][1];
        pixels[0][0] = 0xFFFFFFFF; // white
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setPixel(int x, int y, int argb) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            pixels[y][x] = argb;
        }
    }

    public int getPixel(int x, int y) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return pixels[y][x];
        }
        return 0; // Transparent by default
    }

    public PixelReader getPixelReader() {
        return new PixelReader(this);
    }

    public boolean isError() {
        return false;
    }

    public Throwable getException() {
        return null;
    }

    public IntBuffer getPixelBuffer() {
        IntBuffer buffer = IntBuffer.allocate(width * height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.put(pixels[y][x]);
            }
        }
        buffer.rewind(); // Set position back to 0
        return buffer;
    }
}
