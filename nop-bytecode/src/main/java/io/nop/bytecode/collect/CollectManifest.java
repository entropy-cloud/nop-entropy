package io.nop.bytecode.collect;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Incremental-collection manifest: maps artifact path →
 * {@code size;mtimeMillis;sha256;className;majorVersion}.
 *
 * <p>The className and version columns let an unchanged artifact be reused without re-reading
 * the file. Granularity assumption: the mtime column has filesystem timestamp granularity; on
 * coarse-grained filesystems (1s), a same-second same-size modification can be missed — a
 * consumer needing a strong guarantee clears the manifest and re-collects from scratch.
 */
public final class CollectManifest {

    // in-memory: path -> Entry
    private final Map<String, Entry> entries = new HashMap<>();

    public static final class Entry {
        public final long size;
        public final long mtimeMillis;
        public final String sha256;
        public final String className;
        public final int majorVersion;

        Entry(long size, long mtimeMillis, String sha256, String className, int majorVersion) {
            this.size = size;
            this.mtimeMillis = mtimeMillis;
            this.sha256 = sha256;
            this.className = className;
            this.majorVersion = majorVersion;
        }
    }

    public Entry get(String path) {
        return entries.get(path);
    }

    public void put(String path, Entry entry) {
        entries.put(path, entry);
    }

    public void remove(String path) {
        entries.remove(path);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public java.util.Set<String> keys() {
        return entries.keySet();
    }

    /** Snapshot of the current entries: the delta baseline for one collection run. */
    public java.util.Map<String, Entry> snapshot() {
        return new HashMap<>(entries);
    }

    public static CollectManifest load(Path file) throws IOException {
        CollectManifest m = new CollectManifest();
        if (Files.exists(file)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
            for (String path : props.stringPropertyNames()) {
                String[] parts = props.getProperty(path).split(";", -1);
                if (parts.length != 5) continue; // malformed rows are ignored, manifest is advisory
                try {
                    m.entries.put(path, new Entry(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                            parts[2], parts[3], Integer.parseInt(parts[4])));
                } catch (NumberFormatException e) {
                    // skip malformed row
                }
            }
        }
        return m;
    }

    public void save(Path file) throws IOException {
        Properties props = new Properties();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry en = e.getValue();
            props.setProperty(e.getKey(), en.size + ";" + en.mtimeMillis + ";" + en.sha256
                    + ";" + en.className + ";" + en.majorVersion);
        }
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "nop-bytecode collection manifest (path=size;mtimeMillis;sha256;className;majorVersion)");
        }
    }
}
