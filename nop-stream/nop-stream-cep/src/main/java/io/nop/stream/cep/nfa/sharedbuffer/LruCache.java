package io.nop.stream.cep.nfa.sharedbuffer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * A thread-safe LRU cache backed by ConcurrentHashMap for O(1) access,
 * with a LinkedHashMap tracking access order for LRU eviction.
 *
 * @param <K> key type
 * @param <V> value type
 */
public class LruCache<K, V> {

    private final int maxSize;
    private final ConcurrentHashMap<K, V> map;
    private final LinkedHashMap<K, Boolean> accessTracker;

    public LruCache(int maxSize) {
        this.maxSize = maxSize;
        this.map = new ConcurrentHashMap<>(maxSize);
        this.accessTracker = new LinkedHashMap<K, Boolean>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Boolean> eldest) {
                return size() > LruCache.this.maxSize;
            }
        };
    }

    public V put(K key, V value) {
        V old = map.put(key, value);
        synchronized (this) {
            accessTracker.put(key, Boolean.TRUE);
            evictOverflow();
        }
        return old;
    }

    public V get(Object key) {
        V value = map.get(key);
        if (value != null) {
            synchronized (this) {
                accessTracker.put((K) key, Boolean.TRUE);
            }
        }
        return value;
    }

    public V remove(Object key) {
        V removed = map.remove(key);
        synchronized (this) {
            accessTracker.remove(key);
        }
        return removed;
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Collection<V> values() {
        return map.values();
    }

    public Set<Map.Entry<K, V>> entrySet() {
        return map.entrySet();
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void clear() {
        map.clear();
        synchronized (this) {
            accessTracker.clear();
        }
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        map.putAll(m);
        synchronized (this) {
            for (K key : m.keySet()) {
                accessTracker.put(key, Boolean.TRUE);
            }
            evictOverflow();
        }
    }

    public void forEach(BiConsumer<? super K, ? super V> action) {
        map.forEach(action);
    }

    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    /**
     * Removes all entries whose keys satisfy the given predicate.
     */
    public void removeIf(java.util.function.Predicate<? super K> predicate) {
        map.keySet().removeIf(predicate);
        synchronized (this) {
            accessTracker.keySet().removeIf(predicate);
        }
    }

    /**
     * Removes all entries whose keys are NOT in the given set.
     */
    public void keySetRemoveAll(Set<K> keysToRemove) {
        map.keySet().removeAll(keysToRemove);
        synchronized (this) {
            accessTracker.keySet().removeAll(keysToRemove);
        }
    }

    private void evictOverflow() {
        while (map.size() > maxSize && !accessTracker.isEmpty()) {
            Map.Entry<K, Boolean> eldest = accessTracker.entrySet().iterator().next();
            if (eldest != null) {
                map.remove(eldest.getKey());
                accessTracker.remove(eldest.getKey());
            } else {
                break;
            }
        }
    }
}
