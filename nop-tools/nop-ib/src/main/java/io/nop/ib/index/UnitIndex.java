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
    /** v2: ClassInfo gains super/tst/nstd facts; old payloads must reindex */
    public static final String FORMAT_VERSION = "2";

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

    /**
     * Dispatchable test classes (G-C tail defect fix): a src/test class qualifies
     * when it is itself executable by the JUnit platform — own test-method
     * annotations, or @Nested container, or tests inherited from a superclass in
     * this index. Nested helpers (ExposedXxxHandler-style) and lambdas used to be
     * dispatched too: they run zero tests, cost a worker round-trip each, and any
     * crash in them stalled the sweep tail for a full watchdog period.
     *
     * <p>A @Nested member of a dispatchable outer class is skipped: dispatching
     * the outer already discovers (and counts) it — dispatching both would count
     * its tests twice. Non-@Nested nested test classes are kept: JUnit runs them
     * independently of the outer container.
     *
     * <p>Boundary (rev. defect 18, flink): a CONCRETE test class whose superclass
     * lies outside this index (external test-jar base) is dispatched — its
     * inherited tests are real and Maven runs them; name-pattern filtering at the
     * call site bounds the set and the engine discovers zero tests for test-less
     * helpers, same as Maven. The class must also survive the surefire
     * include-pattern filter, so over-dispatch stays limited to
     * pattern-matching names (Test*, *Test, *Tests, *TestCase).
     */
    public Collection<ClassInfo> testClasses() {
        Map<String, Boolean> marker = new HashMap<>();
        List<ClassInfo> out = new ArrayList<>();
        for (ClassInfo info : classes.values()) {
            if (info.getSourceFile() == null || !info.getSourceFile().startsWith("src/test/"))
                continue;
            if (!isDispatchableTest(info.getClassName(), marker))
                continue;
            // defect 29 (wave6 6C-1): the double-dispatch guard must cover ALL
            // nested classes, not only jupiter @Nested ones. isNested() is
            // @Nested-annotation-only, so JUnit4 static inner classes (zeebe
            // gateway ActivateJobsTest: @RunWith(Enclosed.class) with two
            // Parameterized members) were dispatched BOTH via the Enclosed
            // container (vintage discovers them once = mvn's 18) AND standalone
            // (again = +18, 36 vs mvn's 18). Binary-name $ nesting is the
            // class-file-level fact and covers both worlds.
            if ((info.isNested() || hasEnclosingClassInIndex(info.getClassName()))
                    && coveredByOuterContainer(info.getClassName()))
                continue;
            out.add(info);
        }
        return out;
    }

    /** own markers or a marker-bearing superclass chain within this index. */
    private boolean isDispatchableTest(String className, Map<String, Boolean> memo) {
        Boolean cached = memo.get(className);
        if (cached != null)
            return cached;
        ClassInfo info = classes.get(className);
        if (info == null)
            return false;
        // cycle guard: mark as in-progress (false) so superclass cycles terminate
        memo.put(className, false);
        boolean result = info.hasTestMethods() || info.isNested()
                || (info.getSuperName() != null
                        && isDispatchableTest(info.getSuperName(), memo))
                || ownsDispatchableNested(className, memo);
        if (!result && info.getSuperName() != null
                && !"java.lang.Object".equals(info.getSuperName())
                && !classes.containsKey(info.getSuperName())
                && (info.getAccess() & 0x0400) == 0) {
            // flink observation (defect 18): a concrete class inheriting ALL tests
            // from a base outside this index (flink-runtime test-jar base
            // RestResponseMarshallingTestBase) broke the marker chain and was never
            // dispatched — Maven runs it (6 missed classes in flink-runtime-web).
            // Dispatch stays bounded by the surefire include-pattern filter at the
            // call site; the engine simply discovers zero tests for genuinely
            // test-less helpers, same as Maven. Abstract classes are never
            // dispatchable (they cannot be instantiated — Maven skips them too).
            result = true;
        }
        memo.put(className, result);
        return result;
    }

    /** class-file-level nesting fact: binary name carries a $ member separator
     *  (Java compilers emit $ only for nested/compiled-inner classes). */
    private boolean hasEnclosingClassInIndex(String className) {
        int idx = className.indexOf('$');
        while (idx > 0) {
            if (classes.containsKey(className.substring(0, idx)))
                return true;
            idx = className.indexOf('$', idx + 1);
        }
        return false;
    }

    /** a $-prefix ancestor in this index is itself dispatchable (it discovers me). */
    private boolean coveredByOuterContainer(String className) {
        int idx = className.indexOf('$');
        while (idx > 0) {
            String outer = className.substring(0, idx);
            if (isDispatchableTest(outer, new HashMap<>()))
                return true;
            idx = className.indexOf('$', idx + 1);
        }
        return false;
    }

    /**
     * true when any {@code className$...} member of this index is itself a
     * dispatchable test (defect 28, zeebe-util observation): a top-level class
     * whose tests live ENTIRELY in {@code @Nested} members carries no test
     * markers itself and used to be dropped — and with it the whole nested
     * family (CollectionUtilTest 9, FuturesUtilTest 6, VersionCompatibilityCheck
     * Test 12 on zeebe-util; 27 of mvn's 780). The JUnit platform discovers the
     * nested tree from the outer selector, same as Maven.
     */
    private boolean ownsDispatchableNested(String className, Map<String, Boolean> memo) {
        for (String name : classes.keySet()) {
            if (name.startsWith(className + "$")
                    && isDispatchableTest(name, memo))
                return true;
        }
        return false;
    }

    public void put(ClassInfo info) {
        classes.put(info.getClassName(), info);
    }

    public int size() {
        return classes.size();
    }
}
