package io.nop.lint.core.rule;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transform rule matrix (nop-refactor WI3 Phase 1): a legal transform
 * parses with the synthesized/adjusted metadata semantics, every
 * language-agnostic rejection of the matrix fires with a diagnosable
 * message, and the existing fix carriers are untouched.
 */
public class TestTransformRuleParser {

    private final RuleDslParser parser = new RuleDslParser();

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private DynamicObject transformModel(String id, java.util.function.Consumer<DynamicObject> extra) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        DynamicObject transform = new DynamicObject("transform");
        transform.addProp("description", "rewrite foo to bar");
        transform.addProp("template", "bar($x)");
        model.addProp("transform", transform);
        if (extra != null)
            extra.accept(model);
        return model;
    }

    @Test
    public void legalTransformRuleParses() {
        RuleDslModel model = parser.parseRuleModel(transformModel("demo/t1", null));

        assertNotNull(model.getTransform());
        assertEquals("rewrite foo to bar", model.getTransform().getDescription());
        assertEquals("bar($x)", model.getTransform().getTemplate());
        assertNull(model.getFix(), "one rule carries at most one template carrier");
    }

    @Test
    public void transformWithoutMetadataBlockSynthesizesAutoFixableTrue() {
        RuleDslModel model = parser.parseRuleModel(transformModel("demo/t2", null));

        assertNotNull(model.getMetadata(), "the metadata face is synthesized, never null");
        assertEquals("transform", model.getMetadata().getCategory());
        assertTrue(model.getMetadata().isAutoFixable(),
                "a transform is auto-fixable by definition");
    }

    @Test
    public void transformWithDeclaredMetadataKeepsCategoryAndForcesAutoFixableTrue() {
        RuleDslModel model = parser.parseRuleModel(transformModel("demo/t3", m -> {
            DynamicObject metadata = new DynamicObject("metadata");
            metadata.addProp("category", "convention");
            m.addProp("metadata", metadata);
        }));

        assertEquals("convention", model.getMetadata().getCategory(),
                "the declared category is kept");
        assertTrue(model.getMetadata().isAutoFixable(),
                "the effective autoFixable is always true for a transform");
        assertNull(model.getMetadata().getSeverity());
    }

    @Test
    public void transformWithFixRejected() {
        DynamicObject model = transformModel("demo/t4", m -> {
            DynamicObject fix = new DynamicObject("fix");
            fix.addProp("description", "d");
            fix.addProp("template", "t");
            m.addProp("fix", fix);
        });

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/t4"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'fix'") && ex.getMessage().contains("'transform'"),
                ex.getMessage());
    }

    @Test
    public void transformWithXscriptRejected() {
        DynamicObject model = transformModel("demo/t5", m -> m.addProp("xscript", "return null;"));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/t5"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'xscript'"), ex.getMessage());
    }

    @Test
    public void transformWithTopLevelSeverityRejected() {
        DynamicObject model = transformModel("demo/t6", m -> m.addProp("severity", "warning"));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/t6"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'severity'"), ex.getMessage());
    }

    @Test
    public void transformWithMetadataSeverityRejected() {
        DynamicObject model = transformModel("demo/t7", m -> {
            DynamicObject metadata = new DynamicObject("metadata");
            metadata.addProp("category", "convention");
            metadata.addProp("severity", "warning");
            m.addProp("metadata", metadata);
        });

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/t7"), ex.getMessage());
        assertTrue(ex.getMessage().contains("severity"), ex.getMessage());
    }

    @Test
    public void transformWithExplicitAutoFixableFalseRejected() {
        DynamicObject model = transformModel("demo/t8", m -> {
            DynamicObject metadata = new DynamicObject("metadata");
            metadata.addProp("category", "convention");
            metadata.addProp("autoFixable", false);
            m.addProp("metadata", metadata);
        });

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/t8"), ex.getMessage());
        assertTrue(ex.getMessage().contains("autoFixable"), ex.getMessage());
    }

    @Test
    public void transformWithoutDescriptionRejected() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/t9");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        DynamicObject transform = new DynamicObject("transform");
        transform.addProp("template", "bar($x)");
        model.addProp("transform", transform);

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/t9"), ex.getMessage());
        assertTrue(ex.getMessage().contains("'description'"), ex.getMessage());
    }

    @Test
    public void plainFixRuleUnaffected() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/f1");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        DynamicObject fix = new DynamicObject("fix");
        fix.addProp("description", "d");
        fix.addProp("template", "t");
        fix.addProp("suggest", true);
        model.addProp("fix", fix);

        RuleDslModel parsed = parser.parseRuleModel(model);
        assertNotNull(parsed.getFix());
        assertTrue(parsed.getFix().isSuggest());
        assertNull(parsed.getTransform());
        assertNull(parsed.getMetadata(), "no metadata block stays null for non-transform rules");
    }
}
