package me.hsgamer.hscore.collections.map;

import java.util.HashMap;
import java.util.Map;

/**
 * String Hash Map but case-insensitive
 *
 * @param <V> the type of the value
 */
public class CaseInsensitiveStringHashMap<V> extends CaseInsensitiveStringMap<V> {

  public CaseInsensitiveStringHashMap() {
    super(new HashMap<>());
  }

  public CaseInsensitiveStringHashMap(Map<String, ? extends V> map) {
    this();
    putAll(map);
  }
}
