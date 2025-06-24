package me.hsgamer.hscore.logger.common;

/**
 * The logger
 */
public interface Logger {
  /**
   * Log a message
   *
   * @param level   the level
   * @param message the message
   */
  void log(LogLevel level, String message);

  /**
   * Log an info message
   *
   * @param message the message
   */
  default void log(String message) {
    log(LogLevel.INFO, message);
  }

  /**
   * Log a throwable
   *
   * @param level     the level
   * @param throwable the throwable
   */
  default void log(LogLevel level, Throwable throwable) {
    if (throwable == null) {
      return;
    }

    log(level, throwable.getClass().getName() + ": " + throwable.getMessage());
    for (StackTraceElement element : throwable.getStackTrace()) {
      log(level, "    " + element.toString());
    }

    Throwable cause = throwable.getCause();
    if (cause != null) {
      log(level, "Caused by: " + cause.getMessage());
      log(level, cause);
    }
  }

  /**
   * Log a message and a throwable
   *
   * @param level     the level
   * @param message   the message
   * @param throwable the throwable
   */
  default void log(LogLevel level, String message, Throwable throwable) {
    log(level, message);
    log(level, throwable);
  }
}
