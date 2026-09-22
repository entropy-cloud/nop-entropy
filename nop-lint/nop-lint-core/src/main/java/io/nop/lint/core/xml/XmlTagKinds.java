package io.nop.lint.core.xml;

import io.nop.lint.core.NopLintException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tag-name kind space of the XNode XML path (design 01 §3.5: "标签名匹配
 * 相当于 kind 匹配"). Tag names are interned to dense non-negative ids on
 * first sight, so the engine's kind-bit filter ({@code KindIndex} +
 * {@code CompiledRule.canMatchKinds}) works over XML trees exactly as it does
 * over tree-sitter trees — same O(1) filter, same observability, zero
 * special-casing in the runner.
 *
 * <p>The interning table is per-JVM and unbounded by design: a lint run's
 * kind space is bounded by the tag names of the linted models, which is
 * orders of magnitude below grammar symbol tables. Ids are assigned
 * monotonically and never reused; the table is only written, never cleared,
 * so a node's id is stable for the whole run (and across runs, ids may
 * differ — no contract ever persists them).</p>
 *
 * <p>Only valid XML tag names are interned. Everything else (meta-var marker
 * forms like {@code $$$}, blank text) resolves to {@code -1}, which makes a
 * {@code kind: $$$} matcher fail closed at rule compile time instead of
 * silently interning a nonsense kind.</p>
 */
public final class XmlTagKinds {

    private static final Map<String, Integer> IDS = new ConcurrentHashMap<>();
    private static final Map<Integer, String> NAMES = new ConcurrentHashMap<>();

    private XmlTagKinds() {
    }

    /**
     * The interned kind id of {@code tagName}, assigning the next dense id on
     * first sight. Invalid tag names (blank, or starting with the meta-var
     * marker {@code $}) resolve to {@code -1} — the engine's fail-closed kind
     * resolution contract.
     */
    public static int idFor(String tagName) {
        if (tagName == null || tagName.isEmpty() || tagName.charAt(0) == '$') {
            return -1;
        }
        return IDS.computeIfAbsent(tagName, name -> {
            int id = IDS.size();
            NAMES.put(id, name);
            return id;
        });
    }

    /**
     * The tag name an id was interned for, or null when the id is unknown
     * (diagnostics and tests).
     */
    public static String tagNameFor(int id) {
        return NAMES.get(id);
    }
}
