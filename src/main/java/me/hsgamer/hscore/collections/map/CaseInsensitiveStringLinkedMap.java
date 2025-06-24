package me.hsgamer.hscore.collections.map;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Linked String Map but case-insensitive
 *
 * @param <V> the type of the value
 */
public class CaseInsensitiveStringLinkedMap<V> extends CaseInsensitiveStringMap<V> {

  public CaseInsensitiveStringLinkedMap() {
    super(new LinkedHashMap<>());
  }

  public CaseInsensitiveStringLinkedMap(Map<String, ? extends V> map) {
    this();
    putAll(map);
  }
}
