package io.nop.ib.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Incremental unit state for one module: the module's class graph snapshot
 * (design 05 §6.2). Persisted at {@code .nop-ib/units/<module>.bin}.
 *
 * <p>Persistence format note (Wave 1 adjudication): the file is versioned JSON
 * ({@code formatVersion} first field) under the .bin name reserved by the
 * roadmap. Binary packing is a deferred optimization — the on-disk schema, not
 * the encoding, is the compatibility contract.
 */
public class UnitIndex {
    public static final String FORMAT_VERSION = "1";

    private String moduleArtifactId;
    private String formatVersion = FORMAT_VERSION;
    private long indexedAt;
    /** class binary name -> facts */
    private Map<String, ClassInfo> classes = new LinkedHashMap<>();

    public static UnitIndex empty(String moduleArtifactId) {
        UnitIndex index = new UnitIndex();
        index.moduleArtifactId = moduleArtifactId;
        return index;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("formatVersion", formatVersion);
        m.put("module", moduleArtifactId);
        m.put("indexedAt", indexedAt);
        List<Object> list = new ArrayList<>();
        for (ClassInfo info : classes.values())
            list.add(info.toMap());
        m.put("classes", list);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static UnitIndex fromMap(Map<String, Object> m) {
        UnitIndex index = new UnitIndex();
        index.moduleArtifactId = (String) m.get("module");
        Object fv = m.get("formatVersion");
        index.formatVersion = fv != null ? String.valueOf(fv) : FORMAT_VERSION;
        Object at = m.get("indexedAt");
        index.indexedAt = at instanceof Number n ? n.longValue() : 0L;
        Object list = m.get("classes");
        if (list instanceof Iterable<?> iter) {
            for (Object o : iter) {
                ClassInfo info = ClassInfo.fromMap((Map<String, Object>) o);
                index.classes.put(info.getClassName(), info);
            }
        }
        return index;
    }

    public String getModuleArtifactId() {
        return moduleArtifactId;
    }

    public long getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(long indexedAt) {
        this.indexedAt = indexedAt;
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public Map<String, ClassInfo> getClasses() {
        return classes;
    }

    public ClassInfo classInfo(String className) {
        return classes.get(className);
    }

    /** class -> classes in this module that reference it (direct reverse edges). */
    public Map<String, Set<String>> dependents() {
        Map<String, Set<String>> out = new HashMap<>();
        for (ClassInfo info : classes.values()) {
            for (String ref : info.getRefs()) {
                if (classes.containsKey(ref))
                    out.computeIfAbsent(ref, k -> new HashSet<>()).add(info.getClassName());
            }
        }
        return out;
    }

    /** source path (module-relative) -> class names declared by that source. */
    public Map<String, Set<String>> sourceToClasses() {
        Map<String, Set<String>> out = new TreeMap<>();
        for (ClassInfo info : classes.values()) {
            if (info.getSourceFile() != null) {
                out.computeIfAbsent(info.getSourceFile(), k -> new HashSet<>())
                        .add(info.getClassName());
            }
        }
        return out;
    }

    /** classes declared by a source file (current snapshot; empty if unknown). */
    public Set<String> classesOfSource(String sourceFile) {
        Set<String> out = new HashSet<>();
        for (ClassInfo info : classes.values()) {
            if (sourceFile.equals(info.getSourceFile()))
                out.add(info.getClassName());
        }
        return out;
    }

    /** test-source classes and their references (test selection seed surface). */
    public Collection<ClassInfo> testClasses() {
        List<ClassInfo> out = new ArrayList<>();
        for (ClassInfo info : classes.values()) {
            if (info.getSourceFile() != null && info.getSourceFile().startsWith("src/test/"))
                out.add(info);
        }
        return out;
    }

    public void put(ClassInfo info) {
        classes.put(info.getClassName(), info);
    }

    public int size() {
        return classes.size();
    }
}
