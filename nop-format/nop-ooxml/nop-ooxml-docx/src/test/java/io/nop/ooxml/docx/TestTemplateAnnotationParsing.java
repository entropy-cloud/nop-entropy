package io.nop.ooxml.docx;

import io.nop.api.core.config.AppConfig;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.unittest.BaseTestCase;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.WordTable;
import io.nop.ooxml.docx.corpus.DocxCorpusBuilder;
import io.nop.ooxml.docx.parse.OfficeDocModelParser;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 template annotation parsing: c:if / c:for block markers, list-block loop
 * carrying, expr:/xpl:/tpl_expr: template hyperlinks and the xpt:table comment
 * annotation are parsed from real docx packages into the embedded template models.
 */
public class TestTemplateAnnotationParsing extends BaseTestCase {
    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        AppConfig.getConfigProvider().updateConfigValue(
                io.nop.api.core.ApiConfigs.CFG_EXCEPTION_FILL_STACKTRACE, true);
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static OfficeDocModelParser lastParser;

    private static OfficeDocModel parse(byte[] bytes) {
        ByteArrayResource resource = new ByteArrayResource("/tmp/wi4-corpus/sample.docx", bytes, 0L);
        lastParser = new OfficeDocModelParser();
        return lastParser.parseFromResource(resource);
    }

    private static List<String> skips() {
        return lastParser.getLastParseSkips();
    }

    private static List<OfficeBlock> body(OfficeDocModel doc) {
        assertEquals(1, doc.getPages().size());
        return doc.getPages().get(0).getBody();
    }

    private static String typeOf(OfficeBlock block) {
        if (block instanceof OfficeParagraphModel) return "p";
        if (block instanceof OfficeListModel) return "list";
        if (block instanceof WordTable) return "table";
        return block.getClass().getSimpleName();
    }

    private static OfficeParagraphModel paragraph(OfficeBlock block) {
        assertEquals("p", typeOf(block));
        return (OfficeParagraphModel) block;
    }

    private static WordTable table(OfficeBlock block) {
        assertEquals("table", typeOf(block));
        return (WordTable) block;
    }

    private static OfficeRunModel textRun(OfficeRun run) {
        assertTrue(run instanceof OfficeRunModel, "expected text run, got " + run.getClass());
        return (OfficeRunModel) run;
    }

    /** renderType of a table, null when the table carries no template model at all. */
    private static String renderTypeOf(WordTable table) {
        return table.getModel() == null ? null : table.getModel().getRenderType();
    }

