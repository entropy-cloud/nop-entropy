package io.nop.lint.core.cli;

import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.rule.RuleSetModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads the production rule set from the classpath VFS (design 03 §2.4
 * 增注, 2026-09-22; ruleset coexistence per roadmap item 27): the
 * conventional prefix {@link #DEFAULT_RULES_PREFIX} is scanned recursively
 * for {@code *.rule.yml} and {@code *.ruleset.yml} resources. Standalone
 * rule files load through {@link RuleDslParser#loadRuleModel}; ruleset
 * files load through {@link RuleSetModel#load} and contribute their inline
 * rules plus their run-level exemptions (design 09 §4).
 *
 * <p>Fail-closed choices: a scan that finds no rule files at all is a
 * deployment error (the rule library module is missing from the classpath),
 * not an empty run; a rule whose {@code language} field is blank is
 * rejected; a rule id declared twice across the whole run (two standalone
 * files, two rulesets, or a ruleset and a standalone file) is a dual-source
 * drift and fails the load; an exemption naming a rule id the run does not
 * declare is rejected after the full id set is collected (checking earlier
 * would reject legal references to rules loaded later). The CLI additionally
 * verifies every group's language resolves in the registry — an unshippable
 * rule set must stop the run with exit code 2 rather than lint with a
 * silently reduced rule set. v1 accepts no explicit rules-path override;
 * test harnesses pass a different prefix through
 * {@link #loadRuleSet(String)} directly.</p>
 */
public final class RuleSetLoader {

    /**
     * The conventional VFS prefix of the production rule library
     * (nop-lint-nop main resources).
     */
    public static final String DEFAULT_RULES_PREFIX = "/nop/lint/rules/";

    private static final String RULE_SUFFIX = ".rule.yml";
    private static final String RULESET_SUFFIX = ".ruleset.yml";

    /**
     * The complete load outcome: rules grouped by normalized language id,
     * the run-level exemptions, and the declared rule id set the exemption
     * validation consumed.
     */
    public record LoadedRuleSet(Map<String, List<RuleDslModel>> rulesByLanguage,
                                List<RuleSetModel.Exemption> exemptions) {
    }

    private final RuleDslParser parser = new RuleDslParser();

    /**
     * Loads the rule files under the conventional prefix and groups the
     * models by normalized language id.
     *
     * @throws NopLintException when the prefix holds no rule files, a rule
     *                          file fails to load, or a rule declares a
     *                          blank language
     */
    public Map<String, List<RuleDslModel>> loadGroupedByLanguage() {
        return loadGroupedByLanguage(DEFAULT_RULES_PREFIX);
    }

    /**
     * Loads the rule files under {@code prefix} and groups the models by
     * normalized language id, in path order.
     */
    public Map<String, List<RuleDslModel>> loadGroupedByLanguage(String prefix) {
        return loadRuleSet(prefix).rulesByLanguage();
    }

    /**
     * Loads the standalone rule files and the rulesets under {@code prefix}
     * and returns the rules grouped by normalized language id together with
     * the run-level exemptions.
     *
     * @throws NopLintException when the prefix holds no rule files, a file
     *                          fails to load, a rule id is declared twice,
     *                          or an exemption names an unknown rule id
     */
    public LoadedRuleSet loadRuleSet(String prefix) {
        List<String> paths = scanPaths(prefix);
        if (paths.isEmpty()) {
            throw new NopLintException("no lint rule files (*" + RULE_SUFFIX + " or *" + RULESET_SUFFIX
                    + ") found under '" + prefix + "' on the classpath VFS; the rule library module "
                    + "is missing");
        }

        Map<String, List<RuleDslModel>> grouped = new LinkedHashMap<>();
        List<RuleSetModel.Exemption> exemptions = new ArrayList<>();
        Set<String> declaredIds = new LinkedHashSet<>();

        for (String path : paths) {
            if (path.endsWith(RULESET_SUFFIX)) {
                RuleSetModel ruleset = RuleSetModel.load(path);
                for (RuleDslModel rule : ruleset.rules()) {
                    addRule(grouped, declaredIds, rule, path);
                }
                exemptions.addAll(ruleset.exemptions());
                continue;
            }
            RuleDslModel rule = parser.loadRuleModel(path);
            addRule(grouped, declaredIds, rule, path);
        }

        // the unknown-exemption-rule check runs only after the full id set
        // exists (inline rules and standalone files may load in any order)
        for (RuleSetModel.Exemption exemption : exemptions) {
            if (!declaredIds.contains(exemption.ruleId())) {
                throw new NopLintException("ruleset exemption names rule '" + exemption.ruleId()
                        + "', which the run does not declare (declared: " + declaredIds
                        + "; an exemption for a typo'd or removed rule must not load silently)");
            }
        }
        return new LoadedRuleSet(Map.copyOf(grouped), List.copyOf(exemptions));
    }

    private void addRule(Map<String, List<RuleDslModel>> grouped, Set<String> declaredIds,
                         RuleDslModel rule, String path) {
        if (!declaredIds.add(rule.getId())) {
            throw new NopLintException("rule '" + rule.getId() + "' is declared twice (standalone "
                    + RULE_SUFFIX + " files and inline ruleset rules share one id namespace; dual-source "
                    + "declarations drift): " + path);
        }
        String normalized = normalizeLanguage(rule.getLanguage());
        if (normalized == null) {
            throw new NopLintException("rule '" + rule.getId() + "' loaded from '" + path
                    + "' declares a blank language field");
        }
        grouped.computeIfAbsent(normalized, key -> new ArrayList<>()).add(rule);
    }

    /**
     * Lists the rule/ruleset file paths under {@code prefix}, sorted
     * lexicographically for stable rule order.
     */
    private List<String> scanPaths(String prefix) {
        List<String> paths = new ArrayList<>();
        collectPaths(prefix, paths);
        Collections.sort(paths);
        return paths;
    }

    private void collectPaths(String path, List<String> paths) {
        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(path);
        if (children == null) {
            return;
        }
        for (IResource child : children) {
            if (child.isDirectory()) {
                collectPaths(child.getPath(), paths);
            } else if (child.getName().endsWith(RULE_SUFFIX) || child.getName().endsWith(RULESET_SUFFIX)) {
                paths.add(child.getPath());
            }
        }
    }

    /**
     * The registry's language-id normalization: trim + lowercase
     * ({@link Locale#ROOT}); blank yields null.
     */
    static String normalizeLanguage(String language) {
        if (language == null) {
            return null;
        }
        String trimmed = language.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}
