package io.nop.lint.core.xml;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.CompiledRule;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.AllMatcher;
import io.nop.lint.core.pattern.KindNodeMatcher;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.NodeMatcher;
import io.nop.lint.core.pattern.NotMatcher;
import io.nop.lint.core.pattern.RelationalMatcher;
import io.nop.lint.core.pattern.StopBy;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.xscript.XScriptCompiler;
import io.nop.lint.core.xscript.XScriptEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Compiles {@code language: XML} rules for the XNode pattern substrate (the
 * Phase 1 wiring Decision, design 03 §1.1 增注): the matcher forms mirror the
 * engine's tree-sitter compilation matrix one-to-one, with the pattern leaf
 * swapped for {@link XNodePatternMatcher} and the XML-unsupported refinements
 * rejected fail-closed at compile time — {@code regex} (item 22), relational
 * {@code field} (XNode has no grammar field slots), relational
 * {@code context}+{@code selector} (no separate context parse), and
 * {@code stopBy=rule} (roadmap item 24). Everything else — identity fields,
 * the kind opinion matrix, xscript compilation, depth guards — shares the
 * engine's semantics so a compiled XML rule is indistinguishable downstream.
 */
public final class XmlRuleCompiler {

    private XmlRuleCompiler() {
    }

    /**
     * Compiles one XML rule model.
     *
     * @throws NopLintException with the rule id and reason for every
     *                          unsupported form (fail-closed, never a silent
     *                          skip)
     */
    public static CompiledRule compile(RuleDslModel model, XmlLanguage language) {
        if (model == null) {
            throw new NopLintException("rule model must not be null");
        }
        if (model.getSeverity() == null || model.getMessage() == null) {
            throw new NopLintException("Rule '" + model.getId() + "' cannot compile: "
                    + (model.getSeverity() == null ? "'severity'" : "'message'")
                    + " must not be blank (diagnostics must be attributable)");
        }
        XScriptEngine xscriptEngine = null;
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
            XNodePattern pattern = compilePattern(model.getId(), matcher.getPattern());
            targets.add(pattern.tagKindId());
            return CompiledRule.precompiled(model.getId(), model.getSeverity(), model.getMessage(),
                    targets, tree -> new XNodePatternMatcher(pattern).findMatches(tree.root()),
                    xscriptEngine, model.getXscriptTimeoutMs());
        }
        if (matcher.getKind() != null) {
            int kindId = resolveKind(model.getId(), language, matcher.getKind());
            targets.add(kindId);
            return CompiledRule.precompiled(model.getId(), model.getSeverity(), model.getMessage(),
                    targets, tree -> nodesOfKind(tree.root(), kindId),
                    xscriptEngine, model.getXscriptTimeoutMs());
        }

        if (matcher.getAll() != null || matcher.getNot() != null
                || matcher.getInside() != null || matcher.getHas() != null
                || matcher.getFollows() != null || matcher.getPrecedes() != null) {
            NodeMatcher nodeMatcher = compileNodeMatcher(model.getId(), matcher, language, 0, "rule container");
            int[] opinion = kindOpinion(model.getId(), matcher, language);
            for (int kindId : opinion) {
                targets.add(kindId);
            }
            final int[] filterKinds = opinion;
            return CompiledRule.precompiled(model.getId(), model.getSeverity(), model.getMessage(),
                    targets, tree -> scanTree(tree, nodeMatcher, filterKinds),
                    xscriptEngine, model.getXscriptTimeoutMs());
        }

