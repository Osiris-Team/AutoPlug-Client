package me.hsgamer.hscore.collections.map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * String Map but case-insensitive
 *
 * @param <V> the type of the value
 */
public class CaseInsensitiveStringMap<V> implements Map<String, V> {
  private final Map<String, V> delegate;

  /**
   * Create a new case-insensitive map
   *
   * @param delegate the background map
   */
  public CaseInsensitiveStringMap(Map<String, V> delegate) {
    this.delegate = delegate;
    this.normalize();
  }

  private String getLowerCase(Object obj) {
    return String.valueOf(obj).toLowerCase(Locale.ROOT);
  }

  private void normalize() {
    Map<String, V> linkedMap = new LinkedHashMap<>(delegate);
    this.clear();
    this.putAll(linkedMap);
    linkedMap.clear();
  }

  @Override
  public int size() {
    return this.delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return this.delegate.isEmpty();
  }

  @Override
  public boolean containsKey(Object o) {
    return this.delegate.containsKey(getLowerCase(o));
  }

  @Override
  public boolean containsValue(Object o) {
    return this.delegate.containsValue(o);
  }

  @Override
  public V get(Object o) {
    return this.delegate.get(getLowerCase(o));
  }

  @Nullable
  @Override
  public V put(String s, V v) {
    return this.delegate.put(getLowerCase(s), v);
  }

  @Override
  public V remove(Object o) {
    return this.delegate.remove(getLowerCase(o));
  }

  @Override
  public void putAll(@NotNull Map<? extends String, ? extends V> map) {
    map.forEach(this::put);
  }

  @Override
  public void clear() {
    this.delegate.clear();
  }

  @NotNull
  @Override
  public Set<String> keySet() {
    return this.delegate.keySet();
  }

  @NotNull
  @Override
  public Collection<V> values() {
    return this.delegate.values();
  }

  @NotNull
  @Override
  public Set<Entry<String, V>> entrySet() {
    return this.delegate.entrySet();
  }
}
