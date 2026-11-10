package io.nop.office.doc.model;

import io.nop.commons.collections.KeyedList;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * basedOn inheritance resolution tests: scalar fields override down the chain (excluding
 * identity/meta fields), nested objects replace as a whole, default lookup works, and
 * cycles / unknown ids / cross-type inheritance fail fast.
 */
public class TestOfficeStyleResolver {

    @Test
    public void testScalarOverrideAlongBasedOnChain() {
        OfficeDocModel doc = new OfficeDocModel();

        // root: bold + size + highlight
        WordRunStyle root = new WordRunStyle();
        root.setId("base");
        root.setName("Base");
        root.setFont(new io.nop.office.model.OfficeFont());
        root.getFont().setBold(true);
        root.getFont().setFontSize(10.5f);
        root.setHighlightColor("yellow");
        doc.addStyle(root);

        // child overrides highlightColor only (scalar); keeps the whole font from root
        WordRunStyle child = new WordRunStyle();
        child.setId("child");
        child.setName("Child");
        child.setBasedOn("base");
        child.setHighlightColor("green");
        doc.addStyle(child);

        WordRunStyle resolved = new OfficeStyleResolver(doc).resolveRunStyle("child");
        assertEquals("green", resolved.getHighlightColor(), "child scalar override");
        assertTrue(resolved.getFont().isBold(), "font object inherited whole from root");
        assertEquals(10.5f, resolved.getFont().getFontSize(), 0.001, "font object inherited whole from root");
        assertEquals("child", resolved.getId(), "identity always from requested leaf");
        assertEquals("Child", resolved.getName());
    }

    @Test
    public void testExcludedIdentityFieldsNotMerged() {
        OfficeDocModel doc = new OfficeDocModel();
        WordParagraphStyle root = new WordParagraphStyle();
        root.setId("p-base");
        root.setName("Root Name");
        root.setLineSpacing(1.5);
        root.setTitleLevel(3);
        doc.addStyle(root);

        WordParagraphStyle child = new WordParagraphStyle();
        child.setId("p-child");
        child.setName("Child Para");
        child.setBasedOn("p-base");
        doc.addStyle(child);

        WordParagraphStyle resolved = new OfficeStyleResolver(doc).resolveParagraphStyle("p-child");
        assertEquals("p-child", resolved.getId(), "id excluded from merge");
        assertEquals("Child Para", resolved.getName(), "name comes from leaf, never inherited from root");
        assertNull(resolved.getBasedOn(), "basedOn excluded from merge snapshot");
        assertFalse(resolved.isDefault(), "default flag excluded from merge");
        assertEquals(1.5, resolved.getLineSpacing(), 0.001, "line spacing inherited");
        assertEquals(3, resolved.getTitleLevel(), "title level inherited");
    }

    @Test
    public void testNestedObjectReplacesWhole() {
        OfficeDocModel doc = new OfficeDocModel();
        WordRunStyle root = new WordRunStyle();
        root.setId("root");
        root.setFont(new io.nop.office.model.OfficeFont());
        root.getFont().setBold(true);
        root.getFont().setFontSize(12.0f);
        root.getFont().setFontFamily("Arial");
        doc.addStyle(root);

        WordRunStyle child = new WordRunStyle();
        child.setId("nested-child");
        child.setBasedOn("root");
        child.setFont(new io.nop.office.model.OfficeFont());
        child.getFont().setFontFamily("Times New Roman");
        doc.addStyle(child);

        WordRunStyle resolved = new OfficeStyleResolver(doc).resolveRunStyle("nested-child");
        assertEquals("Times New Roman", resolved.getFont().getFontFamily(), "nested object replaced");
        assertFalse(resolved.getFont().isBold(), "nested replace is whole-object: bold from base font gone");
        assertNull(resolved.getFont().getFontSize(), "nested replace is whole-object: size from base font gone");
    }

