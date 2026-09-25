package io.nop.lint.core.engine;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.constraint.Constraint;
import io.nop.lint.core.fix.TemplateFix;
import io.nop.lint.core.constraint.Constraints;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.AllMatcher;
import io.nop.lint.core.pattern.AnyMatcher;
import io.nop.lint.core.pattern.KindNodeMatcher;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.NodeMatcher;
import io.nop.lint.core.pattern.NotMatcher;
import io.nop.lint.core.pattern.PatternNodeMatcher;
import io.nop.lint.core.pattern.ReferentMatcher;
import io.nop.lint.core.pattern.RelationalMatcher;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
import io.nop.lint.core.pattern.StopBy;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.xscript.XScriptCompiler;
import io.nop.lint.core.xscript.XScriptEngine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

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
 *
 * <p>Constraints (roadmap item 22): the rule's parsed constraints compile
 * into executable predicates whose capture references are validated against
 * the matcher's declared capture set at compile time — an undeclared or
 * sequence-capture reference is a {@link NopLintException} naming the rule
 * id, the constraint kind, and the capture. A rule whose language binding
 * compiles on its own substrate (the XML path) may not carry constraints:
 * the rejection is explicit, never a silent bypass of the filter.</p>
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
    private final List<Constraint> constraints;
    private final TemplateFix templateFix;
    private final String fixDescription;
    private final boolean fixSuggestOnly;
    private final Set<LintCapability> requires;
    private final Set<String> rawRequires;

    private CompiledRule(String ruleId, String severity, String message,
                         TreeSet<Integer> targetKindIds, RuleMatcher matcher, XScriptEngine xscriptEngine,
                         int xscriptTimeoutMs, List<Constraint> constraints, TemplateFix templateFix,
                         String fixDescription, boolean fixSuggestOnly, Set<LintCapability> requires,
                         Set<String> rawRequires) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.targetKindIds = targetKindIds.stream().mapToInt(Integer::intValue).toArray();
        this.matcher = matcher;
        this.xscriptEngine = xscriptEngine;
        this.xscriptTimeoutMs = xscriptTimeoutMs;
        this.constraints = List.copyOf(constraints);
        this.templateFix = templateFix;
        this.fixDescription = fixDescription;
        this.fixSuggestOnly = fixSuggestOnly;
        this.requires = Set.copyOf(requires);
        this.rawRequires = Set.copyOf(rawRequires);
    }

    /**
     * The compiled autofix template of this rule (roadmap item 25), or null
     * when the rule declares none. Rendered per match in the runner; a
     * suggestion-only template never reaches the applier.
     */
    public TemplateFix templateFix() {
        return templateFix;
    }

    /**
     * The fix description for suggestion reporting; null without a fix.
     */
    public String fixDescription() {
        return fixDescription;
    }

    /**
     * Whether this rule's fix is suggestion-only (reported, never applied).
     */
    public boolean fixSuggestOnly() {
        return fixSuggestOnly;
    }

    /**
     * The compiled per-match constraints of this rule (design 01 §3.2);
     * empty when the rule declares none. A match is reported only when every
     * constraint holds — see {@code RuleSetRunner}.
     */
    public List<Constraint> constraints() {
        return constraints;
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
    /**
     * Compiles a rule model for {@code language}. The compiled form is
     * file- and run-independent by construction (plan 10 compile-reuse): L2
     * query support reaches a typeOf constraint through the evaluation
     * context at run time, never through a compile-time capture, so one
     * compiled rule serves every file of a run.
     *
     * @throws NopLintException when the rule uses a regex matcher, carries
     *                          an xscript body violating the whitelist,
     *                          references an unknown kind, or has no matcher
     */
    public static CompiledRule compile(RuleDslModel model, LintLanguage language) {
        if (model == null) {
            throw new NopLintException("rule model must not be null");
        }
        if (language == null) {
            throw new NopLintException("Rule '" + model.getId() + "' cannot compile: language binding is null");
        }
        CompiledRule external = language.compileRule(model);
        if (external != null) {
            // The binding compiles on its own pattern substrate (the XNode
            // XML path); the engine consumes the precompiled rule through the
            // identical downstream pipeline. Constraints need the tree-sitter
            // capture machinery, so a constrained XML rule is rejected here
            // instead of riding the pipeline with an unenforced filter.
            if (model.getFix() != null) {
                throw new NopLintException("Rule '" + model.getId() + "' declares a 'fix' on the '"
                        + language.id() + "' language path, whose compiler provides no match "
                        + "environments to render templates with; the autofix surface is "
                        + "supported on tree-sitter language rules only (fail-closed, never a "
                        + "silently dead template)");
            }
            if (!model.getConstraints().isEmpty()) {
                throw new NopLintException("Rule '" + model.getId() + "' declares constraints on the "
                        + "'" + language.id() + "' language path, whose compiler does not provide "
                        + "the capture set constraint validation needs; constraints are currently "
                        + "supported on tree-sitter language rules only (fail-closed, never an "
                        + "unenforced filter)");
            }
            return external;
        }
        return compileTreeSitter(model, language);
    }

    /**
     * Assembles a rule compiled by a language binding's own compiler (the
     * XML XNode path): identity fields, the precomputed target kinds, and
     * the matcher body over the facade tree. The xscript engine, when the
     * binding accepted one, rides the same per-match execution semantics.
     * Binding-compiled rules carry no constraints ({@link #compile} rejects
     * constrained models on that path before reaching here). The raw
     * {@code requires} tokens ride along so the runner can re-judge them at
     * rule boundaries against an engaged degrade ladder (roadmap item 31).
     */
    public static CompiledRule precompiled(String ruleId, String severity, String message,
                                           Iterable<Integer> targetKindIds,
                                           Function<LintTree, List<Match>> matcher,
                                           XScriptEngine xscriptEngine, int xscriptTimeoutMs,
                                           Set<String> requires) {
        TreeSet<Integer> targets = new TreeSet<>();
        for (int kindId : targetKindIds) {
            targets.add(kindId);
        }
        return new CompiledRule(ruleId, severity, message, targets, matcher::apply,
                xscriptEngine, xscriptTimeoutMs, List.of(), null, null, false,
                resolveRequires(requires), requires);
    }

    private static CompiledRule compileTreeSitter(RuleDslModel model, LintLanguage language) {
        XScriptEngine xscriptEngine = null;
        if (model.getSeverity() == null || model.getMessage() == null) {
            throw new NopLintException("Rule '" + model.getId() + "' cannot compile: "
                    + (model.getSeverity() == null ? "'severity'" : "'message'")
                    + " must not be blank (diagnostics must be attributable)");
        }
        if (model.getXscript() != null) {
            // the requires-gated whitelist variant (roadmap items 32/33): a
            // rule declaring a deep capability may reference that binding;
            // anything else referencing it fails compilation (fail-closed)
            xscriptEngine = new XScriptEngine(model.getId(), model.getSeverity(),
                    XScriptCompiler.compile(model.getId(), model.getXscript(),
                            resolveRequires(model.getRequires())));
        }
        RuleDslModel.Matcher matcher = model.getMatcher();
        if (matcher == null) {
            throw new NopLintException("Rule '" + model.getId() + "' has no matcher");
        }
        if (matcher.getRegex() != null) {
            throw regexRejected(model.getId(), "rule container");
        }

        // Every pattern compile below feeds this index so the constraint
        // compiler can verify each capture reference against the rule's real
        // declared meta-var set (roadmap item 22).
        CaptureIndex captures = new CaptureIndex();

        // The utils registry is compiled eagerly and before the container
        // matcher (roadmap item 24): every declared util must compile
        // fail-closed even when unreferenced, and matches/stopBy=rule
        // resolution needs it. Util captures join the rule's capture index.
        UtilRegistry utils = buildUtilRegistry(model, language, captures);

        TreeSet<Integer> targets = new TreeSet<>();
        if (matcher.getPattern() != null) {
            SourcePattern pattern = compilePattern(model.getId(), matcher.getPattern(), language);
            captures.collect(pattern);
            addPatternTargets(targets, pattern, -1);
            return finish(model, targets, tree -> pattern.matchIn(tree.root()), xscriptEngine,
                    captures, language);
        }
        if (matcher.getKind() != null) {
            int kindId = resolveKind(model.getId(), language, matcher.getKind());
            targets.add(kindId);
            return finish(model, targets, tree -> nodesOfKind(tree.root(), kindId), xscriptEngine,
                    captures, language);
        }

        // Composite forms (all/not/matches/relational): a node-matcher tree
        // over a whole-tree scan, with the kind opinion per design 01 §4
        // step 5.
        if (matcher.getMatches() != null) {
            NodeMatcher referent = new ReferentMatcher(model.getId(), matcher.getMatches(),
                    utils.matchers);
            int[] opinion = utils.opinionOf(matcher.getMatches());
            for (int kindId : opinion) {
                targets.add(kindId);
            }
            final int[] filterKinds = opinion;
            return finish(model, targets, tree -> scanTree(tree, referent, filterKinds),
                    xscriptEngine, captures, language);
        }
        if (matcher.getAll() != null || matcher.getNot() != null
                || matcher.getInside() != null || matcher.getHas() != null
                || matcher.getFollows() != null || matcher.getPrecedes() != null) {
            CompiledNode node = compileNodeMatcher(model.getId(), matcher, language,
                    "rule container", captures, utils);
            for (int kindId : node.opinion()) {
                targets.add(kindId);
            }
            final int[] filterKinds = node.opinion();
            return finish(model, targets, tree -> scanTree(tree, node.matcher(), filterKinds),
                    xscriptEngine, captures, language);
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
            if (branch.getNested() != null) {
                // object-form branch (roadmap item 24): the nested matcher
                // compiles into the same node-matcher kernel and scans the
                // whole tree; its kind opinion joins the union prefilter
                CompiledNode nested = compileNodeMatcher(model.getId(), branch.getNested(), language,
                        "'any' branch #" + index, captures, utils);
                int[] branchOpinion = nested.opinion();
                for (int kindId : branchOpinion) {
                    targets.add(kindId);
                }
                final int[] filterKinds = branchOpinion;
                branchMatchers.add(tree -> scanTree(tree, nested.matcher(), filterKinds));
            } else if (branch.getPattern() != null) {
                SourcePattern pattern = compilePattern(model.getId(), branch.getPattern(), language);
                captures.collect(pattern);
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
                        + index + " (at least one of pattern|kind|regex, or exactly one nested "
                        + "matcher, is required)");
            }
        }
        List<RuleMatcher> branchChain = List.copyOf(branchMatchers);
        return finish(model, targets, tree -> {
            List<Match> all = new ArrayList<>();
            for (RuleMatcher branchMatcher : branchChain) {
                all.addAll(branchMatcher.match(tree));
            }
            return all;
        }, xscriptEngine, captures, language);
    }

    /**
     * Compiles the rule's constraints against the capture set the matcher
     * declared and assembles the final rule — the single construction exit
     * of the tree-sitter compile path, so a constrained rule can never skip
     * its capture validation.
     */
    private static CompiledRule finish(RuleDslModel model, TreeSet<Integer> targets, RuleMatcher body,
                                       XScriptEngine xscriptEngine, CaptureIndex captures,
                                       LintLanguage language) {
        List<Constraint> constraints = new ArrayList<>(model.getConstraints().size());
        for (RuleDslModel.Constraint constraint : model.getConstraints()) {
            constraints.add(Constraints.compile(constraint, model.getId(), language,
                    captures.singleCaptures, captures.multiCaptures));
        }
        TemplateFix templateFix = null;
        String fixDescription = null;
        boolean fixSuggestOnly = false;
        if (model.getFix() != null) {
            templateFix = TemplateFix.compile(model.getId(), model.getFix().getTemplate(),
                    captures.singleCaptures, captures.multiCaptures);
            fixDescription = model.getFix().getDescription();
            fixSuggestOnly = model.getFix().isSuggest();
        }
        return new CompiledRule(model.getId(), model.getSeverity(), model.getMessage(), targets,
                body, xscriptEngine, model.getXscriptTimeoutMs(), constraints, templateFix,
                fixDescription, fixSuggestOnly, resolveRequires(model.getRequires()),
                model.getRequires());
    }

    /**
     * The rule's {@code requires} tokens resolved to capabilities (unknown
     * tokens drop out — the gate already failed them closed before any
     * compile, so a compiled rule only ever carries known ones).
     */
    private static Set<LintCapability> resolveRequires(Set<String> tokens) {
        Set<LintCapability> resolved = EnumSet.noneOf(LintCapability.class);
        for (String token : tokens) {
            LintCapability capability = LintCapability.byToken(token);
            if (capability != null) {
                resolved.add(capability);
            }
        }
        return resolved;
    }

    /**
     * The compiled utils registry of one rule file (roadmap item 24): every
     * declared util's node matcher plus its memoized kind opinion. Matchers
     * are compiled eagerly — a declared-but-broken util fails the rule
     * compile even when unreferenced (fail-closed at load) — while
     * references between utils resolve lazily through {@link #matchers} at
     * match time, so declaration order never matters.
     */
    private static final class UtilRegistry {
        final Map<String, NodeMatcher> matchers;
        private final Map<String, int[]> opinions;

        UtilRegistry(Map<String, NodeMatcher> matchers, Map<String, int[]> opinions) {
            this.matchers = matchers;
            this.opinions = opinions;
        }

        /**
         * The kind opinion of one util (design 01 §4 step 5), computed once
         * during the util's own compilation (plan 10: the opinion derives
         * from the compiled node — no second pattern compilation).
         */
        int[] opinionOf(String utilId) {
            int[] opinion = opinions.get(utilId);
            if (opinion == null) {
                throw new NopLintException("Rule asks for the kind opinion of util '"
                        + utilId + "' which the registry does not contain (invariant broken; "
                        + "fail-closed)");
            }
            return opinion;
        }
    }

    /**
     * Compiles every declared util of the rule file eagerly (a broken util
     * fails the compile even when unreferenced) and returns the registry the
     * matches/stopBy=rule resolution shares.
     */
    private static UtilRegistry buildUtilRegistry(RuleDslModel model, LintLanguage language,
                                                  CaptureIndex captures) {
        Map<String, RuleDslModel.Matcher> sources = model.getUtils();
        if (sources.isEmpty()) {
            return new UtilRegistry(Map.of(), Map.of());
        }
        Map<String, NodeMatcher> matchers = new LinkedHashMap<>();
        Map<String, int[]> opinions = new LinkedHashMap<>();
        UtilRegistry registry = new UtilRegistry(matchers, opinions);
        for (Map.Entry<String, RuleDslModel.Matcher> util : sources.entrySet()) {
            CompiledNode node = compileNodeMatcher(model.getId(), util.getValue(), language,
                    "util '" + util.getKey() + "'", captures, registry);
            matchers.put(util.getKey(), node.matcher());
            opinions.put(util.getKey(), node.opinion());
        }
        return registry;
    }

    /**
     * The union of capture names every pattern of one rule declares — the
     * reference set the constraint compiler validates against.
     */
    private static final class CaptureIndex {
        private final Set<String> singleCaptures = new HashSet<>();
        private final Set<String> multiCaptures = new HashSet<>();

        void collect(SourcePattern pattern) {
            singleCaptures.addAll(pattern.captureNames());
            multiCaptures.addAll(pattern.multiCaptureNames());
        }
    }

    private static NopLintException regexRejected(String ruleId, String location) {
        return new NopLintException("Rule '" + ruleId + "' uses a 'regex' matcher (" + location
                + "), which this engine version does not execute (deferred to roadmap item 22); the rule "
                + "is rejected at compile time instead of being skipped silently");
    }

    // ==================== composite forms (all / not / relational) ====================


    private static TreeSet<Integer> toSet(int[] values) {
        TreeSet<Integer> set = new TreeSet<>();
        for (int value : values) {
            set.add(value);
        }
        return set;
    }

    /**
     * Compiles one matcher object into the node-matcher tree. The composite
     * surface nests recursively (roadmap item 24: any/all/not/matches are
     * legal at every matcher-object position); termination comes from the
     * parse-time acyclicity of the util reference graph plus YAML's finite
     * structure, and the matches expansion carries its own runtime depth
     * cap as the backstop.
     */
    /**
     * One compiled node matcher together with its kind opinion (design 01 §4
     * step 5): computed during the single compilation descent — the former
     * separate {@code kindOpinion} walk re-compiled every pattern a second
     * time (plan 10). The opinion rules are preserved verbatim:
     * pattern → its possible kinds, kind → singleton, matches → the util's
     * opinion, any → union of non-empty branch opinions, all → conservative
     * intersection of non-empty child opinions, relational/not → empty.
     */
    private record CompiledNode(NodeMatcher matcher, int[] opinion) {
    }

    private static CompiledNode compileNodeMatcher(String ruleId, RuleDslModel.Matcher matcher,
                                                   LintLanguage language, String location,
                                                   CaptureIndex captures, UtilRegistry utils) {
        if (matcher.getAny() != null) {
            List<NodeMatcher> branches = new ArrayList<>(matcher.getAny().size());
            List<int[]> branchOpinions = new ArrayList<>(matcher.getAny().size());
            int index = 0;
            for (RuleDslModel.Branch branch : matcher.getAny()) {
                index++;
                if (branch.getNested() != null) {
                    CompiledNode nested = compileNodeMatcher(ruleId, branch.getNested(), language,
                            location + " any branch #" + index, captures, utils);
                    branches.add(nested.matcher());
                    branchOpinions.add(nested.opinion());
                } else {
                    CompiledNode flat = flatBranchMatcher(ruleId, branch, language, captures,
                            location + " any branch #" + index);
                    branches.add(flat.matcher());
                    branchOpinions.add(flat.opinion());
                }
            }
            return new CompiledNode(new AnyMatcher(branches), union(branchOpinions));
        }
        if (matcher.getMatches() != null) {
            return new CompiledNode(new ReferentMatcher(ruleId, matcher.getMatches(), utils.matchers),
                    utils.opinionOf(matcher.getMatches()));
        }
        if (matcher.getAll() != null) {
            List<NodeMatcher> children = new ArrayList<>(matcher.getAll().size());
            List<int[]> childOpinions = new ArrayList<>(matcher.getAll().size());
            int index = 0;
            for (RuleDslModel.Matcher element : matcher.getAll()) {
                index++;
                CompiledNode child = compileNodeMatcher(ruleId, element, language,
                        "all element #" + index, captures, utils);
                children.add(child.matcher());
                childOpinions.add(child.opinion());
            }
            return new CompiledNode(new AllMatcher(children), intersection(childOpinions));
        }
        if (matcher.getNot() != null) {
            NodeMatcher inner = compileNodeMatcher(ruleId, matcher.getNot(), language,
                    location + " 'not'", captures, utils).matcher();
            return new CompiledNode(new NotMatcher(inner), EMPTY_OPINION);
        }
        if (matcher.getInside() != null) {
            return new CompiledNode(new RelationalMatcher(RelationalMatcher.Op.INSIDE,
                    relationalInner(ruleId, matcher.getInside(), language, captures),
                    relationalStopBy(ruleId, matcher.getInside(), utils), matcher.getInside().getField()),
                    EMPTY_OPINION);
        }
        if (matcher.getHas() != null) {
            return new CompiledNode(new RelationalMatcher(RelationalMatcher.Op.HAS,
                    relationalInner(ruleId, matcher.getHas(), language, captures),
                    relationalStopBy(ruleId, matcher.getHas(), utils), matcher.getHas().getField()),
                    EMPTY_OPINION);
        }
        if (matcher.getFollows() != null) {
            return new CompiledNode(new RelationalMatcher(RelationalMatcher.Op.FOLLOWS,
                    relationalInner(ruleId, matcher.getFollows(), language, captures),
                    relationalStopBy(ruleId, matcher.getFollows(), utils),
                    matcher.getFollows().getField()),
                    EMPTY_OPINION);
        }
        if (matcher.getPrecedes() != null) {
            return new CompiledNode(new RelationalMatcher(RelationalMatcher.Op.PRECEDES,
                    relationalInner(ruleId, matcher.getPrecedes(), language, captures),
                    relationalStopBy(ruleId, matcher.getPrecedes(), utils),
                    matcher.getPrecedes().getField()),
                    EMPTY_OPINION);
        }
        if (matcher.getPattern() != null) {
            SourcePattern pattern = compilePattern(ruleId, matcher.getPattern(), language);
            captures.collect(pattern);
            return new CompiledNode(new PatternNodeMatcher(pattern), pattern.possibleKindIds());
        }
        if (matcher.getKind() != null) {
            int kindId = resolveKind(ruleId, language, matcher.getKind());
            return new CompiledNode(new KindNodeMatcher(kindId), new int[]{kindId});
        }
        throw regexRejected(ruleId, location);
    }

    private static final int[] EMPTY_OPINION = new int[0];

    private static int[] union(List<int[]> opinions) {
        TreeSet<Integer> merged = new TreeSet<>();
        for (int[] opinion : opinions) {
            for (int kindId : opinion) {
                merged.add(kindId);
            }
        }
        return merged.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The conservative intersection of the children's NON-empty opinions; an
     * empty result (or all-empty children) means no opinion — the filter
     * must not exclude.
     */
    private static int[] intersection(List<int[]> opinions) {
        TreeSet<Integer> merged = null;
        for (int[] opinion : opinions) {
            if (opinion.length == 0) {
                continue;
            }
            if (merged == null) {
                merged = new TreeSet<>();
                for (int kindId : opinion) {
                    merged.add(kindId);
                }
            } else {
                merged.retainAll(toSet(opinion));
            }
        }
        return merged == null ? EMPTY_OPINION : merged.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * The flat any branch (pattern/kind/regex conjunction) as a node matcher
     * over a whole-tree scan — the object-branch path of an {@code any}
     * whose sibling branches may be composites.
     */
    /**
     * The flat any branch (pattern/kind/regex conjunction). Opinion mirrors
     * the former flatBranchOpinion verbatim: the pattern's kinds when a
     * pattern is present (the kind conjunct narrows matching, not the
     * conservative opinion), else the kind singleton, else none.
     */
    private static CompiledNode flatBranchMatcher(String ruleId, RuleDslModel.Branch branch,
                                                  LintLanguage language, CaptureIndex captures,
                                                  String location) {
        SourcePattern pattern = null;
        KindNodeMatcher kindMatcher = null;
        int[] kindOpinion = EMPTY_OPINION;
        if (branch.getPattern() != null) {
            pattern = compilePattern(ruleId, branch.getPattern(), language);
            captures.collect(pattern);
        }
        if (branch.getKind() != null) {
            int kindId = resolveKind(ruleId, language, branch.getKind());
            kindMatcher = new KindNodeMatcher(kindId);
            kindOpinion = new int[]{kindId};
        }
        if (pattern == null && kindMatcher == null) {
            throw regexRejected(ruleId, location);
        }
        if (pattern == null) {
            return new CompiledNode(kindMatcher, kindOpinion);
        }
        if (kindMatcher == null) {
            return new CompiledNode(new PatternNodeMatcher(pattern), pattern.possibleKindIds());
        }
        // the flat conjunction form: pattern and kind on one branch
        return new CompiledNode(new AllMatcher(List.of(new PatternNodeMatcher(pattern), kindMatcher)),
                pattern.possibleKindIds());
    }

    private static NodeMatcher relationalInner(String ruleId, RuleDslModel.Relational relational,
                                               LintLanguage language, CaptureIndex captures) {
        if (relational.getContext() != null) {
            try {
                SourcePattern pattern = SourcePatternCompiler.contextual(
                        relational.getSelector(), relational.getContext(), language);
                captures.collect(pattern);
                return new PatternNodeMatcher(pattern);
            } catch (NopLintException e) {
                throw new NopLintException("Rule '" + ruleId + "' has an invalid contextual pattern: "
                        + e.getMessage(), e);
            }
        }
        SourcePattern pattern = compilePattern(ruleId, relational.getPattern(), language);
        captures.collect(pattern);
        return new PatternNodeMatcher(pattern);
    }

    private static StopBy relationalStopBy(String ruleId, RuleDslModel.Relational relational,
                                           UtilRegistry utils) {
        switch (relational.getStopBy()) {
            case "neighbor":
                return StopBy.neighbor();
            case "rule":
                // roadmap item 24: the horizon resolves through the same
                // utils registry as 'matches' — lazily, via a ReferentMatcher,
                // so a util may name a stop rule declared later in the file
                // (the parse-time reference validation guarantees presence)
                return StopBy.rule(new ReferentMatcher(ruleId, relational.getStopByRule(),
                        utils.matchers));
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
     * one of {@code occurringKinds} (or when the rule has no kind opinion). A
     * false return lets the engine skip the matcher entirely. Both arrays
     * must be sorted ascending — {@code targetKindIds} is sorted at compile
     * time and {@code occurringKinds} comes from {@link KindIndex#collect};
     * the merge runs in O(K+T) with no boxing (plan 08).
     */
    public boolean canMatchKinds(int[] occurringKinds) {
        if (targetKindIds.length == 0) {
            return true;
        }
        int t = 0;
        for (int occurring : occurringKinds) {
            while (t < targetKindIds.length && targetKindIds[t] < occurring) {
                t++;
            }
            if (t == targetKindIds.length) {
                return false;
            }
            if (targetKindIds[t] == occurring) {
                return true;
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
     * The rule's resolved {@code requires} capabilities (roadmap item 31):
     * the runner re-judges these at rule boundaries against an engaged
     * degrade ladder — a rule whose requirement the ladder closed degrades
     * instead of running.
     */
    public Set<LintCapability> requires() {
        return requires;
    }

    /**
     * The {@code requires} tokens exactly as declared — including unknown
     * ones. The per-file gate of the precompiled path re-judges these with
     * the identical token logic as the per-model gate, so an unknown token
     * still reads as skipped-by-profile, never as runnable (plan 10).
     */
    public Set<String> rawRequires() {
        return rawRequires;
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
