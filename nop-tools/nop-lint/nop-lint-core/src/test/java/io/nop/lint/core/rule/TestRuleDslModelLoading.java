package io.nop.lint.core.rule;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xdef.IXDefinition;
import io.nop.xlang.xmeta.SchemaLoader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 loading proofs for the rule DSL metamodel.
 *
 * <p>Exercises the registered loader chain end to end: register-model discovery
 * maps fileType {@code rule.yml} to {@code DslJsonResourceLoader}, YAML is
 * transformed to XNode, validated against {@code /nop/lint/schema/lint-rule.xdef}
 * (including x:extends delta merge), and returned as a model object whose field
 * values must match the YAML sources.
 */
public class TestRuleDslModelLoading {

    private static final String DIR = "/test/lint/rules/";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static Object load(String fixture) {
        return ResourceComponentManager.instance().loadComponentModel(DIR + fixture);
    }

    private static Object prop(Object model, String name) {
        assertNotNull(model, "model must be loaded when reading prop " + name);
        return ((DynamicObject) model).obj_propValues().get(name);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof DynamicObject dyn)
            return dyn.obj_propValues();
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> mapProp(Object model, String name) {
        return asMap(prop(model, name));
    }

    private static Map<String, Object> mapValue(Object value) {
        return asMap(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listProp(Object model, String name) {
        return (List<Object>) prop(model, name);
    }

    @Test
    public void xdefLoadsAndPassesLoadTimeFailFastChecks() {
        IXDefinition xdef = SchemaLoader.loadXDefinition("/nop/lint/schema/lint-rule.xdef");
        assertNotNull(xdef, "lint-rule.xdef must load through the platform schema loader");
        assertEquals("LintRule", xdef.getRootNode().getXdefName());
    }

    @Test
    public void registeredLoaderWiringReturnsModelMatchingYaml() {
        Object model = load("valid-simple.rule.yml");

        assertEquals("demo/no-console", prop(model, "id"));
        assertEquals("Java", prop(model, "language"));
        assertEquals("warning", prop(model, "severity"));
        assertEquals("Do not use System.out.println for logging", prop(model, "message"));
        assertEquals(100, prop(model, "xscriptTimeoutMs"), "xscriptTimeoutMs must default to 100");

        Map<String, Object> rule = mapProp(model, "rule");
        assertEquals("System.out.println($$$ARGS)", rule.get("pattern"));
    }

    @Test
    public void anyBranchesKeepMatcherStructure() {
        Object model = load("valid-any.rule.yml");

        Map<String, Object> rule = mapProp(model, "rule");
        List<Object> any = (List<Object>) rule.get("any");
        assertEquals(2, any.size(), "any must keep two branches");

        Map<String, Object> first = mapValue(any.get(0));
        assertEquals("$OBJ.invoke($NAME)", first.get("pattern"));

        Map<String, Object> second = mapValue(any.get(1));
        assertEquals("$OBJ.getClass().getMethod($NAME)", second.get("pattern"));
        assertEquals("method_invocation", second.get("kind"), "conjunction branch keeps pattern+kind");
    }

    @Test
    public void fullFieldRuleRoundTripsEveryPhase1Field() {
        Object model = load("valid-full.rule.yml");

        assertEquals("demo/no-file-stream", prop(model, "id"));
        assertEquals(250, prop(model, "xscriptTimeoutMs"));
        assertEquals("captures.F", prop(model, "xscript"), "xscript text must round-trip verbatim");

        Collection<?> requires = (Collection<?>) prop(model, "requires");
        assertTrue(requires.contains("L1") && requires.contains("L2"),
                "requires must keep both levels, got " + requires);

        Map<String, Object> metadata = mapProp(model, "metadata");
        assertEquals("api-usage", metadata.get("category"));
        assertEquals("warning", metadata.get("severity"));
        assertEquals("1.2", metadata.get("version"));
        assertEquals(Boolean.FALSE, metadata.get("autoFixable"));
        Collection<?> source = (Collection<?>) metadata.get("source");
        assertTrue(source.contains("eslint") && source.contains("custom"),
                "metadata.source must keep both values, got " + source);

        Map<String, Object> options = mapProp(model, "options");
        Map<String, Object> maxFiles = mapValue(options.get("maxFiles"));
        assertEquals("16", maxFiles.get("value"));

        Map<String, Object> settings = mapProp(model, "settings");
        Map<String, Object> mode = mapValue(settings.get("mode"));
        assertEquals("strict", mode.get("value"));

        Map<String, Object> files = mapProp(model, "files");
        Collection<?> include = (Collection<?>) files.get("include");
        assertTrue(include.contains("src/main/**/*.java"));
        Collection<?> exclude = (Collection<?>) files.get("exclude");
        assertTrue(exclude.contains("**/*Test.java") && exclude.contains("**/*IT.java"),
                "files.exclude must keep both globs, got " + exclude);
    }

    @Test
    public void xExtendsChildRuleMergesParentFields() {
        Object model = load("child-rule.rule.yml");

        assertEquals("demo/child-logging", prop(model, "id"), "child id must win");
        assertEquals("Overridden child message", prop(model, "message"), "child message must win");

        Map<String, Object> rule = mapProp(model, "rule");
        assertEquals("System.out.println($$$ARGS)", rule.get("pattern"),
                "parent rule.matcher must be inherited through x:extends");

        Map<String, Object> metadata = mapProp(model, "metadata");
        assertEquals("style", metadata.get("category"), "parent metadata must be inherited");
        assertEquals(100, prop(model, "xscriptTimeoutMs"));
    }

    @Test
    public void missingIdIsRejected() {
        assertThrows(NopException.class, () -> load("invalid-missing-id.rule.yml"),
                "missing mandatory id must fail loading");
    }

    @Test
    public void missingMessageIsRejected() {
        assertThrows(NopException.class, () -> load("invalid-missing-message.rule.yml"),
                "missing mandatory message must fail loading");
    }

    @Test
    public void severityOutsideEnumIsRejected() {
        assertThrows(NopException.class, () -> load("invalid-bad-severity.rule.yml"),
                "severity outside hint|info|warning|error|off must fail loading");
    }

    @Test
    public void unknownTopLevelKeyIsRejected() {
        assertThrows(NopException.class, () -> load("invalid-unknown-key.rule.yml"),
                "unknown top level key must fail loading (no silent drop)");
    }

    @Test
    public void unknownKeyInsideRuleContainerIsRejected() {
        assertThrows(NopException.class, () -> load("invalid-unknown-key-in-rule.rule.yml"),
                "unknown key inside the rule container must fail loading");
    }
}
