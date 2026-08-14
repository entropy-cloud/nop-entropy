/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import io.nop.core.lang.json.JsonTool;

/**
 * Shared test helper for the nop-stream invariant gates (Cycle 1 / I1).
 *
 * <p>Reads the single-source-of-truth gate table {@code ai-dev/audits/nop-stream-invariants/
 * gate-inventory.json} (one entry per class, listing its change-type methods) and verifies
 * two-way exact equality against live reflection: a method added to code but missing from the
 * table is red; a table entry pointing at a method that no longer exists is red.
 *
 * <p>The classifier mirrors the mechanical I0 §4.1 rules implemented by
 * {@code ai-dev/tools/check-nop-stream-invariants.mjs}: change-type methods are the
 * non-private (public/protected/package-private) methods declared by the class/interface
 * itself (not inherited, not nested-class members), excluding static methods, abstract
 * methods in classes (abstract in interfaces is included), constructors, synthetic/bridge
 * methods, test-only accessors ({@code *ForTest*}), and read accessors named
 * {@code get*}/{@code is*}/{@code has*} ({@code getAnd*}/{@code getOr*} are mutators and ARE
 * included).
 */
public class InvariantTableCompleteness {

    static final Set<String> EXCLUDED_NAMES = Set.of(
            "equals", "hashCode", "toString", "getClass", "wait", "notify", "notifyAll",
            "finalize", "clone");

    /**
     * Locate the gate inventory file by walking up from the test working directory
     * (surefire runs with basedir = module dir). Fails loudly when not found —
     * no silent skip.
     */
    public static Path findInventoryFile() {
        String prop = System.getProperty("nop.gate.inventory");
        if (prop != null) {
            Path p = Paths.get(prop);
            if (Files.exists(p)) {
                return p;
            }
            throw new IllegalStateException("nop.gate.inventory system property points to missing file: " + p);
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8; i++) {
            Path candidate = dir.resolve("ai-dev/audits/nop-stream-invariants/gate-inventory.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
            if (dir == null) {
                break;
            }
        }
        throw new IllegalStateException(
                "gate-inventory.json not found (searched up from " + Paths.get("").toAbsolutePath()
                        + "); no silent skip — set -Dnop.gate.inventory=<abs path> or run from the repo");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadInventory() {
        Path file = findInventoryFile();
        Object parsed;
        try {
            parsed = JsonTool.parse(Files.readString(file));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read gate-inventory.json: " + file, e);
        }
        if (!(parsed instanceof Map)) {
            throw new IllegalStateException("gate-inventory.json is not a JSON object: " + file);
        }
        return (Map<String, Object>) parsed;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> moduleClasses(Map<String, Object> inventory, String module) {
        Object modules = inventory.get("modules");
        if (!(modules instanceof Map)) {
            throw new IllegalStateException("gate-inventory.json has no 'modules' object");
        }
        Object classes = ((Map<String, Object>) modules).get(module);
        if (!(classes instanceof Map)) {
            throw new IllegalStateException("gate-inventory.json has no module section: " + module);
        }
        return (Map<String, Object>) classes;
    }

    @SuppressWarnings("unchecked")
    public static List<String> tableMethods(Map<String, Object> classEntry) {
        Object methods = classEntry.get("methods");
        if (!(methods instanceof List)) {
            throw new IllegalStateException("gate-inventory entry has no 'methods' array: " + classEntry);
        }
        return (List<String>) methods;
    }

    /**
     * Mechanical change-type classifier, mirroring the mjs scanner.
     */
    public static Set<String> changeTypeMethodNames(Class<?> clazz) {
        Set<String> names = new TreeSet<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (isChangeTypeMethod(clazz, m)) {
                names.add(m.getName());
            }
        }
        return names;
    }

    static boolean isChangeTypeMethod(Class<?> clazz, Method m) {
        int mod = m.getModifiers();
        if (Modifier.isPrivate(mod)) {
            return false;
        }
        if (Modifier.isStatic(mod)) {
            return false;
        }
        if (m.isSynthetic() || m.isBridge()) {
            return false;
        }
        if (!clazz.isInterface() && Modifier.isAbstract(mod)) {
            return false;
        }
        String name = m.getName();
        if (EXCLUDED_NAMES.contains(name)) {
            return false;
        }
        if (name.contains("ForTest")) {
            return false;
        }
        if (isReadAccessorName(name)) {
            return false;
        }
        return true;
    }

    static boolean isReadAccessorName(String name) {
        return (name.startsWith("get") || name.startsWith("is") || name.startsWith("has"))
                && !name.startsWith("getAnd") && !name.startsWith("getOr");
    }

    /**
     * Two-way exact equality between the gate table and the live reflection set.
     * Returns human-readable violations; empty list = green.
     *
     * @param label       class label for messages
     * @param table       methods listed in gate-inventory.json
     * @param live        methods enumerated from live code (reflection or fixture)
     */
    public static List<String> findViolations(String label, Set<String> table, Set<String> live) {
        List<String> violations = new ArrayList<>();
        for (String name : new TreeSet<>(table)) {
            if (!live.contains(name)) {
                violations.add(label + ": table lists method absent from code (ghost): " + name);
            }
        }
        for (String name : new TreeSet<>(live)) {
            if (!table.contains(name)) {
                violations.add(label + ": change-type method missing from table: " + name);
            }
        }
        return violations;
    }
}
