package io.nop.lint.core.engine;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.AllMatcher;
import io.nop.lint.core.pattern.KindNodeMatcher;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.NodeMatcher;
import io.nop.lint.core.pattern.NotMatcher;
import io.nop.lint.core.pattern.PatternNodeMatcher;
import io.nop.lint.core.pattern.RelationalMatcher;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
import io.nop.lint.core.pattern.StopBy;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.xscript.XScriptCompiler;
import io.nop.lint.core.xscript.XScriptEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The compiled form of one {@link RuleDslModel}: identity fields, the
 * executable matcher body, and the precomputed set of node kinds the rule
 * can possibly match — the rule-level O(1) filter input (design 03 §1.3,
 * design 11 §3). Compilation is profile-independent: one compile, all
 * profiles reuse (design 11 §1, 口径表 01 §4).
 *
 * <p>Form execution matrix (compile time, fail-closed): pattern rules
 * execute; kind rules execute (whole-tree kind traversal); {@code any}
 * rules execute branch by branch (several matcher fields on one branch form
 * a conjunction); {@code all}/{@code not}/relational rules execute as a
 * node-matcher tree over a whole-tree pre-order scan, with the kind
 * contribution defined per design 01 §4 step 5 (pattern → its possible
 * kinds, kind → singleton, relational/not → no opinion, all → conservative
 * intersection of non-empty opinions). Rules carrying {@code xscript}
 * compile the script through the {@link XScriptCompiler} whitelist — the
 * matcher produces the matches, the script runs per match and decides what
 * gets reported (roadmap item 14). Regex matchers are still rejected here —
 * regex semantics belong to the constraint evaluator (roadmap item 22) —
 * and {@code stopByRule} util references are rejected until the utils
 * registry lands (roadmap item 24), so an unsupported form fails loudly
 * with the rule id instead of being skipped silently.</p>
 */
public final class CompiledRule {

    /**
     * The executable matcher body: every tree match this rule reports on,
     * in match order (a branch's matches precede the next branch's), each
     * carrying its capture environment.
     */
    @FunctionalInterface
    interface RuleMatcher {
        List<Match> match(LintTree tree);
    }

    private final String ruleId;
    private final String severity;
    private final String message;
    private final int[] targetKindIds;
    private final RuleMatcher matcher;
    private final XScriptEngine xscriptEngine;
    private final int xscriptTimeoutMs;

