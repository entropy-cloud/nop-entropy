package io.nop.ooxml.docx;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ClassPathResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.ooxml.docx.model.WordHyperlink;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRunTemplateModel;
import io.nop.office.doc.model.WordTableTemplateModel;
import io.nop.office.doc.model.WordTable;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.api.core.ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_END_NOT_MATCH;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_BEGIN_NO_END;
import static io.nop.ooxml.docx.DocxErrors.ERR_DOCX_XPL_END_NO_BEGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOfficeDocModelParser extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().updateConfigValue(CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testParseBodyAndConfig() {
        IResource resource = new ClassPathResource("classpath:docx/test-tpl.docx");
        OfficeDocModel doc = new OfficeDocModelParser().parseFromResource(resource);

        assertNotNull(doc.getModel());
        assertNotNull(doc.getPages());
        assertEquals(1, doc.getPages().size());

        OfficeDocPageModel page = doc.getPages().get(0);
        assertFalse(page.getBody().isEmpty());
        assertTrue(page.getBody().stream().anyMatch(block -> block instanceof OfficeParagraphModel));
        assertTrue(page.getBody().stream().anyMatch(block -> block instanceof WordTable));
    }

    @Test
    public void testParseHyperlinkTemplateSemantics() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        OfficeRunTemplateModel exprModel = new OfficeRunTemplateModel();
        parser.applyExprLink(exprModel, "entity.name");
        assertNotNull(exprModel.getValueExpr());

        OfficeRunTemplateModel tplModel = new OfficeRunTemplateModel();
        parser.applyTplLink(tplModel, "${entity.name}");
        assertNotNull(tplModel.getTemplateExpr());
    }

    @Test
    public void testParseHeaderFooter() {
        IResource resource = new ClassPathResource("classpath:docx/test-header-footer.docx");
        OfficeDocModel doc = new OfficeDocModelParser().parseFromResource(resource);

        OfficeDocPageModel page = doc.getPages().get(0);
        assertFalse(page.getHeader().isEmpty());
        assertFalse(page.getFooter().isEmpty());
        assertNull(page.getOrientation());
    }

    @Test
    public void testMapParagraphIfToTestExpr() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode para = makeParagraph(body, "p1", "Shown");
        makeHyperlink(para, "xpl:<c:if test=\"${entity.show}\">", "begin");
        makeHyperlink(para, "xpl:</c:if>", "end");

        OfficeParagraphModel model = (OfficeParagraphModel) parser.parseBody(body).get(0);
        assertNotNull(model.getModel());
        assertNotNull(model.getModel().getTestExpr());
        assertEquals(1, model.getRuns().size());
    }

    @Test
    public void testMapTableIfToTestExpr() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode table = makeTable(body, "Cell");
        XNode firstPara = table.find(node -> "w:p".equals(node.getTagName()));
        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.hasTable}\">", "begin");
        makeHyperlink(firstPara, "xpl:</c:if>", "end");

        WordTable model = (WordTable) parser.parseBody(body).get(0);
        WordTableTemplateModel tableModel = model.getModel();
        assertNotNull(tableModel);
        assertNotNull(tableModel.getTestExpr());
    }

    @Test
    public void testMapParagraphIfVisibleToVisibleExpr() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode para = makeParagraph(body, "p1", "Shown");
        makeHyperlink(para, "xpl:<c:if visible=\"${entity.show}\">", "begin");
        makeHyperlink(para, "xpl:</c:if>", "end");

        OfficeParagraphModel model = (OfficeParagraphModel) parser.parseBody(body).get(0);
        assertNotNull(model.getModel());
        assertNotNull(model.getModel().getVisibleExpr());
        assertNull(model.getModel().getTestExpr());
    }

    @Test
    public void testNestedParagraphIfTestsAreCombined() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode para = makeParagraph(body, "p1", "Shown");
        makeHyperlink(para, "xpl:<c:if test=\"${entity.show}\">", "outer-begin");
        makeHyperlink(para, "xpl:<c:if test=\"${entity.enabled}\">", "inner-begin");
        makeHyperlink(para, "xpl:</c:if>", "inner-end");
        makeHyperlink(para, "xpl:</c:if>", "outer-end");

        OfficeParagraphModel model = (OfficeParagraphModel) parser.parseBody(body).get(0);
        assertNotNull(model.getModel());
        assertNotNull(model.getModel().getTestExpr());

        IEvalScope bothTrue = XLang.newEvalScope();
        bothTrue.setLocalValue(null, "entity", java.util.Map.of("show", true, "enabled", true));
        assertTrue(model.getModel().getTestExpr().passConditions(bothTrue));

        IEvalScope oneFalse = XLang.newEvalScope();
        oneFalse.setLocalValue(null, "entity", java.util.Map.of("show", true, "enabled", false));
        assertFalse(model.getModel().getTestExpr().passConditions(oneFalse));
    }

    @Test
    public void testNestedTableIfTestsAreCombined() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode table = makeTable(body, "Cell");
        XNode firstPara = table.find(node -> "w:p".equals(node.getTagName()));
        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.hasTable}\">", "outer-begin");
        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.enabled}\">", "inner-begin");
        makeHyperlink(firstPara, "xpl:</c:if>", "inner-end");
        makeHyperlink(firstPara, "xpl:</c:if>", "outer-end");

        WordTable model = (WordTable) parser.parseBody(body).get(0);
        assertNotNull(model.getModel());
        assertNotNull(model.getModel().getTestExpr());

        IEvalScope bothTrue = XLang.newEvalScope();
        bothTrue.setLocalValue(null, "entity", java.util.Map.of("hasTable", true, "enabled", true));
        assertTrue(model.getModel().getTestExpr().passConditions(bothTrue));

        IEvalScope oneFalse = XLang.newEvalScope();
        oneFalse.setLocalValue(null, "entity", java.util.Map.of("hasTable", true, "enabled", false));
        assertFalse(model.getModel().getTestExpr().passConditions(oneFalse));
    }

    @Test
    public void testMapPageIfToTestExpr() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        XNode lastPara = makeParagraph(body, "p2", "Second");
        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.show}\">", "begin");
        makeHyperlink(lastPara, "xpl:</c:if>", "end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertNotNull(page.getModel());
        assertNotNull(page.getModel().getTestExpr());
        assertNull(page.getModel().getBeginLoop());
        assertNull(page.getModel().getLoopItemsName());
    }

    @Test
    public void testNestedPageIfTestsAreCombined() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        makeParagraph(body, "p2", "Middle");
        XNode lastPara = makeParagraph(body, "p3", "Last");

        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.show}\">", "outer-begin");
        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.enabled}\">", "inner-begin");
        makeHyperlink(lastPara, "xpl:</c:if>", "inner-end");
        makeHyperlink(lastPara, "xpl:</c:if>", "outer-end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertNotNull(page.getModel());
        assertNotNull(page.getModel().getTestExpr());

        IEvalScope bothTrue = XLang.newEvalScope();
        bothTrue.setLocalValue(null, "entity", java.util.Map.of("show", true, "enabled", true));
        assertTrue(page.getModel().getTestExpr().passConditions(bothTrue));

        IEvalScope oneFalse = XLang.newEvalScope();
        oneFalse.setLocalValue(null, "entity", java.util.Map.of("show", true, "enabled", false));
        assertFalse(page.getModel().getTestExpr().passConditions(oneFalse));
    }

    @Test
    public void testMapPageForToLoopFields() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        makeParagraph(body, "p2", "Second");
        makeHyperlink(firstPara, "xpl:<c:for var=\"item\" items=\"${entity.items}\" index=\"idx\">", "begin");
        XNode lastPara = body.child(body.getChildCount() - 1);
        makeHyperlink(lastPara, "xpl:</c:for>", "end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertNotNull(page.getModel());
        assertEquals("entity.items", page.getModel().getLoopItemsName());
        assertEquals("item", page.getModel().getLoopVarName());
        assertEquals("idx", page.getModel().getLoopIndexName());
        assertNull(page.getModel().getBeginLoop());
    }

    @Test
    public void testMapPageForToBeginLoopWhenItemsIsComplexExpr() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        makeParagraph(body, "p2", "Second");
        makeHyperlink(firstPara, "xpl:<c:for var=\"item\" items=\"${entity.items.filter(x => x.active)}\">", "begin");
        XNode lastPara = body.child(body.getChildCount() - 1);
        makeHyperlink(lastPara, "xpl:</c:for>", "end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertNotNull(page.getModel());
        assertNull(page.getModel().getLoopItemsName());
        assertNotNull(page.getModel().getBeginLoop());
    }

    @Test
    public void testNestedPageForAndParagraphIf() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        XNode lastPara = makeParagraph(body, "p2", "Last");

        makeHyperlink(firstPara, "xpl:<c:for var=\"item\" items=\"${entity.items}\">", "for-begin");
        makeHyperlink(firstPara, "xpl:<c:if test=\"${item.show}\">", "if-begin");
        makeHyperlink(firstPara, "xpl:</c:if>", "if-end");
        makeHyperlink(lastPara, "xpl:</c:for>", "for-end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertNotNull(page.getModel());
        assertEquals("entity.items", page.getModel().getLoopItemsName());

        OfficeParagraphModel para = (OfficeParagraphModel) page.getBody().get(0);
        assertNotNull(para.getModel());
        assertNotNull(para.getModel().getTestExpr());
    }

    @Test
    public void testUnsupportedCrossBlockPairIsIgnored() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        XNode lastPara = makeParagraph(body, "p2", "Last");

        makeHyperlink(firstPara, "xpl:<orm-docx:for-each-table xpl:slotScope=\"table\">", "begin");
        makeHyperlink(lastPara, "xpl:</orm-docx:for-each-table>", "end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertEquals(2, page.getBody().size());
        assertNull(page.getModel());

        OfficeParagraphModel para = (OfficeParagraphModel) page.getBody().get(0);
        assertNull(para.getModel());
    }

    @Test
    public void testNestedPageIfAndPageForUseIndependentPairs() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        makeParagraph(body, "p2", "Middle");
        XNode lastPara = makeParagraph(body, "p3", "Last");

        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.visible}\">", "if-begin");
        makeHyperlink(firstPara, "xpl:<c:for var=\"item\" items=\"${entity.items}\">", "for-begin");
        makeHyperlink(lastPara, "xpl:</c:for>", "for-end");
        makeHyperlink(lastPara, "xpl:</c:if>", "if-end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertNotNull(page.getModel());
        assertNotNull(page.getModel().getTestExpr());
        assertEquals("entity.items", page.getModel().getLoopItemsName());
    }

    @Test
    public void testUnsupportedOuterPairDoesNotBlockSupportedInnerPair() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode firstPara = makeParagraph(body, "p1", "First");
        XNode lastPara = makeParagraph(body, "p2", "Last");

        makeHyperlink(firstPara, "xpl:<orm-docx:for-each-table xpl:slotScope=\"table\">", "outer-begin");
        makeHyperlink(firstPara, "xpl:<c:if test=\"${entity.show}\">", "inner-begin");
        makeHyperlink(firstPara, "xpl:</c:if>", "inner-end");
        makeHyperlink(lastPara, "xpl:</orm-docx:for-each-table>", "outer-end");

        OfficeDocPageModel page = parser.parsePage(body);
        assertNull(page.getModel());

        OfficeParagraphModel para = (OfficeParagraphModel) page.getBody().get(0);
        assertNotNull(para.getModel());
        assertNotNull(para.getModel().getTestExpr());
    }

    @Test
    public void testEndWithoutBeginThrows() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode para = makeParagraph(body, "p1", "Only end");
        makeHyperlink(para, "xpl:</c:if>", "end");

        try {
            parser.parseBody(body);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(ERR_DOCX_XPL_END_NO_BEGIN.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testBeginWithoutEndThrows() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode para = makeParagraph(body, "p1", "Only begin");
        makeHyperlink(para, "xpl:<c:if test=\"${entity.show}\">", "begin");

        try {
            parser.parseBody(body);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(ERR_DOCX_XPL_BEGIN_NO_END.getErrorCode(), e.getErrorCode());
        }
    }

    @Test
    public void testMismatchedBeginEndThrows() {
        TestableOfficeDocModelParser parser = new TestableOfficeDocModelParser();

        XNode body = XNode.make("w:body");
        XNode para = makeParagraph(body, "p1", "Mismatch");
        makeHyperlink(para, "xpl:<c:if test=\"${entity.show}\">", "begin");
        makeHyperlink(para, "xpl:</c:for>", "end");

        try {
            parser.parseBody(body);
            assertTrue(false);
        } catch (NopException e) {
            assertEquals(ERR_DOCX_XPL_BEGIN_END_NOT_MATCH.getErrorCode(), e.getErrorCode());
        }
    }

    private static XNode makeParagraph(XNode parent, String paraId, String text) {
        XNode para = parent.addChild("w:p");
        para.setAttr("w14:paraId", paraId);
        XNode run = para.addChild("w:r");
        run.makeChild("w:t").content(text);
        return para;
    }

    private static XNode makeHyperlink(XNode para, String target, String label) {
        XNode link = para.addChild("w:hyperlink");
        link.setAttr("url", target);
        XNode run = link.addChild("w:r");
        run.makeChild("w:t").content(label);
        return link;
    }

    private static XNode makeTable(XNode parent, String text) {
        XNode table = parent.makeChild("w:tbl");
        table.makeChild("w:tblGrid").makeChild("w:gridCol").setAttr("w:w", "2400");
        XNode row = table.makeChild("w:tr");
        XNode cell = row.makeChild("w:tc");
        makeParagraph(cell, "cell-p1", text);
        return table;
    }

    static class TestableOfficeDocModelParser extends OfficeDocModelParser {
        void applyExprLink(OfficeRunTemplateModel model, String source) {
            WordHyperlink link = buildLink("expr:" + source, source);
            applyHyperlinkModel(model, link, new io.nop.ooxml.common.gen.XplGenConfig(),
                    XLang.newCompileTool().allowUnregisteredScopeVar(true), null);
        }

        void applyTplLink(OfficeRunTemplateModel model, String source) {
            WordHyperlink link = buildLink("xpl:" + source, source);
            applyHyperlinkModel(model, link, new io.nop.ooxml.common.gen.XplGenConfig(),
                    XLang.newCompileTool().allowUnregisteredScopeVar(true), null);
        }

        List<io.nop.office.doc.model.OfficeBlock> parseBody(XNode body) {
            return parseBlocks(new io.nop.ooxml.docx.model.WordOfficePackage(), "word/document.xml", body, null,
                    new io.nop.ooxml.common.gen.XplGenConfig(),
                    XLang.newCompileTool().allowUnregisteredScopeVar(true));
        }

        OfficeDocPageModel parsePage(XNode body) {
            OfficeDocPageModel page = new OfficeDocPageModel();
            page.setName("page1");
            page.setBody(parseBlocks(new io.nop.ooxml.docx.model.WordOfficePackage(), "word/document.xml", body, page,
                    new io.nop.ooxml.common.gen.XplGenConfig(),
                    XLang.newCompileTool().allowUnregisteredScopeVar(true)));
            return page;
        }

        private WordHyperlink buildLink(String target, String label) {
            XNode linkNode = XNode.make("w:hyperlink");
            XNode runNode = linkNode.makeChild("w:r");
            runNode.makeChild("w:t").content(label);
            return WordHyperlink.build(
                    new OfficeRelationship(null, "rId1", OfficeConstants.NS_LINK, target, null),
                    label, linkNode, new io.nop.ooxml.common.gen.XplGenConfig());
        }
    }
}