    @Test
    public void testMultiLevelChainAndDefaultLookup() {
        OfficeDocModel doc = new OfficeDocModel();

        WordListStyle root = new WordListStyle();
        root.setId("list-root");
        root.setNumFmt("decimal");
        root.setLvlText("%1.");
        root.setStart(3);
        root.setLevel(0);
        doc.addStyle(root);

        WordListStyle mid = new WordListStyle();
        mid.setId("list-mid");
        mid.setBasedOn("list-root");
        mid.setLvlText("[%1]");
        doc.addStyle(mid);

        WordListStyle leaf = new WordListStyle();
        leaf.setId("list-leaf");
        leaf.setBasedOn("list-mid");
        leaf.setDefault(true);
        doc.addStyle(leaf);

        OfficeStyleResolver resolver = new OfficeStyleResolver(doc);
        WordListStyle resolved = resolver.resolveListStyle("list-leaf");
        assertEquals("[%1]", resolved.getLvlText(), "mid overrides root");
        assertEquals("decimal", resolved.getNumFmt(), "root inherited through mid");
        assertEquals(3, resolved.getStart(), "root inherited through mid");
        assertEquals(0, resolved.getLevel());

        OfficeStyle def = resolver.defaultStyle(WordListStyle.class);
        assertSame(leaf, def, "default flag marks the default style of its kind");
        assertNull(resolver.defaultStyle(WordRunStyle.class), "no default run style in this doc");
    }

    @Test
    public void testCycleFailsFast() {
        OfficeDocModel doc = new OfficeDocModel();
        WordRunStyle a = new WordRunStyle();
        a.setId("cycle-a");
        a.setBasedOn("cycle-b");
        doc.addStyle(a);
        WordRunStyle b = new WordRunStyle();
        b.setId("cycle-b");
        b.setBasedOn("cycle-a");
        doc.addStyle(b);

        NopOfficeDocModelException e = assertThrows(NopOfficeDocModelException.class,
                () -> new OfficeStyleResolver(doc).resolveRunStyle("cycle-a"),
                "basedOn cycle must fail fast");
        assertTrue(StringHelper_contains(e.getMessage(), "cycle"), "error mentions cycle: " + e.getMessage());
    }

    @Test
    public void testUnknownIdAndCrossTypeFailFast() {
        OfficeDocModel doc = new OfficeDocModel();
        WordRunStyle runStyle = new WordRunStyle();
        runStyle.setId("only-run");
        doc.addStyle(runStyle);

        OfficeStyleResolver resolver = new OfficeStyleResolver(doc);
        assertThrows(NopOfficeDocModelException.class, () -> resolver.resolveRunStyle("missing"),
                "unknown style id fails fast");

        assertThrows(NopOfficeDocModelException.class, () -> resolver.resolveParagraphStyle("only-run"),
                "cross-type resolution fails fast");

        WordRunStyle badChain = new WordRunStyle();
        badChain.setId("bad");
        badChain.setBasedOn("missing-parent");
        doc.addStyle(badChain);
        assertThrows(NopOfficeDocModelException.class, () -> resolver.resolveRunStyle("bad"),
                "unknown basedOn parent fails fast");
    }

    @Test
    public void testListStyleReferencedFromListModel() {
        OfficeDocModel doc = new OfficeDocModel();

        WordListStyle listStyle = new WordListStyle();
        listStyle.setId("num-style");
        listStyle.setNumFmt("lowerLetter");
        listStyle.setLvlText("%2)");
        doc.addStyle(listStyle);

        OfficeListModel list = new OfficeListModel();
        list.setId("l1");
        list.setNumId("num1");
        list.setStyleId("num-style");

        OfficeStyleResolver resolver = new OfficeStyleResolver(doc);
        WordListStyle resolved = resolver.resolveListStyle(list.getStyleId());
        assertEquals("lowerLetter", resolved.getNumFmt());
        assertEquals("%2)", resolved.getLvlText());
    }

    private static boolean StringHelper_contains(String s, String part) {
        return s != null && s.contains(part);
    }
}
