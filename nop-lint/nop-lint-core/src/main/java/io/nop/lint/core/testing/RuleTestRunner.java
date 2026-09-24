package io.nop.lint.core.testing;

import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IFile;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.lint.core.semantic.TypeResolver;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.cli.TargetScanner;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.semantic.MetricsResolver;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;

import java.io.File;
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
 * the rule file {@code <rule-name>.rule.yml}, optional {@code valid/}
 * sources that must produce zero diagnostics, and {@code invalid/} sources
 * whose sibling {@code <name>.expect} file (parsed by {@link ExpectParser})
 * lists the expected diagnostics one by one — order-insensitive matching on
 * ruleId, 1-based line/endLine, and message fragment. The allowed fixture
 * extension is derived from the rule's language through the language
 * extension table ({@link TargetScanner#extensionsForLanguage}:
 * {@code java → *.java}, {@code typescript → *.ts}, {@code tsx → *.tsx}), so
 * the fixture surface and the CLI's target classification share one table
 * and cannot drift; a rule whose language binds no extension fails the
 * suite explicitly.</p>
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

    private final LanguageRegistry registry;
    private final LintProfile profile;
    private final TypeResolver typeResolver;
    private final MetricsResolver metricsResolver;
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
        this(registry, profile, null);
    }

    /**
     * A runner with the run family's L2 provider (roadmap item 20): suites
     * whose rules carry {@code requires: "L2"} resolve types through it, and
     * file-backed fixtures are linted under their real paths so type queries
     * can locate positions. Null keeps the L2-less behavior (those rules
     * degrade). The resolver is consulted lazily; suites without L2 rules
     * never start it.
     */
    public RuleTestRunner(LanguageRegistry registry, LintProfile profile, TypeResolver typeResolver) {
        this(registry, profile, typeResolver, null);
    }

    /**
     * A runner with the deep-profile metrics provider (roadmap item 32):
     * suites whose rules carry {@code requires: METRICS} resolve their
     * {@code metrics} binding through it when the suite lints under
     * {@link LintProfile#DEEP}. Null keeps the metrics-less behavior (those
     * rules degrade).
     */
    public RuleTestRunner(LanguageRegistry registry, LintProfile profile, TypeResolver typeResolver,
                          MetricsResolver metricsResolver) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.typeResolver = typeResolver;
        this.metricsResolver = metricsResolver;
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

        List<String> extensions = TargetScanner.extensionsForLanguage(rule.getLanguage());
        if (extensions.isEmpty()) {
            failures.add(new FixtureFailure(suiteName, rulePath, rule.getId(),
                    "rule language '" + rule.getLanguage() + "' binds no fixture extension in "
                            + "the language extension table; fixtures can never exist for it"));
            return new SuiteResult(suitePath, rule.getId(), failures);
        }

        List<IResource> validFixtures = sourceFixtures(suiteName, rule.getId(), suitePath + "/valid",
                extensions, failures);
        List<IResource> invalidFixtures = sourceFixtures(suiteName, rule.getId(), suitePath + "/invalid",
                extensions, failures);
        if (validFixtures.isEmpty() && invalidFixtures.isEmpty()) {
            failures.add(new FixtureFailure(suiteName, suitePath, rule.getId(),
                    "suite declares no valid/*.java and no invalid/*.java fixtures; zero "
                            + "assertions would be vacuous (no silent no-op)"));
        }

        for (IResource fixture : validFixtures) {
            runValidFixture(suiteName, rule, fixture, failures);
        }
        for (IResource fixture : invalidFixtures) {
            runInvalidFixture(suiteName, rule, fixture, extensions, failures);
        }
        return new SuiteResult(suitePath, rule.getId(), failures);
    }

    // ==================== fixture execution ====================

    private void runValidFixture(String suiteName, RuleDslModel rule, IResource fixture,
                                 List<FixtureFailure> failures) {
        String source = readText(fixture);
        LintResult result = lint(rule, source, fixture);
        if (!result.diagnostics().isEmpty()) {
            LineIndex lines = new LineIndex(source);
            failures.add(new FixtureFailure(suiteName, fixture.getPath(), rule.getId(),
                    "valid fixture must produce zero diagnostics but got "
                            + result.diagnostics().size() + ": "
                            + describeAll(result.diagnostics(), lines)));
        }
    }

    private void runInvalidFixture(String suiteName, RuleDslModel rule, IResource fixture,
                                   List<String> extensions, List<FixtureFailure> failures) {
        String source = readText(fixture);
        String extension = matchedExtension(fixture.getName(), extensions);
        String expectPath = expectPathOf(fixture.getPath(), extension);
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

        LintResult result = lint(rule, source, fixture);
        assertExpectations(suiteName, rule.getId(), fixture.getPath(), expect,
                result, new LineIndex(source), failures);
    }

    private LintResult lint(RuleDslModel rule, String source) {
        return lint(rule, source, null);
    }

    private LintResult lint(RuleDslModel rule, String source, IResource fixture) {
        LintEngine engine = new LintEngine(registry, profile, typeResolver, null, metricsResolver);
        String filePath = realPathOrNull(fixture);
        if (filePath != null) {
            return engine.lint(List.of(rule), rule.getLanguage(), filePath, source);
        }
        return engine.lint(List.of(rule), rule.getLanguage(), source);
    }

    /**
     * The fixture's real file path when it is file-backed (the L2 query
     * positions resolve against it), or null for virtual fixtures — those
     * lint unnamed, so L2-requiring rules degrade with the engine's explicit
     * accounting rather than querying a path tsc cannot read.
     */
    private static String realPathOrNull(IResource resource) {
        if (resource instanceof IFile file) {
            return file.toFile().getAbsolutePath();
        }
        if (resource instanceof ClassPathResource classPathResource) {
            File file = classPathResource.toFile();
            if (file != null) {
                return file.getAbsolutePath();
            }
        }
        return null;
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

    private List<IResource> sourceFixtures(String suiteName, String ruleId, String dirPath,
                                           List<String> extensions, List<FixtureFailure> failures) {
        List<IResource> fixtures = new ArrayList<>();
        for (IResource child : children(dirPath)) {
            if (child.getName().endsWith(EXPECT_FILE_SUFFIX)) {
                continue;
            }
            if (matchedExtension(child.getName(), extensions) == null) {
                failures.add(new FixtureFailure(suiteName, child.getPath(), ruleId,
                        "unexpected file '" + child.getName() + "' under '" + dirPath
                                + "' (only " + describeExtensions(extensions)
                                + " fixtures with optional *.expect siblings are allowed)"));
                continue;
            }
            fixtures.add(child);
        }
        return fixtures;
    }

    /**
     * The table extension a fixture name ends with, or null when the name
     * matches none of the rule language's extensions.
     */
    private static String matchedExtension(String name, List<String> extensions) {
        for (String extension : extensions) {
            if (name.endsWith("." + extension)) {
                return extension;
            }
        }
        return null;
    }

    private static String describeExtensions(List<String> extensions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < extensions.size(); i++) {
            if (i > 0) {
                sb.append(" or ");
            }
            sb.append("*.").append(extensions.get(i));
        }
        return sb.toString();
    }

    private List<IResource> children(String path) {
        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(path);
        if (children == null) {
            return List.of();
        }
        return new ArrayList<>(children);
    }

    private static String expectPathOf(String fixturePath, String extension) {
        return fixturePath.substring(0, fixturePath.length() - extension.length() - 1)
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
