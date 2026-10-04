package io.nop.treesitter.shell;

import io.nop.treesitter.language.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capability 5: candidate literal file paths.
 *
 * <p>Six dimensions decide what counts as a path: the source command's
 * argument-position convention (with everything after {@code --} treated as a
 * path); the literal form; the confidence of the source; flag exclusion;
 * redirect destinations as a separate write class; and no filesystem check.</p>
 *
 * <p>The confidence annotation is the part that matters most. A path taken from a
 * proven script and a path taken from a harvest are different claims, and a
 * consumer that cannot tell them apart will read "this script mentions /data
 * somewhere" as "this script provably touches /data".</p>
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
                collectFrom(command.name(), command.arguments(),
                        ShellPath.Confidence.PROVEN, paths);
            }
        } else {
            for (LiteralHarvest.Command command : analysis.harvestLiterals(script).commands()) {
                collectFrom(command.name(), command.arguments(),
                        // A partial argv is a subset, so a path taken from one is a
                        // weaker claim than one taken from a complete argv. Both are
                        // unproven, but they are not equally unproven.
                        command.partial()
                                ? ShellPath.Confidence.HARVESTED_PARTIAL
                                : ShellPath.Confidence.HARVESTED,
                        paths);
            }
        }

        addRedirectDestinations(script, paths);
        return List.copyOf(paths);
    }

    /**
     * Selects the path arguments of one command.
     *
     * <p>Positions are counted over <b>non-flag</b> arguments, not over argv slots.
     * Counting slots would put {@code /etc/passwd} at position 2 in
     * {@code cat -n /etc/passwd} and therefore drop it entirely — losing the path
     * exactly when a flag is present, which is the common case. It would also
     * promote the <em>first</em> non-path argument to a path in commands that take
     * an option value ({@code chmod 777 /etc/shadow} reporting {@code 777}).</p>
     */
    private void collectFrom(String name, List<String> arguments,
                             ShellPath.Confidence confidence, List<ShellPath> paths) {
        int[] positions = ShellAnalysis.PATH_ARGUMENT_POSITIONS.get(name);
        boolean allArguments = ShellAnalysis.ALL_ARGUMENTS_ARE_PATHS.contains(name);

        // Build the list of true positional arguments: flags are dropped, and so is
        // a token shaped like a flag's value. Dropping both is what keeps the
        // positions aligned — keeping the value would leave a hole and push the
        // real path one slot too far (`head -n 5 /var/log/syslog`).
        List<String> positional = new ArrayList<>();
        boolean afterDoubleDash = false;
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (!afterDoubleDash && "--".equals(argument)) {
                afterDoubleDash = true;
                continue;
            }
            if (afterDoubleDash) {
                positional.add(argument);
                continue;
            }
            if (isFlag(argument)) {
                if (i + 1 < arguments.size() && looksLikeAFlagValue(arguments.get(i + 1))) {
                    i++;
                }
                continue;
            }
            if (looksLikeAFlagValue(argument)) {
                // A bare count or format specifier is never a path.
                continue;
            }
            positional.add(argument);
        }

        for (int i = 0; i < positional.size(); i++) {
            boolean selected = afterDoubleDash || allArguments
                    || (positions != null && contains(positions, i + 1));
            if (selected) {
                addIfPath(positional.get(i), ShellPath.Access.READ, confidence, paths);
            }
        }
    }

    /**
     * Redirect destinations are always <b>write</b> paths, kept in their own class
     * so a consumer can tell "this script writes here" from "this script reads here"
     * without re-deriving it from the source.
     */
    private void addRedirectDestinations(String script, List<ShellPath> paths) {
        io.nop.treesitter.TSTree tree = analysis.parse(script);
        if (tree == null) {
            return;
        }
        boolean proven = !tree.rootNode().hasError();
        ShellSource source = ShellAnalysis.sourceOf(tree);
        ShellAnalysis.walk(tree.rootNode(), node -> {
            if (!node.named() || !"file_redirect".equals(node.type())) {
                return true;
            }
            io.nop.treesitter.TSNode destination = node.childByFieldName("destination");
            if (destination == null) {
                return true;
            }
            addIfPath(source.textOf(destination), ShellPath.Access.WRITE,
                    proven ? ShellPath.Confidence.PROVEN : ShellPath.Confidence.HARVESTED, paths);
            return true;
        });
    }

    /**
     * Whether a token names a path literally.
     *
     * <p>A token may be wholly quoted ({@code "/etc/passwd"}), in which case the
     * quotes are stripped and the inner text judged — dropping quoted paths would
     * lose exactly the paths a careful script author writes. What is rejected: an
     * empty result, a leading {@code ~} (a tilde is an expansion, not a name), and
     * any glob or expansion character that survives unquoting.</p>
     *
     * <p>Flag exclusion is <b>not</b> applied here: it is a separate dimension,
     * decided where arguments are selected, because everything after a
     * {@code --} separator is a path even when it begins with a dash.</p>
     */
    static void addIfPath(String token, ShellPath.Access access,
                          ShellPath.Confidence confidence, List<ShellPath> paths) {
        String path = unquote(token);
        if (!isPathLiteral(path)) {
            return;
        }
        for (ShellPath existing : paths) {
            if (existing.path().equals(path) && existing.access() == access
                    && existing.confidence() == confidence) {
                return;
            }
        }
        paths.add(new ShellPath(path, access, confidence));
    }

    /**
     * Whether a token is shaped like an option's value rather than a filename: all
     * digits (a count or a mode), or a {@code %} format specifier.
     *
     * <p>Telling a flag's value from a genuine positional argument needs per-command
     * knowledge of which flags take values, which no bounded table can supply. The
     * shapes below cover the realistic cases ({@code head -n 5}, {@code stat -c %s},
     * {@code chmod 777}) at the cost of also dropping a genuinely numeric filename,
     * which is the right trade: a missed path is recoverable, a path that is not a
     * path is not.</p>
     */
    static boolean looksLikeAFlagValue(String token) {
        if (token.isEmpty()) {
            return false;
        }
        if (token.charAt(0) == '%') {
            return true;
        }
        boolean allDigits = true;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                allDigits = false;
                break;
            }
        }
        return allDigits;
    }

    /**
     * Strips one wholly-quoting pair. Anything else is returned unchanged, so a
     * fragment such as {@code pre"$X"} keeps its quotes and is then rejected by the
     * expansion-character rule rather than being silently trimmed into a path.
     */
    static String unquote(String token) {
        if (token == null || token.length() < 2) {
            return token;
        }
        char first = token.charAt(0);
        char last = token.charAt(token.length() - 1);
        if ((first == '"' || first == '\'') && first == last) {
            return token.substring(1, token.length() - 1);
        }
        return token;
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
