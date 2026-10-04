package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

import java.util.ArrayList;
import java.util.List;

/**
 * Capability 5: candidate literal file paths.
 *
 * <p>Six dimensions decide what counts as a path: the source command's
 * argument-position convention (with everything after {@code --} treated as a
 * path); the literal form; the confidence of the source; flag exclusion;
 * redirect destinations as a separate write class; and no filesystem check.</p>
 *
 * <p>The confidence annotation is the part that matters most. A path taken from a
 * {@link ShellAnalysis.ProvenSplitter} proof and a path taken from a
 * {@link LiteralHarvest} partial are different claims, and a consumer that cannot
 * tell them apart will treat "this script mentions /data somewhere" as "this
 * script provably touches /data".</p>
 *
 * <p>No filesystem check is performed. This capability extracts literals; whether
 * a path exists is a different question, with a different cost and a different
 * failure mode, and answering it here would make the output depend on the
 * machine.</p>
 */
final class PathExtractor {

    private final Language language;
    private final ShellAnalysis analysis;

    PathExtractor(Language language, ShellAnalysis analysis) {
        this.language = language;
        this.analysis = analysis;
    }

    List<ShellPath> extract(String script) {
        List<ShellPath> paths = new ArrayList<>();

        ProvenSplit proven = analysis.splitProven(script);
        if (proven instanceof ProvenCommandSequence sequence) {
            for (ProvenCommandSequence.Command command : sequence.commands()) {
                addFromProof(command.name(), command.arguments(), paths);
            }
        } else {
            LiteralHarvest harvest = analysis.harvestLiterals(script);
            for (LiteralHarvest.Command command : harvest.commands()) {
                addFromHarvest(command.name(), command.arguments(), paths);
            }
        }

        addRedirectDestinations(script, paths);
        return List.copyOf(paths);
    }

    private void addFromProof(String name, List<String> arguments, List<ShellPath> paths) {
        int[] positions = ShellAnalysis.PATH_ARGUMENT_POSITIONS.get(name);
        boolean allArguments = ShellAnalysis.ALL_ARGUMENTS_ARE_PATHS.contains(name);
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if ("--".equals(argument)) {
                for (int j = i + 1; j < arguments.size(); j++) {
                    addIfPath(arguments.get(j), ShellPath.Access.READ, ShellPath.Confidence.PROVEN, paths);
                }
                return;
            }
            if (isFlag(argument)) {
                continue;
            }
            boolean selected = allArguments || (positions != null && contains(positions, i + 1));
            if (selected) {
                addIfPath(argument, ShellPath.Access.READ, ShellPath.Confidence.PROVEN, paths);
            }
        }
    }

    private void addFromHarvest(String name, List<String> arguments, List<ShellPath> paths) {
        int[] positions = ShellAnalysis.PATH_ARGUMENT_POSITIONS.get(name);
        boolean allArguments = ShellAnalysis.ALL_ARGUMENTS_ARE_PATHS.contains(name);
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if ("--".equals(argument)) {
                for (int j = i + 1; j < arguments.size(); j++) {
                    addIfPath(arguments.get(j), ShellPath.Access.READ,
                            ShellPath.Confidence.HARVESTED, paths);
                }
                return;
            }
            if (isFlag(argument)) {
                continue;
            }
            boolean selected = allArguments || (positions != null && contains(positions, i + 1));
            if (selected) {
                addIfPath(argument, ShellPath.Access.READ, ShellPath.Confidence.HARVESTED, paths);
            }
        }
    }

    /**
     * Redirect destinations are always <b>write</b> paths, kept in their own class
     * so a consumer can tell "this script writes here" from "this script reads
     * here" without re-deriving it from the source.
     */
    private void addRedirectDestinations(String script, List<ShellPath> paths) {
        TSTree tree = analysis.parse(script);
        if (tree == null) {
            return;
        }
        String source = ShellAnalysis.sourceText(tree);
        ShellAnalysis.walk(tree.rootNode(), node -> {
            if (!node.named() || !"file_redirect".equals(node.type())) {
                return true;
            }
            TSNode destination = node.childByFieldName("destination");
            if (destination == null) {
                return true;
            }
            String text = ShellLexical.text(destination, source);
            addIfPath(text, ShellPath.Access.WRITE,
                    tree.rootNode().hasError() ? ShellPath.Confidence.HARVESTED
                            : ShellPath.Confidence.PROVEN, paths);
            return true;
        });
    }

    /**
     * Whether a token is usable as a path literal.
     *
     * <p>Excluded: flags, tokens containing glob metacharacters, tokens starting
     * with {@code ~} (a tilde is an expansion, not a name), and tokens containing
     * any residual expansion character.</p>
     */
    private static void addIfPath(String token, ShellPath.Access access,
                                  ShellPath.Confidence confidence, List<ShellPath> paths) {
        if (!isPathLiteral(token)) {
            return;
        }
        for (ShellPath existing : paths) {
            if (existing.path().equals(token) && existing.access() == access
                    && existing.confidence() == confidence) {
                return;
            }
        }
        paths.add(new ShellPath(token, access, confidence));
    }

    static boolean isPathLiteral(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        if (token.startsWith("~")) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '*' || c == '?' || c == '[' || c == ']' || c == '{' || c == '}'
                    || c == '$' || c == '`' || c == '\\' || c == '"' || c == '\'') {
                return false;
            }
        }
        return true;
    }

    /**
     * A single dash is a path (stdin/stdout); two or more is a flag. Deciding it
     * here rather than inside the form check is what lets a post-{@code --} name
     * like {@code --weird-name} through while still excluding {@code -r}.
     */
    private static boolean isFlag(String token) {
        return token.length() > 1 && token.charAt(0) == '-';
    }

    private static boolean contains(int[] positions, int index) {
        for (int position : positions) {
            if (position == index) {
                return true;
            }
        }
        return false;
    }
}
