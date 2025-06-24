package me.hsgamer.hscore.web;

import java.net.URLConnection;

/**
 * The user agent
 */
public final class UserAgent {
  /**
   * User agent for Firefox
   */
  public static final UserAgent FIREFOX = new UserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109) Gecko/20100101 Firefox/112.0");

  /**
   * User agent for Chrome
   */
  public static final UserAgent CHROME = new UserAgent("Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");

  /**
   * The agent string
   */
  private final String agent;

  /**
   * Create a new user agent
   *
   * @param agent the agent string
   */
  public UserAgent(String agent) {
    this.agent = agent;
  }

  /**
   * Get the agent string
   *
   * @return the agent string
   */
  public String getAgent() {
    return agent;
  }

  /**
   * Assign the user agent to the connection
   *
   * @param <T>        the type of the connection
   * @param connection the connection
   *
   * @return the connection for chaining
   */
  public <T extends URLConnection> T assignToConnection(T connection) {
    connection.setRequestProperty("User-Agent", agent);
    return connection;
  }
}
