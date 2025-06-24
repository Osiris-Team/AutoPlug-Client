package me.hsgamer.hscore.web;

import org.jetbrains.annotations.NotNull;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Methods on web
 */
public final class WebUtils {

  private WebUtils() {

  }

  /**
   * Create a new connection
   *
   * @param address the address / URL
   *
   * @return the connection
   *
   * @throws IOException If the URL is invalid or can't be connected
   */
  @NotNull
  public static URLConnection createConnection(@NotNull String address) throws IOException {
    return new URL(address).openConnection();
  }

  /**
   * Create a new connection
   *
   * @param address            the address / URL
   * @param connectionConsumer the consumer to set the connection
   *
   * @return the connection
   *
   * @throws IOException If the URL is invalid or can't be connected
   */
  @NotNull
  public static URLConnection createConnection(@NotNull String address, Consumer<URLConnection> connectionConsumer) throws IOException {
    URLConnection connection = createConnection(address);
    connectionConsumer.accept(connection);
    return new URL(address).openConnection();
  }

  /**
   * Create a new HTTP connection
   *
   * @param address the address / URL
   *
   * @return the connection
   *
   * @throws IOException If the URL is invalid or can't be connected
   */
  @NotNull
  public static HttpURLConnection createHttpConnection(@NotNull String address) throws IOException {
    URLConnection connection = createConnection(address);
    if (connection instanceof HttpURLConnection) {
      return (HttpURLConnection) connection;
    }
    connection.setConnectTimeout(5);
    throw new IOException("The URL is not a HTTP URL");
  }

  /**
   * Create a new HTTP connection
   *
   * @param address            the address / URL
   * @param connectionConsumer the consumer to set the connection
   *
   * @return the connection
   *
   * @throws IOException If the URL is invalid or can't be connected
   */
  @NotNull
  public static HttpURLConnection createHttpConnection(@NotNull String address, Consumer<HttpURLConnection> connectionConsumer) throws IOException {
    HttpURLConnection connection = createHttpConnection(address);
    connectionConsumer.accept(connection);
    return connection;
  }

  /**
   * Create a new HTTPS connection
   *
   * @param address the address / URL
   *
   * @return the connection
   *
   * @throws IOException If the URL is invalid or can't be connected
   */
  @NotNull
  public static HttpsURLConnection createHttpsConnection(@NotNull String address) throws IOException {
    URLConnection connection = createConnection(address);
    if (connection instanceof HttpsURLConnection) {
      return (HttpsURLConnection) connection;
    }
    connection.setConnectTimeout(5);
    throw new IOException("The URL is not a HTTPS URL");
  }

  /**
   * Create a new HTTPS connection
   *
   * @param address            the address / URL
   * @param connectionConsumer the consumer to set the connection
   *
   * @return the connection
   *
   * @throws IOException If the URL is invalid or can't be connected
   */
  @NotNull
  public static HttpsURLConnection createHttpsConnection(@NotNull String address, Consumer<HttpsURLConnection> connectionConsumer) throws IOException {
    HttpsURLConnection connection = createHttpsConnection(address);
    connectionConsumer.accept(connection);
    return connection;
  }

  /**
   * Encode the string
   *
   * @param string the string
   *
   * @return the encoded string
   */
  @NotNull
  public static String encodeUrl(@NotNull String string) {
    try {
      return URLEncoder.encode(string, StandardCharsets.UTF_8.toString());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Decode the string
   *
   * @param string the string
   *
   * @return the decoded string
   */
  @NotNull
  public static String decodeUrl(@NotNull String string) {
    try {
      return URLDecoder.decode(string, StandardCharsets.UTF_8.toString());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Make a URL with the address and the query map
   *
   * @param address  the address
   * @param queryMap the query map
   *
   * @return the URL
   */
  @NotNull
  public static String makeUrl(@NotNull String address, @NotNull Map<String, String> queryMap) {
    if (queryMap.isEmpty()) {
      return address;
    }

    String query = queryMap.entrySet().stream()
      .map(entry -> entry.getKey() + "=" + encodeUrl(entry.getValue()))
      .reduce((s1, s2) -> s1 + "&" + s2)
      .orElse("");
    return address + "?" + query;
  }
}
