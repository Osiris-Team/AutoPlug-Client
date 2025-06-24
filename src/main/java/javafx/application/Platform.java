package javafx.application;

/**
 * Fake class
 */
public class Platform {
    public static void runLater(Runnable code) {
        new Thread(code).start();
    }
}
