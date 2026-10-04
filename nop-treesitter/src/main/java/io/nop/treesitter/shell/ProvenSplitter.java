package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Capability 1: the proven command sequence.
 *
 * <p>One depth-first pass both <b>validates</b> and <b>collects</b>. Validating
 * separately from collecting is what makes the proof checkable: there is no path
 * that emits a command without having passed the whitelist and lexical gates for
 * it and for every node above it, and the first refusal discards everything
 * collected so far.</p>
 *
 * <p>Gate order is load-bearing. A syntax error is checked before anything else,
 * because a tree containing an error is not a tree whose shape can be trusted;
 * then the node-kind whitelist, because a kind outside it is a structural
 * statement about the script that no amount of literal-looking text can repair;
 * then the lexical rules.</p>
 */
final class ProvenSplitter {

    private final Language language;

    ProvenSplitter(Language language) {
        this.language = language;
    }

    ProvenSplit split(String script) {
        TSTree tree = new ShellAnalysis(language).parse(script);
        if (tree == null) {
            return UnprovenScript.at(UnprovenScript.Reason.UNSUPPORTED_LANGUAGE, 0, "",
                    "the script is empty, so there is nothing to prove");
        }
        TSNode root = tree.rootNode();
        if (root.hasError()) {
            return UnprovenScript.at(UnprovenScript.Reason.SYNTAX_ERROR, root.startByte(), "ERROR",
                    "the tree contains an ERROR or MISSING node");
        }
        Collector collector = new Collector(ShellAnalysis.sourceText(tree));
        collector.accept(root);
        return collector.refusal != null
                ? collector.refusal
                : new ProvenCommandSequence(collector.commands);
    }

    /**
     * Recursive validator and collector. Returns as soon as the tree is refused;
     * the caller then discards whatever was collected.
     */
    private static final class Collector {

        private final String source;
        private final List<ProvenCommandSequence.Command> commands = new ArrayList<>();
        /** Connectors seen since the last emitted command, oldest first. */
        private final Deque<String> pendingConnectors = new ArrayDeque<>();
        private UnprovenScript refusal;

        Collector(String source) {
            this.source = source;
        }

        private void accept(TSNode node) {
            String type = node.type();

            if (!node.named()) {
                if (!ShellNodeTypes.PROVABLE_PUNCTUATION.contains(type)) {
                    refuse(UnprovenScript.Reason.CONSTRUCT_OUTSIDE_WHITELIST, node,
                            "anonymous token outside the provable whitelist");
                    return;
                }
                if (isConnector(type)) {
                    pendingConnectors.addLast(type);
                }
                return;
            }

            if (!ShellNodeTypes.PROVABLE_KINDS.contains(type)) {
                UnprovenScript.Reason reason = ShellNodeTypes.DYNAMIC_KINDS.contains(type)
                        ? UnprovenScript.Reason.DYNAMIC_CONSTRUCT
                        : UnprovenScript.Reason.CONSTRUCT_OUTSIDE_WHITELIST;
                refuse(reason, node, "node kind outside the provable whitelist");
                return;
            }

            if (!ShellLexical.isLiteralNode(node, source)) {
                refuse(UnprovenScript.Reason.NON_LITERAL_LEXICAL_FORM, node,
                        "the node's text is not a literal");
                return;
            }

            if ("command".equals(type)) {
                emit(node);
                if (refusal != null) {
                    return;
                }
            }

            int childCount = node.childCount();
            for (int i = 0; i < childCount; i++) {
                accept(ShellAnalysis.childOrFail(node, i));
                if (refusal != null) {
                    return;
                }
            }
        }

        private void emit(TSNode node) {
            String name = "";
            List<String> arguments = new ArrayList<>();
            int childCount = node.childCount();
            for (int i = 0; i < childCount; i++) {
                TSNode child = ShellAnalysis.childOrFail(node, i);
                if (!child.named()) {
                    continue;
                }
                if ("command_name".equals(child.type())) {
                    name = ShellLexical.text(child, source);
                } else {
                    arguments.add(ShellLexical.text(child, source));
                }
            }
            String connector = pendingConnectors.isEmpty() ? "" : pendingConnectors.removeFirst();
            commands.add(new ProvenCommandSequence.Command(name, arguments, connector));
        }

        private void refuse(UnprovenScript.Reason reason, TSNode node, String detail) {
            if (refusal == null) {
                refusal = UnprovenScript.at(reason, node.startByte(), node.type(), detail);
            }
        }

        private static boolean isConnector(String type) {
            return "&&".equals(type) || "||".equals(type) || ";" .equals(type) || "|".equals(type);
        }
    }
}
