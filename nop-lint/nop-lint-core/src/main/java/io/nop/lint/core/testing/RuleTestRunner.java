package io.nop.lint.core.testing;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * RuleTester launcher (design 03 §4): discovers rule test suites under a
 * conventional VFS directory, loads each suite's rule through
 * {@link RuleDslParser#loadRuleModel} (the registered {@code rule.yml}
 * pipeline, so fixtures must live under {@code _vfs/}), runs every fixture
 * through the real {@link LintEngine}, and asserts the {@code *.expect}
 * expectations fail-closed.
 *
 * <p>Suite layout (one directory per rule under
 * {@code <test-resources>/_vfs/test/lint/suites/<category>/<rule-name>/}):
 * the rule file {@code <rule-name>.rule.yml}, optional {@code valid/*.java}
 * sources that must produce zero diagnostics, and {@code invalid/*.java}
 * sources whose sibling {@code <name>.expect} file (parsed by
 * {@link ExpectParser}) lists the expected diagnostics one by one —
 * order-insensitive matching on ruleId, 1-based line/endLine, and message
 * fragment.</p>
 *
 * <p>Nothing is silently skipped: a suite without any fixture, an invalid
 * fixture without its {@code .expect} sibling, an empty expectation list,
 * or an unexpected count/field mismatch all surface as {@link FixtureFailure}
 * entries with the fixture path and the expected-vs-actual contrast, so a
 * broken suite can never render as a green build.</p>
 */
public final class RuleTestRunner {

    /**
     * The conventional VFS root scanned by the JUnit launcher.
     */
    public static final String DEFAULT_SUITES_PATH = "/test/lint/suites";

    private static final String RULE_FILE_SUFFIX = ".rule.yml";
    private static final String EXPECT_FILE_SUFFIX = ".expect";
    private static final String JAVA_FILE_SUFFIX = ".java";

    private final LanguageRegistry registry;
    private final LintProfile profile;
    private final RuleDslParser ruleParser = new RuleDslParser();
    private final ExpectParser expectParser = new ExpectParser();

    /**
     * A runner over ServiceLoader-discovered language bindings and the
     * {@link LintProfile#STANDARD} profile.
     */
    public RuleTestRunner() {
        this(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
    }

    public RuleTestRunner(LanguageRegistry registry, LintProfile profile) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
    }

    /**
     * Runs every suite under {@code suitesPath} (one category level, then
     * one directory per rule). Non-directory entries at either discovery
     * level are layout errors and fail closed.
     */
    public List<SuiteResult> runSuites(String suitesPath) {
        List<SuiteResult> results = new ArrayList<>();
        List<IResource> categories = children(suitesPath);
        if (categories.isEmpty()) {
            throw new NopLintException("No rule test suite categories found under '" + suitesPath
                    + "'; expected <category>/<rule-name> directories with "
                    + "'<rule-name>" + RULE_FILE_SUFFIX + "' files");
        }
        for (IResource category : categories) {
            if (!category.isDirectory()) {
                throw new NopLintException("Unexpected file '" + category.getPath()
                        + "' at suite category level (only <category> directories are allowed)");
            }
            for (IResource suiteDir : children(category.getPath())) {
                if (!suiteDir.isDirectory()) {
                    throw new NopLintException("Unexpected file '" + suiteDir.getPath()
                            + "' at suite level (only <rule-name> directories are allowed)");
                }
                results.add(runSuite(suiteDir.getPath()));
            }
        }
        return results;
    }

    /**
     * Runs one suite directory and reports its fixture failures (an empty
     * list means every assertion held).
     */
    public SuiteResult runSuite(String suitePath) {
        String suiteName = StringHelper.fileName(suitePath);
        List<FixtureFailure> failures = new ArrayList<>();

        String rulePath = suitePath + "/" + suiteName + RULE_FILE_SUFFIX;
        IResource ruleResource = VirtualFileSystem.instance().getResource(rulePath, true);
        if (ruleResource == null || !ruleResource.exists()) {
            failures.add(new FixtureFailure(suiteName, rulePath, null, "suite has no rule file '"
                    + suiteName + RULE_FILE_SUFFIX + "' (the rule file name must match the "
                    + "suite directory name)"));
            return new SuiteResult(suitePath, suiteName, failures);
        }

        RuleDslModel rule;
        try {
            rule = ruleParser.loadRuleModel(rulePath);
        } catch (Exception e) {
            failures.add(new FixtureFailure(suiteName, rulePath, null,
                    "rule file failed to load: " + e));
            return new SuiteResult(suitePath, suiteName, failures);
        }

        List<IResource> validFixtures = javaFixtures(suiteName, rule.getId(), suitePath + "/valid", failures);
        List<IResource> invalidFixtures = javaFixtures(suiteName, rule.getId(), suitePath + "/invalid", failures);
        if (validFixtures.isEmpty() && invalidFixtures.isEmpty()) {
            failures.add(new FixtureFailure(suiteName, suitePath, rule.getId(),
                    "suite declares no valid/*.java and no invalid/*.java fixtures; zero "
                            + "assertions would be vacuous (no silent no-op)"));
        }

        for (IResource fixture : validFixtures) {
            runValidFixture(suiteName, rule, fixture, failures);
        }
        for (IResource fixture : invalidFixtures) {
            runInvalidFixture(suiteName, rule, fixture, failures);
        }
        return new SuiteResult(suitePath, rule.getId(), failures);
    }

    // ==================== fixture execution ====================

    private void runValidFixture(String suiteName, RuleDslModel rule, IResource fixture,
                                 List<FixtureFailure> failures) {
        String source = readText(fixture);
        LintResult result = lint(rule, source);
        if (!result.diagnostics().isEmpty()) {
            LineIndex lines = new LineIndex(source);
            failures.add(new FixtureFailure(suiteName, fixture.getPath(), rule.getId(),
                    "valid fixture must produce zero diagnostics but got "
                            + result.diagnostics().size() + ": "
                            + describeAll(result.diagnostics(), lines)));
        }
    }

    private void runInvalidFixture(String suiteName, RuleDslModel rule, IResource fixture,
                                   List<FixtureFailure> failures) {
        String source = readText(fixture);
        String expectPath = expectPathOf(fixture.getPath());
        IResource expectResource = VirtualFileSystem.instance().getResource(expectPath, true);
        if (expectResource == null || !expectResource.exists()) {
            failures.add(new FixtureFailure(suiteName, fixture.getPath(), rule.getId(),
                    "invalid fixture is missing its expectation file '" + expectPath
                            + "' (an invalid fixture without expectations would be vacuous)"));
            return;
        }

        ExpectModel expect;
        try {
            expect = expectParser.parse(expectPath, readText(expectResource));
        } catch (NopLintException e) {
            failures.add(new FixtureFailure(suiteName, expectPath, rule.getId(),
                    "expectation file rejected: " + e));
            return;
        }

        LintResult result = lint(rule, source);
        assertExpectations(suiteName, rule.getId(), fixture.getPath(), expect,
                result, new LineIndex(source), failures);
    }

    private LintResult lint(RuleDslModel rule, String source) {
        LintEngine engine = new LintEngine(registry, profile);
        return engine.lint(List.of(rule), rule.getLanguage(), source);
    }

    private void assertExpectations(String suiteName, String ruleId, String fixturePath,
                                    ExpectModel expect, LintResult result, LineIndex lines,
                                    List<FixtureFailure> failures) {
        List<ExpectDiagnostic> expected = expect.diagnostics();
        List<Diagnostic> actual = result.diagnostics();

        if (actual.size() != expected.size()) {
            failures.add(new FixtureFailure(suiteName, fixturePath, ruleId, "expected "
                    + expected.size() + " diagnostics but got " + actual.size()
                    + "; expected " + expected + "; actual " + describeAll(actual, lines)));
            return;
        }

        boolean[] consumed = new boolean[actual.size()];
        for (ExpectDiagnostic expectation : expected) {
            int index = findMatch(expectation, actual, consumed, lines);
            if (index < 0) {
                failures.add(new FixtureFailure(suiteName, fixturePath, expectation.ruleId(),
                        "no actual diagnostic matches expectation " + expectation
                                + "; actual " + describeAll(actual, lines)));
                return;
            }
            consumed[index] = true;
        }
    }

    /**
     * Order-insensitive matching: an unconsumed actual diagnostic matches
     * when ruleId, the mapped 1-based line/endLine, and the message fragment
     * all agree.
     */
    private int findMatch(ExpectDiagnostic expectation, List<Diagnostic> actual,
                          boolean[] consumed, LineIndex lines) {
        for (int i = 0; i < actual.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            Diagnostic diagnostic = actual.get(i);
            if (!expectation.ruleId().equals(diagnostic.ruleId())) {
                continue;
            }
            if (expectation.line() != lines.startLine(diagnostic.range())
                    || expectation.endLine() != lines.endLine(diagnostic.range())) {
                continue;
            }
            if (diagnostic.message() == null
                    || !diagnostic.message().contains(expectation.messageContains())) {
                continue;
            }
            return i;
        }
        return -1;
    }

    // ==================== suite layout ====================

    private List<IResource> javaFixtures(String suiteName, String ruleId, String dirPath,
                                         List<FixtureFailure> failures) {
        List<IResource> fixtures = new ArrayList<>();
        for (IResource child : children(dirPath)) {
            if (child.getName().endsWith(EXPECT_FILE_SUFFIX)) {
                continue;
            }
            if (!child.getName().endsWith(JAVA_FILE_SUFFIX)) {
                failures.add(new FixtureFailure(suiteName, child.getPath(), ruleId,
                        "unexpected file '" + child.getName() + "' under '" + dirPath
                                + "' (only *.java fixtures with optional *.expect siblings are allowed)"));
                continue;
            }
            fixtures.add(child);
        }
        return fixtures;
    }

    private List<IResource> children(String path) {
        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(path);
        if (children == null) {
            return List.of();
        }
        return new ArrayList<>(children);
    }

    private String expectPathOf(String fixturePath) {
        return fixturePath.substring(0, fixturePath.length() - JAVA_FILE_SUFFIX.length())
                + EXPECT_FILE_SUFFIX;
    }

    private String readText(IResource resource) {
        try {
            return resource.readText();
        } catch (Exception e) {
            throw new NopLintException("Failed to read fixture resource '" + resource.getPath()
                    + "': " + e.getMessage(), e);
        }
    }

    /**
     * The actual diagnostics rendered for failure messages: the mapped
     * 1-based lines plus rule id and message, so the expected-vs-actual
     * contrast is actionable without re-running anything.
     */
    private String describeAll(List<Diagnostic> diagnostics, LineIndex lines) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < diagnostics.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Diagnostic diagnostic = diagnostics.get(i);
            sb.append("{line=").append(lines.startLine(diagnostic.range()))
                    .append(", endLine=").append(lines.endLine(diagnostic.range()))
                    .append(", ruleId=").append(diagnostic.ruleId())
                    .append(", message=\"").append(diagnostic.message()).append("\"}");
        }
        return sb.append(']').toString();
    }
}
