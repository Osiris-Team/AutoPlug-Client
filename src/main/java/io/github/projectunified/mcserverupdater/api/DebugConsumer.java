package io.github.projectunified.mcserverupdater.api;

public interface DebugConsumer {
    void consume(String message);

    default void consume(Throwable throwable) {
        consume(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        for (StackTraceElement element : throwable.getStackTrace()) {
            consume("    at " + element.toString());
        }
    }

    default void consume(String message, Throwable throwable) {
        consume(message);
        consume(throwable);
    }
}
