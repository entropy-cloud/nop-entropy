package io.nop.lint.core.fix;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.SourcePattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The compiled autofix template of one rule (roadmap item 25, design 01 §2 /
 * design 04 §7): the template text splits into literal fragments and
 * meta-var slots at compile time; {@link #apply} renders one match's
 * replacement.
 *
 * <p>Replacement semantics (plan 2026-09-22-2225-1 adjudications): a single
 * capture renders the captured node's source text verbatim; a sequence
 * capture ({@code $$$VAR}) renders the raw source between the first and last
 * captured nodes (commas and comments preserved, an empty sequence renders
 * as the empty string); every other {@code $TOKEN} in the template is a
 * compile-time rejection — the fail-closed stand-in for an escape syntax
 * that v1 does not have. Replacement text is source-verbatim; automatic
 * reindentation is deferred with the other design 04 §7 extensions.
 */
public final class TemplateFix {

    private final String template;
    private final List<Object> parts;

    private TemplateFix(String template, List<Object> parts) {
        this.template = template;
        this.parts = parts;
    }

    /**
     * Compiles the template against the rule's declared capture sets: every
     * {@code $VAR} must name a single-node capture, every {@code $$$VAR} a
     * sequence capture, and no undeclared {@code $TOKEN} may remain — a
     * template referencing a capture the matcher never binds would render
     * garbage at run time, so it is rejected here with the rule id.
     */
    public static TemplateFix compile(String ruleId, String template, Set<String> singleCaptures,
                                      Set<String> multiCaptures) {
        List<Object> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c != '$') {
                literal.append(c);
                i++;
                continue;
            }
            int dollarRun = 0;
            while (i < template.length() && template.charAt(i) == '$') {
                dollarRun++;
                i++;
            }
            String name = readName(template, i);
            if (name == null) {
                literal.append("$".repeat(dollarRun));
                continue;
            }
            i += name.length();
            if (literal.length() > 0) {
                parts.add(literal.toString());
                literal = new StringBuilder();
            }
            if (dollarRun == 1 && singleCaptures.contains(name)) {
                parts.add(new Slot(name, false));
            } else if (dollarRun == 3 && multiCaptures.contains(name)) {
                parts.add(new Slot(name, true));
            } else {
                throw new NopLintException("Rule '" + ruleId + "' has a fix template referencing '"
                        + "$".repeat(dollarRun) + name + "', which no matcher of the rule declares "
                        + "(add the meta-var to a pattern, or fix the reference; undeclared '$' "
                        + "tokens are rejected because v1 has no escape syntax; fail-closed)");
            }
        }
        if (literal.length() > 0) {
            parts.add(literal.toString());
        }
        return new TemplateFix(template, parts);
    }

    private static String readName(String template, int start) {
        int end = start;
        while (end < template.length() && isNameChar(template.charAt(end))) {
            end++;
        }
        return end == start ? null : template.substring(start, end);
    }

    private static boolean isNameChar(char c) {
        return c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    /**
     * Renders the replacement for one match (design 04 §7
     * {@code TemplateFix.apply}). The source slices come from the caller so
     * sequence captures can cut the raw text between their first and last
     * nodes.
     */
    public String apply(MetaVarEnv env, byte[] source) {
        StringBuilder out = new StringBuilder();
        for (Object part : parts) {
            if (part instanceof String text) {
                out.append(text);
            } else {
                Slot slot = (Slot) part;
                out.append(slot.render(env, source));
            }
        }
        return out.toString();
    }

    String template() {
        return template;
    }

    private record Slot(String name, boolean multi) {

        String render(MetaVarEnv env, byte[] source) {
            if (multi) {
                List<LintNode> nodes = env.getMultiCapture(name);
                if (nodes.isEmpty())
                    return "";
                return sourceSlice(source, nodes.get(0).range().startByte(),
                        nodes.get(nodes.size() - 1).range().endByte());
            }
            LintNode node = env.getCapture(name);
            if (node == null) {
                throw new NopLintException("fix template capture '" + name + "' is unbound at "
                        + "apply time (the compile-time reference check guarantees declared "
                        + "captures bind; invariant broken)");
            }
            return node.text();
        }

        private static String sourceSlice(byte[] source, int start, int end) {
            return new String(source, start, end - start, java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