    private CompiledRule(String ruleId, String severity, String message,
                         TreeSet<Integer> targetKindIds, RuleMatcher matcher, XScriptEngine xscriptEngine,
                         int xscriptTimeoutMs) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.targetKindIds = targetKindIds.stream().mapToInt(Integer::intValue).toArray();
        this.matcher = matcher;
        this.xscriptEngine = xscriptEngine;
        this.xscriptTimeoutMs = xscriptTimeoutMs;
    }

    /**
     * Compiles a rule model for {@code language}.
     *
     * @throws NopLintException when the rule uses a regex matcher (container
     *                          or {@code any} branch), carries an xscript
     *                          body that violates the compile-time whitelist,
     *                          references a kind unknown to the language, or
     *                          has no matcher — every rejection names the
     *                          rule id and the reason
     */
    public static CompiledRule compile(RuleDslModel model, LintLanguage language) {
        if (model == null) {
            throw new NopLintException("rule model must not be null");
        }
        if (language == null) {
            throw new NopLintException("Rule '" + model.getId() + "' cannot compile: language binding is null");
        }
        XScriptEngine xscriptEngine = null;
        if (model.getSeverity() == null || model.getMessage() == null) {
            throw new NopLintException("Rule '" + model.getId() + "' cannot compile: "
                    + (model.getSeverity() == null ? "'severity'" : "'message'")
                    + " must not be blank (diagnostics must be attributable)");
        }
        if (model.getXscript() != null) {
            xscriptEngine = new XScriptEngine(model.getId(), model.getSeverity(),
                    XScriptCompiler.compile(model.getId(), model.getXscript()));
        }
        RuleDslModel.Matcher matcher = model.getMatcher();
        if (matcher == null) {
            throw new NopLintException("Rule '" + model.getId() + "' has no matcher");
        }
        if (matcher.getRegex() != null) {
            throw regexRejected(model.getId(), "rule container");
        }

        TreeSet<Integer> targets = new TreeSet<>();
        if (matcher.getPattern() != null) {
            SourcePattern pattern = compilePattern(model.getId(), matcher.getPattern(), language);
            addPatternTargets(targets, pattern, -1);
            return new CompiledRule(model.getId(), model.getSeverity(), model.getMessage(), targets,
                    tree -> pattern.matchIn(tree.root()), xscriptEngine, model.getXscriptTimeoutMs());
        }
        if (matcher.getKind() != null) {
            int kindId = resolveKind(model.getId(), language, matcher.getKind());
            targets.add(kindId);
            return new CompiledRule(model.getId(), model.getSeverity(), model.getMessage(), targets,
                    tree -> nodesOfKind(tree.root(), kindId), xscriptEngine, model.getXscriptTimeoutMs());
        }

        // Composite forms (all/not/relational): a node-matcher tree over a
        // whole-tree scan, with the kind opinion per design 01 §4 step 5.
        if (matcher.getAll() != null || matcher.getNot() != null
                || matcher.getInside() != null || matcher.getHas() != null
                || matcher.getFollows() != null || matcher.getPrecedes() != null) {
            NodeMatcher nodeMatcher = compileNodeMatcher(model.getId(), matcher, language, 0, "rule container");
            int[] opinion = kindOpinion(model.getId(), matcher, language);
            for (int kindId : opinion) {
                targets.add(kindId);
            }
            final int[] filterKinds = opinion;
            return new CompiledRule(model.getId(), model.getSeverity(), model.getMessage(), targets,
                    tree -> scanTree(tree, nodeMatcher, filterKinds),
                    xscriptEngine, model.getXscriptTimeoutMs());
        }

        List<RuleDslModel.Branch> branches = matcher.getAny();
        if (branches == null || branches.isEmpty()) {
            throw new NopLintException("Rule '" + model.getId()
                    + "' has no matcher (neither pattern/kind/regex nor any branches)");
        }
        List<RuleMatcher> branchMatchers = new ArrayList<>(branches.size());
        int index = 0;
        for (RuleDslModel.Branch branch : branches) {
            index++;
            if (branch.getRegex() != null) {
                throw regexRejected(model.getId(), "'any' branch #" + index);
            }
            boolean hasKind = branch.getKind() != null;
            int branchKindId = hasKind ? resolveKind(model.getId(), language, branch.getKind()) : -1;
            if (branch.getPattern() != null) {
                SourcePattern pattern = compilePattern(model.getId(), branch.getPattern(), language);
                addPatternTargets(targets, pattern, branchKindId);
                final boolean conjunctive = hasKind;
                final int requiredKindId = branchKindId;
                branchMatchers.add(tree -> filterByKind(pattern.matchIn(tree.root()),
                        conjunctive, requiredKindId));
            } else if (hasKind) {
                targets.add(branchKindId);
                branchMatchers.add(tree -> nodesOfKind(tree.root(), branchKindId));
            } else {
                throw new NopLintException("Rule '" + model.getId() + "' declares an empty 'any' branch #"
                        + index + " (at least one of pattern|kind|regex is required)");
            }
        }
        List<RuleMatcher> branchChain = List.copyOf(branchMatchers);
        return new CompiledRule(model.getId(), model.getSeverity(), model.getMessage(), targets,
                tree -> {
                    List<Match> all = new ArrayList<>();
                    for (RuleMatcher branchMatcher : branchChain) {
                        all.addAll(branchMatcher.match(tree));
                    }
                    return all;
                }, xscriptEngine, model.getXscriptTimeoutMs());
    }

    private static NopLintException regexRejected(String ruleId, String location) {
        return new NopLintException("Rule '" + ruleId + "' uses a 'regex' matcher (" + location
                + "), which this engine version does not execute (deferred to roadmap item 22); the rule "
                + "is rejected at compile time instead of being skipped silently");
    }

    // ==================== composite forms (all / not / relational) ====================

    /**
     * The kind opinion of one matcher object (design 01 §4 step 5): a
     * pattern contributes its possible root kinds, a kind conjunct its
     * singleton, relational and not matchers contribute no opinion (empty),
     * and an all matcher contributes the conservative intersection of its
     * children's non-empty opinions. An empty return means no opinion — the
     * kind filter must not exclude the rule.
     */
    private static int[] kindOpinion(String ruleId, RuleDslModel.Matcher matcher, LintLanguage language) {
        if (matcher.getPattern() != null) {
            return compilePattern(ruleId, matcher.getPattern(), language).possibleKindIds();
        }
        if (matcher.getKind() != null) {
            return new int[]{resolveKind(ruleId, language, matcher.getKind())};
        }
        if (matcher.getNot() != null || matcher.getInside() != null || matcher.getHas() != null
                || matcher.getFollows() != null || matcher.getPrecedes() != null) {
            return new int[0];
        }
        List<RuleDslModel.Matcher> all = matcher.getAll();
        TreeSet<Integer> intersection = null;
        for (RuleDslModel.Matcher element : all) {
            int[] elementOpinion = kindOpinion(ruleId, element, language);
            if (elementOpinion.length == 0) {
                continue;
            }
            if (intersection == null) {
                intersection = new TreeSet<>();
                for (int kindId : elementOpinion) {
                    intersection.add(kindId);
                }
            } else {
                intersection.retainAll(toSet(elementOpinion));
            }
        }
        return intersection == null ? new int[0] : intersection.stream().mapToInt(Integer::intValue).toArray();
    }

    private static TreeSet<Integer> toSet(int[] values) {
        TreeSet<Integer> set = new TreeSet<>();
        for (int value : values) {
            set.add(value);
        }
        return set;
    }

    /**
     * Compiles one matcher object into the node-matcher tree. Depth guards
     * mirror the parser's bounded surface: the container is depth 0, all
     * elements depth 1, and an all element's {@code not} inner depth 2;
     * {@code any} below the container is item 24 surface and rejected.
     */
    private static NodeMatcher compileNodeMatcher(String ruleId, RuleDslModel.Matcher matcher,
                                                  LintLanguage language, int depth, String location) {
        if (matcher.getAny() != null) {
            throw new NopLintException("Rule '" + ruleId + "' declares 'any' inside " + location
                    + " (any-nesting refinement is roadmap item 24; fail-closed)");
        }
        if (matcher.getAll() != null) {
            if (depth >= 1) {
                throw new NopLintException("Rule '" + ruleId + "' declares 'all' inside " + location
                        + " (nested composites beyond all-element 'not' are out of the supported "
                        + "surface; fail-closed)");
            }
            List<NodeMatcher> children = new ArrayList<>(matcher.getAll().size());
            int index = 0;
            for (RuleDslModel.Matcher element : matcher.getAll()) {
                index++;
                children.add(compileNodeMatcher(ruleId, element, language, depth + 1,
                        "all element #" + index));
            }
            return new AllMatcher(children);
        }
        if (matcher.getNot() != null) {
            if (depth >= 2) {
                throw new NopLintException("Rule '" + ruleId + "' declares 'not' inside " + location
                        + " (negation nesting beyond an all-element 'not' is out of the supported "
                        + "surface; fail-closed)");
            }
            return new NotMatcher(compileNodeMatcher(ruleId, matcher.getNot(), language, depth + 1,
                    location + " 'not'"));
        }
        if (matcher.getInside() != null) {
            return new RelationalMatcher(RelationalMatcher.Op.INSIDE,
                    relationalInner(ruleId, matcher.getInside(), language),
                    relationalStopBy(ruleId, matcher.getInside()), matcher.getInside().getField());
        }
        if (matcher.getHas() != null) {
            return new RelationalMatcher(RelationalMatcher.Op.HAS,
                    relationalInner(ruleId, matcher.getHas(), language),
                    relationalStopBy(ruleId, matcher.getHas()), matcher.getHas().getField());
        }
        if (matcher.getFollows() != null) {
            return new RelationalMatcher(RelationalMatcher.Op.FOLLOWS,
                    relationalInner(ruleId, matcher.getFollows(), language),
                    relationalStopBy(ruleId, matcher.getFollows()), matcher.getFollows().getField());
        }
        if (matcher.getPrecedes() != null) {
            return new RelationalMatcher(RelationalMatcher.Op.PRECEDES,
                    relationalInner(ruleId, matcher.getPrecedes(), language),
                    relationalStopBy(ruleId, matcher.getPrecedes()), matcher.getPrecedes().getField());
        }
        if (matcher.getPattern() != null) {
            return new PatternNodeMatcher(compilePattern(ruleId, matcher.getPattern(), language));
        }
        if (matcher.getKind() != null) {
            return new KindNodeMatcher(resolveKind(ruleId, language, matcher.getKind()));
        }
        throw regexRejected(ruleId, location);
    }

    private static NodeMatcher relationalInner(String ruleId, RuleDslModel.Relational relational,
                                               LintLanguage language) {
        if (relational.getContext() != null) {
            try {
                return new PatternNodeMatcher(SourcePatternCompiler.contextual(
                        relational.getSelector(), relational.getContext(), language));
            } catch (NopLintException e) {
                throw new NopLintException("Rule '" + ruleId + "' has an invalid contextual pattern: "
                        + e.getMessage(), e);
            }
        }
        return new PatternNodeMatcher(compilePattern(ruleId, relational.getPattern(), language));
    }

    private static StopBy relationalStopBy(String ruleId, RuleDslModel.Relational relational) {
        switch (relational.getStopBy()) {
            case "neighbor":
                return StopBy.neighbor();
            case "rule":
                throw new NopLintException("Rule '" + ruleId + "' uses stopBy=rule with stopByRule '"
                        + relational.getStopByRule() + "', but the utils rule registry is not available "
                        + "until roadmap item 24; the rule is rejected at compile time instead of "
                        + "degrading to another horizon (fail-closed)");
            default:
                return StopBy.end();
        }
    }

    /**
     * The composite rule's match body: a pre-order scan over the whole tree,
     * with the kind opinion applied as an O(1) candidate pre-filter (empty
     * opinion = no filtering, never a fake restriction).
     */
    private static List<Match> scanTree(LintTree tree, NodeMatcher nodeMatcher, int[] filterKinds) {
        List<Match> matches = new ArrayList<>();
        for (LintNode node : tree.root()) {
            if (filterKinds.length > 0 && !contains(filterKinds, node.kindId())) {
                continue;
            }
            MetaVarEnv env = new MetaVarEnv();
            if (nodeMatcher.matches(node, env)) {
                matches.add(new Match(node, env));
            }
        }
        return matches;
    }

    private static boolean contains(int[] values, int needle) {
        for (int value : values) {
            if (value == needle) {
                return true;
            }
        }
        return false;
    }

    private static SourcePattern compilePattern(String ruleId, String patternText, LintLanguage language) {
        try {
            return SourcePatternCompiler.compile(patternText, language);
        } catch (NopLintException e) {
            throw new NopLintException("Rule '" + ruleId + "' has an invalid pattern: " + e.getMessage(), e);
        }
    }

    /**
     * Contributes one pattern's root kinds to the rule's target set. A kind
     * conjunct on the same branch narrows the contribution to the
     * intersection; when the pattern root has no kind opinion (meta-var
     * root), the conjunct kind alone is the contribution.
     */
    private static void addPatternTargets(TreeSet<Integer> targets, SourcePattern pattern, int conjunctKindId) {
        int[] patternKinds = pattern.possibleKindIds();
        if (patternKinds.length == 0) {
            if (conjunctKindId >= 0) {
                targets.add(conjunctKindId);
            }
            return;
        }
        for (int patternKind : patternKinds) {
            if (conjunctKindId < 0 || conjunctKindId == patternKind) {
                targets.add(patternKind);
            }
        }
    }

    private static int resolveKind(String ruleId, LintLanguage language, String kindName) {
        int kindId = language.kindId(kindName);
        if (kindId < 0) {
            throw new NopLintException("Rule '" + ruleId + "' references kind '" + kindName
                    + "', which is unknown to language '" + language.id() + "'");
        }
        return kindId;
    }

    private static List<Match> filterByKind(List<Match> matches, boolean conjunctive, int kindId) {
        if (!conjunctive) {
            return matches;
        }
        List<Match> kept = new ArrayList<>(matches.size());
        for (Match match : matches) {
            if (match.node().kindId() == kindId) {
                kept.add(match);
            }
        }
        return kept;
    }

    private static List<Match> nodesOfKind(LintNode root, int kindId) {
        List<Match> matches = new ArrayList<>();
        for (LintNode node : root) {
            if (node.kindId() == kindId) {
                matches.add(new Match(node, new MetaVarEnv()));
            }
        }
        return matches;
    }

    /**
     * The rule id, as declared in the DSL.
     */
    public String ruleId() {
        return ruleId;
    }

    /**
     * The severity emitted on this rule's diagnostics.
     */
    public String severity() {
        return severity;
    }

    /**
     * The message emitted on this rule's diagnostics (v1 emits the model's
     * message literally; template interpolation is item 11's decision).
     */
    public String message() {
        return message;
    }

    /**
     * The sorted, distinct kind ids the rule can possibly match ({@code any}
     * branches contribute their union). Empty means the rule has no kind
     * opinion — a meta-var root pattern can match any named node — so the
     * kind filter must not exclude it.
     */
    public int[] targetKindIds() {
        return targetKindIds.clone();
    }

    /**
     * Rule-level kind filter: true when the rule may match a file containing
     * {@code occurringKinds} (or when the rule has no kind opinion). A false
     * return lets the engine skip the matcher entirely.
     */
    public boolean canMatchKinds(Iterable<Integer> occurringKinds) {
        if (targetKindIds.length == 0) {
            return true;
        }
        for (int occurring : occurringKinds) {
            for (int target : targetKindIds) {
                if (target == occurring) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Runs the matcher body against the tree: every match this rule reports
     * on, in match order, each with its capture environment. Xscript rules
     * execute their script per match via {@link #xscriptEngine()}; rules
     * without xscript report one diagnostic per match.
     */
    public List<Match> matchWithCaptures(LintTree tree) {
        return matcher.match(tree);
    }

    /**
     * The match nodes only — the subset of {@link #matchWithCaptures(LintTree)}
     * the plain (non-xscript) pipeline consumes.
     */
    public List<LintNode> match(LintTree tree) {
        List<LintNode> nodes = new ArrayList<>();
        for (Match match : matchWithCaptures(tree)) {
            nodes.add(match.node());
        }
        return nodes;
    }

    /**
     * The xscript executor for this rule, or null when the rule carries no
     * {@code xscript} body.
     */
    public XScriptEngine xscriptEngine() {
        return xscriptEngine;
    }

    /**
     * The rule's declared per-match xscript budget in milliseconds
     * (profile-scaled by {@link LintProfile#xscriptBudgetMs(int)} at run
     * time, since compilation is profile-independent).
     */
    public int xscriptTimeoutMs() {
        return xscriptTimeoutMs;
    }
}