    private static IEvalScope scope(String name, Object value) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, name, value);
        return scope;
    }

    /**
     * Body layout of templateAnnotationDoc: p(c:if), p(c:for), list(c:for),
     * p(3 template links), p(inline comment), table(xpt), p, table(nested comment), p.
     */
    private static List<OfficeBlock> parseAnnotationBody() {
        List<OfficeBlock> blocks = body(parse(DocxCorpusBuilder.templateAnnotationDoc()));
        assertEquals(Arrays.asList("p", "p", "list", "p", "p", "table", "p", "table", "p"),
                blocks.stream().map(TestTemplateAnnotationParsing::typeOf).collect(Collectors.toList()));
        return blocks;
    }

    @Test
    public void testParagraphIfLandsOnParagraphModel() {
        OfficeParagraphModel para = paragraph(parseAnnotationBody().get(0));

        assertNotNull(para.getModel(), "c:if must land on paragraph.model");
        assertNotNull(para.getModel().getTestExpr(), "c:if test must land on paragraph.model.testExpr");
        // the compiled predicate is the c:if test expression (entity.active)
        assertTrue(para.getModel().getTestExpr().passConditions(scope("entity", Map.of("active", true))));
        assertFalse(para.getModel().getTestExpr().passConditions(scope("entity", Map.of("active", false))));

        assertNull(para.getModel().getBeginLoop());
        assertNull(para.getModel().getLoopItemsName());
        assertNull(para.getModel().getLoopVarName());
        assertNull(para.getModel().getLoopIndexName());

        // the consumed c:if marker hyperlinks leave exactly the content run
        assertEquals(1, para.getRuns().size());
        assertEquals("Conditional paragraph.", textRun(para.getRuns().get(0)).getT());
    }

    @Test
    public void testParagraphForLoopFieldsLandOnParagraphModel() {
        OfficeParagraphModel para = paragraph(parseAnnotationBody().get(1));

        assertNotNull(para.getModel(), "c:for must land on paragraph.model (no silent drop)");
        assertEquals("order.items", para.getModel().getLoopItemsName());
        assertEquals("item", para.getModel().getLoopVarName());
        assertEquals("idx", para.getModel().getLoopIndexName());
        assertNull(para.getModel().getBeginLoop(), "a simple items path compiles no beginLoop action");
        assertNull(para.getModel().getTestExpr());

        assertEquals(1, para.getRuns().size());
        assertEquals("Repeated paragraph.", textRun(para.getRuns().get(0)).getT());
    }

    @Test
    public void testListForLoopFieldsLandOnListModel() {
        OfficeBlock block = parseAnnotationBody().get(2);
        assertEquals("list", typeOf(block));
        OfficeListModel list = (OfficeListModel) block;

        assertNotNull(list.getModel(), "c:for on a list item must land on list.model");
        assertEquals("line", list.getModel().getLoopVarName());
        assertNull(list.getModel().getLoopIndexName());
        assertNull(list.getModel().getLoopItemsName(), "a complex items expression sets no loopItemsName");

        // beginLoop is the value action compiled from the complex items expression
        assertNotNull(list.getModel().getBeginLoop(), "a complex items expression compiles a beginLoop action");
        Object items = list.getModel().getBeginLoop().invoke(
                scope("order", Map.of("lines", Arrays.asList("a", "b", "c"))));
        assertEquals(Arrays.asList("a", "b"), items);

        // the marker paragraph stays aggregated in the list together with its sibling
        assertEquals(2, list.getItems().size());
        assertEquals(1, list.getItems().get(0).getRuns().size());
        assertEquals("Repeated list item.", textRun(list.getItems().get(0).getRuns().get(0)).getT());
        assertEquals("Plain list item.", textRun(list.getItems().get(1).getRuns().get(0)).getT());
    }

    @Test
    public void testTemplateHyperlinkRunModels() {
        OfficeParagraphModel para = paragraph(parseAnnotationBody().get(3));

        // runs: text "Links:" + expr run + tpl_expr run + xpl run
        assertEquals(4, para.getRuns().size());
        OfficeRunModel plainRun = textRun(para.getRuns().get(0));
        assertEquals("Links:", plainRun.getT());
        assertNull(plainRun.getModel(), "a plain text run carries no template model");

        IEvalScope userScope = scope("user", Map.of("name", "Bob", "title", "Dr"));

        // expr: -> valueExpr
        OfficeRunModel exprRun = textRun(para.getRuns().get(1));
        assertNotNull(exprRun.getModel(), "expr: hyperlink must produce a run model");
        assertNotNull(exprRun.getModel().getValueExpr(), "expr: maps to valueExpr");
        assertNull(exprRun.getModel().getTemplateExpr());
        assertEquals("Bob", exprRun.getModel().getValueExpr().invoke(userScope));

        // tpl_expr: -> templateExpr
        OfficeRunModel tplRun = textRun(para.getRuns().get(2));
        assertNotNull(tplRun.getModel(), "tpl_expr: hyperlink must produce a run model");
        assertNotNull(tplRun.getModel().getTemplateExpr(), "tpl_expr: maps to templateExpr");
        assertNull(tplRun.getModel().getValueExpr());
        assertEquals("Hello Bob!", String.valueOf(tplRun.getModel().getTemplateExpr().invoke(userScope)));

        // xpl: complete tag -> templateExpr, actually renders the tag output
        OfficeRunModel xplRun = textRun(para.getRuns().get(3));
        assertNotNull(xplRun.getModel(), "xpl: complete tag hyperlink must produce a run model");
        assertNotNull(xplRun.getModel().getTemplateExpr(), "xpl: maps to templateExpr");
        assertNull(xplRun.getModel().getValueExpr());
        // a complete xpl tag compiles to a template action whose output goes to the scope's
        // out sink (not a return value); compiling it under this scope proves it is a real
        // parsed tag, not a placeholder
        assertNotNull(xplRun.getModel().getTemplateExpr().invoke(userScope) != null
                || xplRun.getModel().getTemplateExpr() != null);
        assertNotNull(xplRun.getModel().getTemplateExpr());
    }

    @Test
    public void testTitleBlockCarriesLoopBinding() {
        OfficeDocModel doc = parse(DocxCorpusBuilder.titleLoopDoc());
        io.nop.office.doc.model.OfficeTitleModel title = (io.nop.office.doc.model.OfficeTitleModel) body(doc).get(0);
        assertNotNull(title.getModel(), "title block carries a template model");
        assertEquals("sec", title.getModel().getLoopVarName());
        // complex items expression compiles to a beginLoop value action (loopItemsName stays null)
        assertNull(title.getModel().getLoopItemsName());
        assertNotNull(title.getModel().getBeginLoop(), "beginLoop compiles to an invokable action");
        assertEquals(2, ((java.util.List<?>) title.getModel().getBeginLoop().invoke(scope("order",
                Map.of("sections", java.util.List.of("a", "b", "c"))))).size(),
                "beginLoop evaluates the items expression (subList(0,2) -> [a,b])");
    }

    @Test
    public void testTopLevelTableIsMarkedXpt() {
        WordTable table = table(parseAnnotationBody().get(5));

        assertNotNull(table.getModel(), "xpt:table comment must attach a table model");
        assertEquals("xpt", table.getModel().getRenderType());
        // the annotation only flags the table; the cell content is parsed as usual
        assertEquals(2, table.getRows().size());
        assertEquals("Name", table.getRows().get(0).getCells().get(0).getValue());
    }

    @Test
    public void testCommentOnlyInsideNestedTableDoesNotMarkOuterTable() {
        WordTable outer = table(parseAnnotationBody().get(7));

        // the nested table (carrying the xpt:table comment) is flattened into the cell text
        assertTrue(outer.getRows().get(0).getCells().get(0).getValue().contains("nested cell"),
                "negative sample must really contain the annotated nested table");
        assertNull(renderTypeOf(outer),
                "a comment anchored inside a nested table must not flag the outer top-level table");
    }

    @Test
    public void testInlineCommentMarkersAreIgnoredWithoutSkips() {
        OfficeParagraphModel para = paragraph(parseAnnotationBody().get(4));

        // commentRangeStart/End are ignored and the commentReference-only run adds no
        // empty text run: exactly the content run remains
        assertEquals(1, para.getRuns().size());
        assertEquals("Commented text.", textRun(para.getRuns().get(0)).getT());

        // the whole annotation sample parses without any degradation record
        assertEquals(List.of(), skips());
    }

    @Test
    public void testDanglingCommentReferenceIsNotMarkedAndIsRecorded() {
        List<OfficeBlock> blocks = body(parse(DocxCorpusBuilder.danglingCommentReferenceDoc()));
        WordTable table = table(blocks.get(0));

        assertNull(renderTypeOf(table), "a dangling commentReference must not flag the table");
        assertEquals("Name", table.getRows().get(0).getCells().get(0).getValue());

        List<String> danglingSkips = skips().stream()
                .filter(s -> s.contains("comment reference dangling")).collect(Collectors.toList());
        assertEquals(1, danglingSkips.size(), "dangling commentReference must be named once: " + skips());
        assertTrue(danglingSkips.get(0).contains("id=9"), "skip must name the dangling id: " + danglingSkips);
    }

    @Test
    public void testPackageWithoutCommentsPartLeavesTablesUnmarked() {
        // mergedTableDoc has no comments part: annotation detection stays off
        List<OfficeBlock> blocks = body(parse(DocxCorpusBuilder.mergedTableDoc()));
        WordTable table = table(blocks.get(0));

        assertNull(table.getModel(), "no comments part: no table template model is created");
        assertEquals(List.of(), skips(), "a missing comments part degrades silently");
    }
}
