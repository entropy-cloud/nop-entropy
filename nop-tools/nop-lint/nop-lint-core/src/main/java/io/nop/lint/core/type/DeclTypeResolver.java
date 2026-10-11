package io.nop.lint.core.type;

import io.nop.lint.core.node.LintNode;

/**
 * L1 declaration-type extraction over the {@link LintNode} facade (design 06
 * §5.2): given a declaration node whose grammar production carries a
 * {@code type} named field — Java's {@code field_declaration},
 * {@code local_variable_declaration}, {@code method_declaration} (return
 * type) and {@code formal_parameter} — returns the declared type exactly as
 * written in the source. Generics ({@code List<String>}), array suffixes
 * ({@code String[]}), qualified names ({@code java.util.Map}) and wildcards
 * ({@code List<?>}) are returned verbatim; no normalization, resolution or
 * inference happens here (type resolution and {@code var} initializer
 * inference are L2 scope, design 06 §5.2).
 *
 * <p>Failure semantics are explicit: a node without a {@code type} field —
 * including every non-declaration node — yields {@code null}, never a
 * guess. A {@code var} declaration yields the literal text {@code "var"}.</p>
 *
 * <p>Stateless and allocation-free per query: the single-method API is safe
 * to share across rules and embeddable as an L1 capability for xscript
 * inline checks (roadmap item 14).</p>
 */
public class DeclTypeResolver {

    /**
     * The grammar field slot that carries the declared type in declaration
     * productions shared across supported languages.
     */
    public static final String TYPE_FIELD = "type";

    /**
     * Extracts the declared type text of a declaration node.
     *
     * @param declNode a declaration node ({@code field_declaration},
     *                 {@code local_variable_declaration},
     *                 {@code method_declaration}, {@code formal_parameter},
     *                 or any other node whose production has a {@code type}
     *                 field); may be null
     * @return the type text exactly as written (e.g. {@code "String"},
     *         {@code "List<String>"}, {@code "String[]"},
     *         {@code "java.util.Map"}, the literal {@code "var"}), or null
     *         when the node is null or occupies no {@code type} field
     */
    public String resolveDeclType(LintNode declNode) {
        if (declNode == null) {
            return null;
        }
        LintNode typeNode = declNode.childByField(TYPE_FIELD);
        return typeNode != null ? typeNode.text() : null;
    }
}
