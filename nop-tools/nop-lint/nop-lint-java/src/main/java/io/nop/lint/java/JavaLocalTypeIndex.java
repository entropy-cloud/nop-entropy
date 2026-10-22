package io.nop.lint.java;

import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.semantic.LocalTypeIndex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Java local type index (the {@code LTYPE} substrate): one walk over the
 * parsed tree binds every declared name to its declared or trivially
 * inferred type —
 * <ul>
 * <li>local variables and fields: {@code local_variable_declaration} /
 * {@code field_declaration} headers; an {@code implicit_type} ({@code var})
 * infers from the initializer when the shape is decidable (string literal →
 * {@code String}, constructor → its class)</li>
 * <li>method parameters: {@code formal_parameter} headers</li>
 * <li>same-file method return types (method-invocation receivers)</li>
 * </ul>
 * Scoping is file-global rather than method-local: the facade tree's parent
 * pointers are not a stable navigation surface, so the index answers from
 * the first declaration of a name anywhere in the file. Shadowing across
 * methods with diverging types is outside the model and stays unknown
 * (conflicting declarations keep the first binding) — answers still come
 * only from what the file declares, null for everything else (cross-file
 * types, chains, unlisted shapes). No guesses.
 */
final class JavaLocalTypeIndex implements LocalTypeIndex {

    private final Map<String, String> locals = new HashMap<>();
    private final Map<String, String> fields = new HashMap<>();
    private final Map<String, String> methodReturns = new HashMap<>();

    private JavaLocalTypeIndex() {
    }

    static JavaLocalTypeIndex build(LintTree tree) {
        JavaLocalTypeIndex index = new JavaLocalTypeIndex();
        index.walk(tree.root(), null);
        return index;
    }

    private void walk(LintNode node, String currentMethod) {
        switch (node.kind()) {
            case "method_declaration" -> {
                String name = methodName(node);
                String declared = headerType(node);
                if (name != null && declared != null && !"implicit_type".equals(declared)
                        && !"void".equals(declared)) {
                    methodReturns.putIfAbsent(name, declared);
                }
                walkChildren(node, currentMethod);
                return;
            }
            case "local_variable_declaration" -> {
                declareVariables(node, false);
                return;
            }
            case "field_declaration" -> {
                declareVariables(node, true);
                return;
            }
            case "formal_parameter" -> {
                String type = headerType(node);
                String name = firstName(node);
                if (type != null && name != null) {
                    locals.putIfAbsent(name, type);
                }
                return;
            }
            default -> {
                // fall through to the generic child walk
            }
        }
        walkChildren(node, currentMethod);
    }

    private void walkChildren(LintNode node, String currentMethod) {
        // direct children only — the Iterable form is a deep pre-order walk
        // of the subtree and would re-enter walk at every descendant
        for (LintNode child : node.children()) {
            walk(child, currentMethod);
        }
    }

    private void declareVariables(LintNode declaration, boolean isField) {
        String declared = headerType(declaration);

        for (LintNode child : declaration.children()) {
            if (!"variable_declarator".equals(child.kind())) {
                continue;
            }
            String name = null;
            LintNode value = null;
            for (LintNode part : child.children()) {
                if ("identifier".equals(part.kind()) && name == null) {
                    name = part.text();
                } else if (!"dimensions".equals(part.kind()) && !"identifier".equals(part.kind())) {
                    value = part;
                }
            }
            if (name == null) {
                continue;
            }
            String type = declared;
            if ("implicit_type".equals(declared)) {
                type = inferredType(value);
            }
            if (type == null) {
                continue;
            }
            if (isField) {
                fields.putIfAbsent(name, type);
            } else {
                locals.putIfAbsent(name, type);
            }
        }
    }

    /**
     * The declared type of a declaration header: the first child that is one
     * of the tree-sitter-java type shapes, or {@code "implicit_type"} for a
     * {@code var} declaration (inferred by the caller from the initializer).
     */
    private static final Set<String> TYPE_KINDS = Set.of("type_identifier", "scoped_type_identifier",
            "generic_type", "array_type", "implicit_type", "void_type");

    private static String headerType(LintNode declaration) {
        for (LintNode child : declaration.children()) {
            String kind = child.kind();
            if ("modifiers".equals(kind)) {
                continue;
            }
            if (TYPE_KINDS.contains(kind)) {
                if ("implicit_type".equals(kind) || "var".equals(child.text())) {
                    return "implicit_type";
                }
                return child.text();
            }
        }
        return null;
    }

    /**
     * Trivial initializer inference for {@code var}: a string literal is a
     * {@code String}, a constructor the constructed class. Any other shape
     * (factory calls, arrays, lambdas) is left unknown — never guessed.
     */
    private static String inferredType(LintNode initializer) {
        if (initializer == null) {
            return null;
        }
        if ("string_literal".equals(initializer.kind())) {
            return "String";
        }
        if ("object_creation_expression".equals(initializer.kind())) {
            for (LintNode child : initializer.children()) {
                if (TYPE_KINDS.contains(child.kind()) && !"void_type".equals(child.kind())) {
                    return child.text();
                }
            }
        }
        if ("method_invocation".equals(initializer.kind())) {
            // the platform's dominant pattern-source idiom: Pattern.compile(..).
            // child order is [object, name, arguments], so the method name is
            // the LAST direct identifier and the receiver the first
            String object = null;
            String name = null;
            for (LintNode child : initializer.children()) {
                if ("identifier".equals(child.kind())) {
                    if (name != null) {
                        object = name;
                    }
                    name = child.text();
                }
            }
            if ("compile".equals(name) && (object == null || "Pattern".equals(object))) {
                return "Pattern";
            }
        }
        return null;
    }

    private static String methodName(LintNode declaration) {
        return firstName(declaration);
    }

    private static String firstName(LintNode node) {
        for (LintNode child : node.children()) {
            if ("identifier".equals(child.kind())) {
                return child.text();
            }
        }
        return null;
    }

    @Override
    public String typeOf(LintNode receiver) {
        switch (receiver.kind()) {
            case "identifier": {
                String name = receiver.text();
                String local = locals.get(name);
                return local != null ? local : fields.get(name);
            }
            case "field_access": {
                List<LintNode> children = receiver.children();
                if (children.isEmpty()) {
                    return null;
                }
                return fields.get(children.get(children.size() - 1).text());
            }
            case "method_invocation": {
                for (LintNode child : receiver.children()) {
                    if ("identifier".equals(child.kind())) {
                        return methodReturns.get(child.text());
                    }
                }
                return null;
            }
            default:
                return null;
        }
    }

}
