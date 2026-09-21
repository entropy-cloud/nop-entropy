package io.nop.lint.core.cli;

import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads the production rule set from the classpath VFS (design 03 §2.4
 * 增注, 2026-09-22): the conventional prefix
 * {@link #DEFAULT_RULES_PREFIX} is scanned recursively for
 * {@code *.rule.yml} resources and every match is loaded through
 * {@link RuleDslParser#loadRuleModel} (the registered {@code rule.yml}
 * xdef pipeline), then grouped by the rule's {@code language} field under
 * its normalized (lowercased) id — the same normalization
 * {@link io.nop.lint.core.engine.LanguageRegistry} applies when resolving.
 *
 * <p>Fail-closed choices: a scan that finds no rule files at all is a
 * deployment error (the rule library module is missing from the classpath),
 * not an empty run; a rule whose {@code language} field is blank is
 * rejected. The CLI additionally verifies every group's language resolves
 * in the registry — an unshippable rule set must stop the run with exit
 * code 2 rather than lint with a silently reduced rule set. v1 accepts no
 * explicit rules-path override; test harnesses pass a different prefix
 * through {@link #loadGroupedByLanguage(String)} directly.</p>
 */
public final class RuleSetLoader {

    /**
     * The conventional VFS prefix of the production rule library
     * (nop-lint-nop main resources).
     */
    public static final String DEFAULT_RULES_PREFIX = "/nop/lint/rules/";

    private static final String RULE_SUFFIX = ".rule.yml";

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
        List<String> rulePaths = scanRulePaths(prefix);
        if (rulePaths.isEmpty()) {
            throw new NopLintException("no lint rule files (*" + RULE_SUFFIX + ") found under '"
                    + prefix + "' on the classpath VFS; the rule library module is missing");
        }

        Map<String, List<RuleDslModel>> grouped = new LinkedHashMap<>();
        for (String path : rulePaths) {
            RuleDslModel rule = parser.loadRuleModel(path);
            String normalized = normalizeLanguage(rule.getLanguage());
            if (normalized == null) {
                throw new NopLintException("rule '" + rule.getId() + "' loaded from '" + path
                        + "' declares a blank language field");
            }
            grouped.computeIfAbsent(normalized, key -> new ArrayList<>()).add(rule);
        }
        return grouped;
    }

    /**
     * Lists the rule file paths under {@code prefix}, sorted lexicographically
     * for stable rule order.
     */
    private List<String> scanRulePaths(String prefix) {
        List<String> paths = new ArrayList<>();
        collectRulePaths(prefix, paths);
        Collections.sort(paths);
        return paths;
    }

    private void collectRulePaths(String path, List<String> paths) {
        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(path);
        if (children == null) {
            return;
        }
        for (IResource child : children) {
            if (child.isDirectory()) {
                collectRulePaths(child.getPath(), paths);
            } else if (child.getName().endsWith(RULE_SUFFIX)) {
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
