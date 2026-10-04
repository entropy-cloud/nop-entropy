package io.nop.treesitter.shell;

import io.nop.treesitter.TSNode;
import io.nop.treesitter.TSParser;
import io.nop.treesitter.TSTree;
import io.nop.treesitter.language.Language;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The shell analysis semantic layer: six capabilities over an arbitrary shell
 * script, for an agent that is about to run it.
 *
 * <p>What this layer is for is <b>narrowing what an agent has to reason about</b>.
 * It produces structural facts and, for one of its six capabilities, a proof. It
 * does not decide whether a command is dangerous, and it contains nothing that
 * executes, expands or resolves anything.</p>
 *
 * <p>The load-bearing property is that a caller can always tell which of the two
 * situations it is in. {@link #splitProven} either proves the whole script or
 * refuses with a closed reason; the refusal type has no accessor that yields
 * commands, because "no commands" and "I could not prove what this does" must
 * never look alike. The other capabilities are explicitly incomplete and say so in
 * their own types.</p>
 *
 * <p>Every capability parses through the primary {@link TSParser} API — never
 * through the compat layer, so nothing here depends on the migration shim. The
 * capabilities do <b>not</b> share a single parse: each takes one of its own, so
 * that a caller can ask one question without paying for five others. The cost is
 * measured rather than assumed (six parses per {@link #summarize}); a shared parse
 * is a caching change, not a correctness one.</p>
 *
 * <p>Instances are immutable and safe to share; the underlying
 * {@link io.nop.treesitter.language.Language} is shared and its external scanner
 * is created per parse.</p>
 */
public final class ShellAnalysis {

    /**
     * Commands whose non-flag arguments are <b>all</b> file paths.
     *
     * <p>Almost every command that touches files belongs here, because a
     * positional table is the wrong shape for them: a table has to encode each
     * command's argument positions, and one flag whose operand is itself a file
     * ({@code grep -f <patterns>}) shifts every position after it. "All of them
     * are paths" cannot be shifted by a flag.</p>
     */
    static final Set<String> ALL_ARGUMENTS_ARE_PATHS = Set.of(
            "cat", "less", "more", "head", "tail", "find", "ls", "stat", "wc", "file",
            "readlink", "realpath", "cd", "chdir", "pushd", "popd", "cp", "mv", "ln",
            "diff", "rm", "rmdir", "mkdir", "touch", "unlink", "tee", "tr", "dd",
            "install", "tar");

    /**
     * Commands whose <b>first</b> non-flag operand is not a file: the grep family
     * takes a pattern, {@code chmod} and {@code chown} take a mode or an owner.
     *
     * <p>A second, complementary rule applies to these — their operands must
     * <em>look like</em> a path (see {@link PathExtractor#isPathLike}). A bare
     * pattern like {@code secret} has no separator and is therefore not a file,
     * whatever slot it happens to land in.</p>
     */
    static final Set<String> LEADING_NON_PATH_OPERAND = Set.of(
            "grep", "egrep", "fgrep", "rg", "chmod", "chown");

    private final Language language;

    /**
     * @param language the bash language; the caller is responsible for having
     *                 wired its external scanner (both the compat adapter and the
     *                 built-in provider do)
     */
    public ShellAnalysis(Language language) {
        this.language = language;
    }

    // ---- capability 1: proven split ----------------------------------------

    /**
     * Splits a script into a proven literal command sequence, or refuses.
     *
     * <p>The proof requires the whole tree to be free of syntax errors, every node
     * to be in the closed whitelist, and every argument to pass the lexical rules.
     * Anything else is a refusal carrying the byte offset and node type at which
     * the decision was made.</p>
     */
    public ProvenSplit splitProven(String script) {
        return new ProvenSplitter(language).split(script);
    }

    // ---- capability 2: literal harvest -------------------------------------

    /**
     * Collects every command in the script whose name and arguments are literal,
     * descending through control flow, pipelines and substitutions.
     *
     * <p>Requires a syntax-error-free tree as well: a tree containing errors is not
     * a tree whose commands can be enumerated at all. Incomplete by construction —
     * see {@link LiteralHarvest}.</p>
     */
    public LiteralHarvest harvestLiterals(String script) {
        return new LiteralHarvester(language).harvest(script);
    }

    // ---- capability 3: heredoc extraction ----------------------------------

    /**
     * Extracts the heredoc body of a script that is nothing but a heredoc
     * statement (optionally preceded by a {@code cd ... &&}).
     *
     * @param allowedCommandNames the command names accepted after a {@code cd}
     *                            prefix; when empty only the direct form matches
     */
    public HeredocScript.Lookup extractHeredoc(String script, Set<String> allowedCommandNames) {
        return new HeredocExtractor(language).extract(script, allowedCommandNames);
    }

    // ---- capability 4: dynamic construction points -------------------------

    /**
     * Locates every construct whose value is computed at run time, with byte
     * ranges. Nothing is evaluated.
     */
    public List<DynamicConstruct> findDynamicConstructs(String script) {
        return new DynamicConstructMarker(language).mark(script);
    }

    // ---- capability 5: literal path extraction -----------------------------

    /**
     * Extracts candidate literal file paths, each annotated with read/write and
     * with whether it came from a proof or from a harvest.
     *
     * <p>Performs <b>no</b> filesystem check: this extracts literals, and whether a
     * path exists is a different question with a different cost.</p>
     */
    public List<ShellPath> extractPaths(String script) {
        return new PathExtractor(language, this).extract(script);
    }

    // ---- capability 6: structured summary ----------------------------------

    /**
     * One record with all six capabilities' findings, including the proof status
     * as a field. Never degrades to returning the original text in place of an
     * analysis.
     */
    public ShellSummary summarize(String script) {
        return new SummaryBuilder(language, this).summarize(script);
    }

    // ---- shared plumbing ---------------------------------------------------

    /**
     * Parses a script, or returns null when the tree cannot be produced. Callers
     * that must not silently continue treat null as "nothing found": the script
     * had no command to parse. A parse that fails outright raises rather than
     * returning null.
     */
    TSTree parse(String script) {
        if (script == null || script.isEmpty()) {
            return null;
        }
        return TSParser.parse(language, script.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The tree's own source bytes, wrapped so that byte offsets can be sliced
     * correctly. See {@link ShellSource} for why this is not a plain
     * {@code String}.
     */
    static ShellSource sourceOf(TSTree tree) {
        byte[] source = tree.source();
        return new ShellSource(source);
    }

    /**
     * Depth-first pre-order walk over every visible node, named and anonymous.
     *
     * <p>Child iteration goes through {@link TSNode#child(int)} rather than a
     * {@link TSTreeCursor} rooted at a non-root node. A cursor reproduces the C
     * runtime's sibling motion, which bubbles past the starting node's parent when
     * the node is the last child — correct when the caller only ever descends from
     * the tree root, wrong when it is resumed from a subtree, and silent when it is
     * wrong. Index-based iteration has no such motion to get wrong.</p>
     */
    static void walk(TSNode root, NodeVisitor visitor) {
        walkFrom(root, visitor);
    }

    private static void walkFrom(TSNode node, NodeVisitor visitor) {
        if (!visitor.visit(node)) {
            return;
        }
        int count = node.childCount();
        for (int i = 0; i < count; i++) {
            walkFrom(childOrFail(node, i), visitor);
        }
    }

    /**
     * The {@code index}-th visible child, or a hard failure. A child that cannot be
     * materialised means the tree and the cursor disagree, which is a bug worth
     * surfacing rather than a gap worth skipping.
     */
    static TSNode childOrFail(TSNode node, int index) {
        TSNode child = node.child(index);
        if (child == null) {
            throw new IllegalStateException("node " + node.type() + " at byte " + node.startByte()
                    + " reports " + node.childCount() + " children but child " + index
                    + " could not be materialised");
        }
        return child;
    }

    /**
     * The named children of a node with the given type, in source order.
     */
    static List<TSNode> namedChildrenOfType(TSNode node, String type) {
        List<TSNode> found = new ArrayList<>();
        int count = node.namedChildCount();
        for (int i = 0; i < count; i++) {
            TSNode child = node.namedChild(i);
            if (child != null && type.equals(child.type())) {
                found.add(child);
            }
        }
        return found;
    }

    /** A node visitor that can prune the walk by returning {@code false}. */
    interface NodeVisitor {
        boolean visit(TSNode node);
    }

}
