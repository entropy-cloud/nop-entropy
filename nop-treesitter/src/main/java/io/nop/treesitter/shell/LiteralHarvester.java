package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * Capability 2: the literal harvest.
 *
 * <p>Where capability 1 refuses on the first thing it cannot read, this one keeps
 * going: its job is to find the literal commands <em>anywhere</em> in the script,
 * including inside control flow, pipelines and command substitutions. That is a
 * different question, and the answer is incomplete by construction — which is why
 * it returns {@link LiteralHarvest} and not {@link ProvenCommandSequence}.</p>
 *
 * <p>The rule that decides how incompleteness is expressed: a command with an
 * unreadable child is reported as <b>partial</b>, carrying the literal words it
 * did read plus an explicit marker. Skipping the unreadable child and continuing
 * silently would hand the consumer an argv that looks complete and is not;
 * truncating would throw away the literal part. Neither is acceptable for a value
 * a guardrail reads.</p>
 */
final class LiteralHarvester {

    private final Language language;

    LiteralHarvester(Language language) {
        this.language = language;
    }

    LiteralHarvest harvest(String script) {
        TSTree tree = new ShellAnalysis(language).parse(script);
        if (tree == null) {
            return new LiteralHarvest(List.of());
        }
        TSNode root = tree.rootNode();
        if (root.hasError()) {
            // A tree with errors is not a tree whose commands can be enumerated;
            // returning a partial list here would look like "these are the commands".
            return new LiteralHarvest(List.of());
        }
        ShellSource source = ShellAnalysis.sourceOf(tree);
        List<LiteralHarvest.Command> commands = new ArrayList<>();
        collect(root, source, commands);
        return new LiteralHarvest(commands);
    }

    private void collect(TSNode node, ShellSource source, List<LiteralHarvest.Command> commands) {
        if ("command".equals(node.type())) {
            commands.add(readCommand(node, source));
        }
        int childCount = node.childCount();
        for (int i = 0; i < childCount; i++) {
            collect(ShellAnalysis.childOrFail(node, i), source, commands);
        }
    }

    /** Whether every {@code value:} of the assignment is a literal. */
    private static boolean isLiteralAssignment(TSNode assignment, ShellSource source) {
        TSNode value = assignment.childByFieldName("value");
        if (value == null) {
            // `A=` with no value at all is fully known.
            return true;
        }
        return ShellLexical.isLiteralNode(value, source);
    }

    private LiteralHarvest.Command readCommand(TSNode node, ShellSource source) {
        String name = "";
        List<String> arguments = new ArrayList<>();
        boolean partial = false;
        int childCount = node.childCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = ShellAnalysis.childOrFail(node, i);
            if (!child.named()) {
                continue;
            }
            if ("command_name".equals(child.type())) {
                if (ShellLexical.isLiteralNode(child, source)) {
                    name = ShellLexical.text(child, source);
                } else {
                    partial = true;
                }
            } else if ("variable_assignment".equals(child.type())
                    || "variable_assignments".equals(child.type())) {
                // A prefix assignment is not an argument: counting it as one would
                // shift every later argument position and mis-attribute the paths
                // that follow. Its literalness is decided by its `value:` field,
                // since the node itself is a container, not a token.
                if (!isLiteralAssignment(child, source)) {
                    partial = true;
                }
            } else if (ShellLexical.isLiteralNode(child, source)) {
                arguments.add(ShellLexical.text(child, source));
            } else {
                // The word exists but its value is computed at run time. Keep
                // the literal words around and mark the argv as incomplete.
                partial = true;
            }
        }
        return new LiteralHarvest.Command(name, arguments, partial, node.startByte());
    }
}