        List<RuleDslModel.Branch> branches = matcher.getAny();
        if (branches == null || branches.isEmpty()) {
            throw new NopLintException("Rule '" + model.getId()
                    + "' has no matcher (neither pattern/kind/regex nor any branches)");
        }
        List<CompiledRuleAccessor> branchMatchers = new ArrayList<>(branches.size());
        int index = 0;
        for (RuleDslModel.Branch branch : branches) {
            index++;
            if (branch.getRegex() != null) {
                throw regexRejected(model.getId(), "'any' branch #" + index);
            }
            if (branch.getPattern() != null) {
                XNodePattern pattern = compilePattern(model.getId(), branch.getPattern());
                targets.add(pattern.tagKindId());
                branchMatchers.add(tree -> new XNodePatternMatcher(pattern).findMatches(tree.root()));
            } else if (branch.getKind() != null) {
                int kindId = resolveKind(model.getId(), language, branch.getKind());
                targets.add(kindId);
                branchMatchers.add(tree -> nodesOfKind(tree.root(), kindId));
            } else {
                throw new NopLintException("Rule '" + model.getId() + "' declares an empty 'any' branch #"
                        + index + " (at least one of pattern|kind|regex is required)");
            }
        }
        List<CompiledRuleAccessor> chain = List.copyOf(branchMatchers);
        return CompiledRule.precompiled(model.getId(), model.getSeverity(), model.getMessage(),
                targets, tree -> {
                    List<Match> all = new ArrayList<>();
                    for (CompiledRuleAccessor branch : chain) {
                        all.addAll(branch.match(tree));
                    }
                    return all;
                }, xscriptEngine, model.getXscriptTimeoutMs());
    }

    @FunctionalInterface
    private interface CompiledRuleAccessor {
        List<Match> match(LintTree tree);
    }

    /**
     * The kind opinion of one matcher object (design 01 §4 step 5, mirrored
     * for XNode patterns): a pattern contributes its tag kind, a kind
     * conjunct its singleton, relational and not matchers no opinion, an all
     * matcher the conservative intersection of its children's non-empty
     * opinions. Empty = no opinion = the kind filter must not exclude.
     */
    private static int[] kindOpinion(String ruleId, RuleDslModel.Matcher matcher, LintLanguage language) {
        if (matcher.getPattern() != null) {
            return new int[]{compilePattern(ruleId, matcher.getPattern()).tagKindId()};
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
                    relationalInner(ruleId, matcher.getInside()), relationalStopBy(ruleId, matcher.getInside()),
                    null);
        }
        if (matcher.getHas() != null) {
            return new RelationalMatcher(RelationalMatcher.Op.HAS,
                    relationalInner(ruleId, matcher.getHas()), relationalStopBy(ruleId, matcher.getHas()),
                    null);
        }
        if (matcher.getFollows() != null) {
            return new RelationalMatcher(RelationalMatcher.Op.FOLLOWS,
                    relationalInner(ruleId, matcher.getFollows()), relationalStopBy(ruleId, matcher.getFollows()),
                    null);
        }
        if (matcher.getPrecedes() != null) {
            return new RelationalMatcher(RelationalMatcher.Op.PRECEDES,
                    relationalInner(ruleId, matcher.getPrecedes()), relationalStopBy(ruleId, matcher.getPrecedes()),
                    null);
        }
        if (matcher.getPattern() != null) {
            return new XNodePatternMatcher(compilePattern(ruleId, matcher.getPattern()));
        }
        if (matcher.getKind() != null) {
            return new KindNodeMatcher(resolveKind(ruleId, language, matcher.getKind()));
        }
        throw regexRejected(ruleId, location);
    }

    private static NodeMatcher relationalInner(String ruleId, RuleDslModel.Relational relational) {
        if (relational.getContext() != null || relational.getSelector() != null) {
            throw new NopLintException("Rule '" + ruleId + "' uses the relational context+selector form, "
                    + "which has no XNode reading in v1 (XML has no separate context parse; fail-closed; "
                    + "use the plain pattern form)");
        }
        if (relational.getField() != null && !relational.getField().isBlank()) {
            throw new NopLintException("Rule '" + ruleId + "' declares a relational 'field' constraint, "
                    + "but XNode has no grammar field slots (fail-closed)");
        }
        return new XNodePatternMatcher(compilePattern(ruleId, relational.getPattern()));
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

    private static XNodePattern compilePattern(String ruleId, String patternText) {
        try {
            return XNodePatternCompiler.compile(patternText);
        } catch (NopLintException e) {
            throw new NopLintException("Rule '" + ruleId + "' has an invalid pattern: " + e.getMessage(), e);
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

    private static List<Match> nodesOfKind(LintNode root, int kindId) {
        List<Match> matches = new ArrayList<>();
        for (LintNode node : root) {
            if (node.kindId() == kindId) {
                matches.add(new Match(node, new MetaVarEnv()));
            }
        }
        return matches;
    }

    private static NopLintException regexRejected(String ruleId, String location) {
        return new NopLintException("Rule '" + ruleId + "' uses a 'regex' matcher (" + location
                + "), which this engine version does not execute (deferred to roadmap item 22); the rule "
                + "is rejected at compile time instead of being skipped silently");
    }
}
