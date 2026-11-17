package io.nop.ib.scope;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave 12 closure-audit pins (Fix regression): isGenOrModel's XLANG_MODEL_EXT
 * rule used the file-NAME dot index against the FULL lower path — the extension
 * rule never fired for any real repo-relative path. Every pin here exercises
 * the dot branch (none matches the /_gen/, gen-* prefix or _ retention rules).
 */
class TestChangeClassifierExtRule {
    private final ChangeClassifier classifier = new ChangeClassifier();

    @Test
    void xlangExtensionOnRealRepoPathClassifiesGenOrModel() {
        assertTrue(ChangeClassifier.isGenOrModel("nop-wf/model/NopWf.xmeta", "NopWf.xmeta"));
        assertTrue(ChangeClassifier.isGenOrModel("a/b/c/app.orm.xml", "app.orm.xml"));
        assertTrue(classifier.classify("modA/model/gen.xgen", false, false, false, false)
                == ChangeClassifier.Kind.GEN_OR_MODEL);
    }

    @Test
    void negativeCasesStayOther() {
        assertFalse(ChangeClassifier.isGenOrModel("modA/src/main/java/Foo.java", "Foo.java"));
        assertFalse(ChangeClassifier.isGenOrModel("docs/readme.md", "readme.md"));
        assertFalse(classifier.classify("modA/docs/notes.txt", false, false, false, false)
                == ChangeClassifier.Kind.GEN_OR_MODEL);
    }
}
