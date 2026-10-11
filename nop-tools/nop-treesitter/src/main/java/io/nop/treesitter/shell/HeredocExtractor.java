package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

import java.util.List;
import java.util.Set;

/**
 * Capability 3: heredoc extraction.
 *
 * <p>Two shapes are accepted, and only two, because a heredoc's body is only
 * unambiguous when the command that opens it is the only command in the script.
 * Given {@code echo x && cat <<EOF}, the text {@code x} cannot be attributed
 * unambiguously, and an extraction that guesses is worse than one that declines.</p>
 *
 * <ul>
 *   <li><b>direct</b> — the root has exactly one named child, a
 *       {@code redirected_statement}, whose {@code redirect:} child is the only
 *       {@code heredoc_redirect} and whose three named children are
 *       {@code heredoc_start}, {@code heredoc_body}, {@code heredoc_end} in
 *       order;</li>
 *   <li><b>{@code cd} prefix</b> — the {@code body:} is a {@code list} of
 *       exactly two {@code command}s joined by a literal {@code &&}, the first
 *       named {@code cd} with exactly one argument, and the second named from the
 *       caller-supplied allow-set. The separator must be {@code &&}: {@code ;},
 *       {@code ||} and {@code |} do not guarantee the first command succeeded and
 *       therefore do not guarantee the second ran in that directory.</li>
 * </ul>
 *
 * <p>Herestring ({@code <<<}) is not part of this contract: it is a different
 * grammar node and never reaches the external scanner, and widening a capability
 * whose value is that it means exactly one thing would be a silent extension.</p>
 */
final class HeredocExtractor {

    private final Language language;

    HeredocExtractor(Language language) {
        this.language = language;
    }

    HeredocScript.Lookup extract(String script, Set<String> allowedCommandNames) {
        TSTree tree = new ShellAnalysis(language).parse(script);
        if (tree == null) {
            return HeredocScript.Absent.because("the script is empty");
        }
        TSNode root = tree.rootNode();
        if (root.hasError()) {
            return HeredocScript.Absent.because("the tree contains an ERROR or MISSING node");
        }
        ShellSource source = ShellAnalysis.sourceOf(tree);
        if (root.namedChildCount() != 1) {
            return HeredocScript.Absent.because(
                    "the script has " + root.namedChildCount() + " top-level statements, not 1");
        }
        TSNode only = root.namedChild(0);
        if (!"redirected_statement".equals(only.type())) {
            return HeredocScript.Absent.because(
                    "the single top-level statement is " + only.type() + ", not redirected_statement");
        }

        TSNode redirect = only.childByFieldName("redirect");
        if (redirect == null || !"heredoc_redirect".equals(redirect.type())) {
            return HeredocScript.Absent.because("redirect: is not a heredoc_redirect");
        }
        if (redirect.namedChildCount() != 3) {
            return HeredocScript.Absent.because(
                    "heredoc_redirect has " + redirect.namedChildCount() + " named children, not 3");
        }
        TSNode start = redirect.namedChild(0);
        TSNode body = redirect.namedChild(1);
        TSNode end = redirect.namedChild(2);
        if (!"heredoc_start".equals(start.type())
                || !"heredoc_body".equals(body.type())
                || !"heredoc_end".equals(end.type())) {
            return HeredocScript.Absent.because(
                    "heredoc_redirect children are " + start.type() + "/" + body.type() + "/"
                            + end.type() + ", not heredoc_start/heredoc_body/heredoc_end");
        }

        TSNode bodyNode = only.childByFieldName("body");
        String commandName;
        if (bodyNode == null) {
            return HeredocScript.Absent.because("redirected_statement has no body:");
        }
        if ("command".equals(bodyNode.type())) {
            commandName = commandNameOf(bodyNode, source);
            if (commandName.isEmpty()) {
                return HeredocScript.Absent.because("the heredoc command has no literal name");
            }
            if (bodyNode.namedChildCount() != 1) {
                // The reference implementation's anchor is "the command node has
                // exactly one child", not "has no word child": `apply_patch "$X"`
                // and `FOO=bar apply_patch` have the same `command` node with no
                // `word` child at all, yet neither is a bare command, and the
                // first is provably not even literal.
                return HeredocScript.Absent.because(
                        "the heredoc command takes arguments, so the heredoc is not the whole command line");
            }
        } else if ("list".equals(bodyNode.type())) {
            List<TSNode> commands = ShellAnalysis.namedChildrenOfType(bodyNode, "command");
            if (commands.size() != 2) {
                return HeredocScript.Absent.because("the cd-prefix form needs exactly 2 commands, found "
                        + commands.size());
            }
            if (!onlyConnectorIs(bodyNode, "&&")) {
                return HeredocScript.Absent.because("the cd-prefix form requires && as its only separator");
            }
            TSNode first = commands.get(0);
            if (!"cd".equals(commandNameOf(first, source))
                    || ShellAnalysis.namedChildrenOfType(first, "word").size() != 1) {
                return HeredocScript.Absent.because("the first command must be `cd <one argument>`");
            }
            TSNode secondCommand = commands.get(1);
            String second = commandNameOf(secondCommand, source);
            if (allowedCommandNames == null || allowedCommandNames.isEmpty()
                    || !allowedCommandNames.contains(second)) {
                return HeredocScript.Absent.because(
                        "`" + second + "` is not in the caller's allowed command set");
            }
            if (secondCommand.namedChildCount() != 1) {
                // same bare-command anchor as the direct form
                return HeredocScript.Absent.because(
                        "the heredoc command takes arguments, so the heredoc is not the whole command line");
            }
            commandName = second;
        } else {
            return HeredocScript.Absent.because("body: is " + bodyNode.type()
                    + ", neither command nor the cd-prefix list");
        }

        String delimiter = ShellLexical.text(start, source);
        boolean quoted = delimiter.startsWith("'") || delimiter.startsWith("\"")
                || delimiter.startsWith("\\");
        return new HeredocScript.Found(delimiter, ShellLexical.text(body, source),
                quoted, commandName, body.startByte(), body.endByte());
    }

    /** The literal text of a command's {@code name:} field, or the empty string. */
    private static String commandNameOf(TSNode command, ShellSource source) {
        TSNode name = command.childByFieldName("name");
        return name == null ? "" : ShellLexical.text(name, source);
    }

    /**
     * Whether every anonymous child of the node is the given connector, and there
     * is at least one. Any other anonymous token — {@code ;}, {@code ||},
     * {@code |}, a redirection operator — fails.
     */
    private static boolean onlyConnectorIs(TSNode node, String connector) {
        int connectors = 0;
        int childCount = node.childCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = ShellAnalysis.childOrFail(node, i);
            if (child.named()) {
                continue;
            }
            if (!connector.equals(child.type())) {
                return false;
            }
            connectors++;
        }
        return connectors > 0;
    }
}
