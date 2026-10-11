package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSTree;
import java.util.ArrayList;
import java.util.List;

/**
 * Capability 5: candidate literal file paths.
 *
 * <p>Six dimensions decide what counts as a path: the source command's
 * argument-role convention; the literal form; the confidence of the source; flag
 * exclusion; redirect direction; and no filesystem check.</p>
 *
 * <p>The confidence annotation is the part that carries the most weight. A path
 * taken from a proven script and a path taken from a harvest are different
 * claims, and a consumer that cannot tell them apart will read "this script
 * mentions /data somewhere" as "this script provably touches /data".</p>
 *
 * <p>No filesystem check is performed. This capability extracts literals; whether
 * a path exists is a different question, with a different cost and a different
 * failure mode, and answering it here would make the output depend on the
 * machine.</p>
 */
final class PathExtractor {

    private final ShellAnalysis analysis;

    PathExtractor(ShellAnalysis analysis) {
        this.analysis = analysis;
    }

    List<ShellPath> extract(String script) {
        List<ShellPath> paths = new ArrayList<>();

        // The confidence of every extracted path comes from the same decision the
        // whole extraction was based on. Deriving it from a weaker fact — "the tree
        // has no syntax errors" — would label paths PROVEN on scripts the proof
        // explicitly refused.
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
            addRedirectDestinations(script, ShellPath.Confidence.HARVESTED, paths);
            return List.copyOf(paths);
        }

