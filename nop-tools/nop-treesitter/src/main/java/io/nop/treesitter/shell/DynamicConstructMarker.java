package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Capability 4: where the script computes values at run time.
 *
 * <p>Nothing is evaluated — working out what {@code $(...)} produces means running
 * it, which is the thing this layer exists to make unnecessary. Every finding is a
 * byte range plus a kind.</p>
 *
 * <p>Two entries exist for reasons a naive implementation gets wrong.</p>
 *
 * <p><b>{@code concatenation}</b> is enumerated separately from the expansions: it
 * is the node that models a literal glued to an expansion, so if it were not
 * marked, {@code pre"$X"post} would report <em>no</em> dynamic construction and
 * read as fully literal. That is a safety hole, not an incompleteness.</p>
 *
 * <p><b>The background operator</b> has no named node type in the bash grammar —
 * it is an anonymous token — so a walk that considers only named nodes reports
 * nothing and the script reads as if nothing were left running in the
 * background.</p>
 */
final class DynamicConstructMarker {

    private final Language language;

    DynamicConstructMarker(Language language) {
        this.language = language;
    }

    List<DynamicConstruct> mark(String script) {
        TSTree tree = new ShellAnalysis(language).parse(script);
        if (tree == null) {
            return List.of();
        }
        ShellSource source = ShellAnalysis.sourceOf(tree);
        Map<DynamicConstruct.Kind, List<DynamicConstruct>> found =
                new EnumMap<>(DynamicConstruct.Kind.class);

        ShellAnalysis.walk(tree.rootNode(), node -> {
            DynamicConstruct.Kind kind = kindOf(node);
            if (kind != null) {
                found.computeIfAbsent(kind, k -> new ArrayList<>())
                        .add(new DynamicConstruct(kind, node.startByte(), node.endByte(),
                                ShellLexical.text(node, source)));
            }
            return true;
        });

        List<DynamicConstruct> result = new ArrayList<>();
        for (Map.Entry<DynamicConstruct.Kind, List<DynamicConstruct>> entry : found.entrySet()) {
            result.addAll(entry.getValue());
        }
        result.sort((a, b) -> a.startByte() != b.startByte()
                ? Integer.compare(a.startByte(), b.startByte())
                : Integer.compare(a.endByte(), b.endByte()));
        return List.copyOf(result);
    }

    private static DynamicConstruct.Kind kindOf(TSNode node) {
        if (!node.named()) {
            // The background operator is an anonymous token, so a named-only walk
            // would never report it.
            return "&".equals(node.type()) ? DynamicConstruct.Kind.BACKGROUND_OPERATOR : null;
        }
        switch (node.type()) {
            case "command_substitution":
                return DynamicConstruct.Kind.COMMAND_SUBSTITUTION;
            case "process_substitution":
                return DynamicConstruct.Kind.PROCESS_SUBSTITUTION;
            case "simple_expansion":
                return DynamicConstruct.Kind.SIMPLE_EXPANSION;
            case "expansion":
                return DynamicConstruct.Kind.EXPANSION;
            case "arithmetic_expansion":
                return DynamicConstruct.Kind.ARITHMETIC_EXPANSION;
            case "concatenation":
                return isMixedLiteral(node) ? DynamicConstruct.Kind.CONCATENATION : null;
            case "variable_assignment":
            case "variable_assignments":
                return DynamicConstruct.Kind.VARIABLE_ASSIGNMENT;
            case "file_redirect":
                return DynamicConstruct.Kind.FILE_REDIRECT;
            case "redirected_statement":
                return DynamicConstruct.Kind.REDIRECTED_STATEMENT;
            case "heredoc_redirect":
                return DynamicConstruct.Kind.HEREDOC_REDIRECT;
            case "heredoc_start":
                return DynamicConstruct.Kind.HEREDOC_START;
            case "heredoc_body":
                return DynamicConstruct.Kind.HEREDOC_BODY;
            case "heredoc_end":
                return DynamicConstruct.Kind.HEREDOC_END;
            case "heredoc_content":
                return DynamicConstruct.Kind.HEREDOC_CONTENT;
            case "herestring_redirect":
                return DynamicConstruct.Kind.HERESTRING_REDIRECT;
            default:
                return null;
        }
    }

    /**
     * A {@code concatenation} is a dynamic construct only when it actually mixes a
     * literal with something computed. A concatenation of two literals
     * ({@code a"b"}) is just a word written in two pieces, and reporting it would
     * make the finding list noisy enough to be ignored — which is how a real
     * finding gets missed.
     */
    private static boolean isMixedLiteral(TSNode node) {
        boolean[] literalPart = {false};
        boolean[] dynamic = {false};
        classify(node, literalPart, dynamic);
        return dynamic[0] && literalPart[0];
    }

    /**
     * Classifies the parts of a concatenation. The quoting containers
     * ({@code string}, {@code raw_string}) and nested concatenations are descended
     * through rather than judged: {@code a"b"} is a {@code string} holding only
     * {@code string_content}, and treating the container itself as dynamic would
     * mark every two-piece word as computed.
     */
    private static void classify(TSNode node, boolean[] literalPart, boolean[] dynamic) {
        String type = node.type();
        switch (type) {
            case "word":
            case "number":
            case "string_content":
                literalPart[0] = true;
                return;
            case "string":
            case "raw_string":
            case "concatenation": {
                int childCount = node.childCount();
                for (int i = 0; i < childCount; i++) {
                    TSNode child = ShellAnalysis.childOrFail(node, i);
                    if (child.named()) {
                        classify(child, literalPart, dynamic);
                    }
                }
                return;
            }
            default:
                dynamic[0] = true;
        }
    }

}