        addRedirectDestinations(script, ShellPath.Confidence.PROVEN, paths);
        return List.copyOf(paths);
    }

    /**
     * Selects the path arguments of one command.
     *
     * <p>The model is per command <em>role</em>, not per argument position. Almost
     * every file-touching command takes only paths, so for those the rule is "every
     * non-flag argument". A positional table cannot express that safely: one flag
     * whose operand is itself a file ({@code grep -f &lt;patterns&gt;}) shifts every
     * position after it, which is how a search <em>pattern</em> ends up reported as a
     * file the script reads.</p>
     *
     * <p>The grep family and {@code chmod}/{@code chown} additionally require their
     * operands to <em>look like</em> a path, so a bare pattern is rejected wherever it
     * lands. Requiring a separator rather than a fixed slot is what keeps
     * {@code grep -f &lt;patterns&gt; pattern &lt;file&gt;} honest: the flag's operand is
     * itself a file and is kept, while the pattern is rejected.</p>
     */
    private void collectFrom(String name, List<String> arguments,
                             ShellPath.Confidence confidence, List<ShellPath> paths) {
        boolean allArgumentsArePaths = ShellAnalysis.ALL_ARGUMENTS_ARE_PATHS.contains(name);
        boolean leadingNonPath = ShellAnalysis.LEADING_NON_PATH_OPERAND.contains(name);
        if (!allArgumentsArePaths && !leadingNonPath) {
            return;
        }

        boolean afterDoubleDash = false;
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (!afterDoubleDash && "--".equals(argument)) {
                afterDoubleDash = true;
                continue;
            }
            if (afterDoubleDash) {
                addIfPath(argument, ShellPath.Access.READ, confidence, paths);
                continue;
            }
            if (isFlag(argument)) {
                // A flag whose operand is itself a file (`-f <patterns>`) or a
                // directory (`-C <dir>`) must not be dropped, and must not be
                // counted as a non-path operand either. It is left in place; the
                // `isPathLike` rule below is what keeps a pattern out.
                continue;
            }
            if (looksLikeAnOptionValue(argument)) {
                // `head -n 5 file`: the `5` is the value of `-n`. Telling an
                // option's value from a genuine operand needs per-command knowledge
                // of which flags take values, which no bounded rule can supply; the
                // shapes below are the realistic ones instead. The cost is that a
                // genuinely numeric filename is also dropped — the intended trade,
                // because a missed path is recoverable and a path that is not a path
                // is not.
                continue;
            }
            if (leadingNonPath && !isPathLike(argument)) {
                // The grep family's pattern and chmod/chown's mode look exactly like
                // a bare filename, so the separator rule is what rejects them —
                // whichever slot they land in, which is the whole point of not using
                // a positional table.
                continue;
            }
            addIfPath(argument, ShellPath.Access.READ, confidence, paths);
        }
    }

    /**
     * Redirect destinations, classified by the <b>direction</b> of the redirect
     * rather than by the node type.
     *
     * <p>{@code >} and {@code >>} write; {@code <} only ever reads. The operator is
     * an anonymous child of the {@code file_redirect}, so the direction is available
     * without re-parsing the text. A destination that is a bare file descriptor
     * ({@code 2>&1}) is not a path at all and is skipped.</p>
     */
    private void addRedirectDestinations(String script, ShellPath.Confidence confidence,
                                         List<ShellPath> paths) {
        TSTree tree = analysis.parse(script);
        if (tree == null) {
            return;
        }
        ShellSource source = ShellAnalysis.sourceOf(tree);
        ShellAnalysis.walk(tree.rootNode(), node -> {
            if (!node.named() || !"file_redirect".equals(node.type())) {
                return true;
            }
            TSNode destination = node.childByFieldName("destination");
            if (destination == null || "file_descriptor".equals(destination.type())
                    || "number".equals(destination.type())) {
                // `2>&1` and friends name a descriptor, not a file.
                return true;
            }
            ShellPath.Access access = readsFrom(node) ? ShellPath.Access.READ : ShellPath.Access.WRITE;
            addIfPath(source.textOf(destination), access, confidence, paths);
            return true;
        });
    }

    /** Whether the redirect reads from its destination rather than writing to it. */
    private static boolean readsFrom(TSNode fileRedirect) {
        int childCount = fileRedirect.childCount();
        for (int i = 0; i < childCount; i++) {
            TSNode child = ShellAnalysis.childOrFail(fileRedirect, i);
            if (child.named()) {
                continue;
            }
            return child.type().startsWith("<");
        }
        return false;
    }

    /**
     * Whether a token names a path literally, on form grounds only.
     *
     * <p>Excluded: an empty result, a leading {@code ~} (a tilde is an expansion,
     * not a name), any glob or expansion character, and any whitespace or shell
     * operator. The last group matters because a quoted token is unquoted before
     * this check: {@code cat '/a; rm -rf /b'} is one argument, and reporting it as a
     * path would put a command separator inside a filename.</p>
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
     * Strips one wholly-quoting pair, and applies the flag rule to the result: a
     * quoted flag is still a flag, so {@code rm "-rf"} must not report {@code -rf}
     * as a path.
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

    private static boolean isFlag(String token) {
        String bare = unquote(token);
        return bare.length() > 1 && bare.charAt(0) == '-';
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
                    || c == '$' || c == '`' || c == '\\' || c == '"' || c == '\''
                    || c == ' ' || c == '\t' || c == '\n' || Character.isWhitespace(c)
                    || c == ';' || c == '|' || c == '&' || c == '(' || c == ')'
                    || c == '<' || c == '>' || c == '#') {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a token has the shape of a filesystem path rather than a bare word.
     *
     * <p>Used for the commands whose operands include a non-file (the grep family,
     * {@code chmod}/{@code chown}), where "all non-flag operands are paths" is not
     * true. Requiring a separator is the cheapest rule that rejects a search pattern
     * regardless of which slot it lands in.</p>
     */
    /**
     * Whether a token is shaped like an option's value rather than a file: all
     * digits (a count or a mode) or a {@code %} format specifier.
     */
    static boolean looksLikeAnOptionValue(String token) {
        String bare = unquote(token);
        if (bare.isEmpty()) {
            return false;
        }
        if (bare.charAt(0) == '%') {
            return true;
        }
        for (int i = 0; i < bare.length(); i++) {
            if (!Character.isDigit(bare.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a token has the shape of a filesystem path rather than a bare word.
     *
     * <p>Used only for the commands whose operands include a non-file (the grep
     * family, {@code chmod}/{@code chown}), where "every non-flag argument is a
     * path" is not true. Requiring a separator is the cheapest rule that rejects a
     * search pattern regardless of which slot it lands in — which is what a
     * positional table could not do, because a flag whose operand is itself a file
     * shifts every slot after it.</p>
     *
     * <p><b>Accepted collateral:</b> a <em>bare filename</em> operand of these
     * commands is not reported, because a bare filename is indistinguishable from a
     * bare pattern. {@code grep foo README.md} therefore reports nothing while
     * {@code grep foo src/} reports {@code src/}. The direction is safe — a
     * missed path, not an invented one — and {@code ShellPathExtractionTest} pins
     * it so that it stays a decision rather than an accident.</p>
     */
    static boolean isPathLike(String token) {
        String bare = unquote(token);
        return bare.indexOf('/') >= 0 || bare.startsWith(".");
    }
}