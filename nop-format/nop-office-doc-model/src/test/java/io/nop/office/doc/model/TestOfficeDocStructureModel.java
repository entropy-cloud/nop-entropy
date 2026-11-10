package io.nop.office.doc.model;

import io.nop.commons.bytes.ByteString;

import java.util.ArrayList;
import java.util.List;
import io.nop.commons.collections.KeyedList;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI structure-model tests: all typed blocks/runs/page-setup/assets are constructible,
 * makeModel/clone/freeze follow the nop-excel model conventions, and the extended
 * doc.xdef really deserializes the new tags into the typed model classes.
 */
public class TestOfficeDocStructureModel {
    static final String SCHEMA = "/nop/schema/office/doc.xdef";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testMakeModelLazyAndIdempotent() {
        OfficeParagraphModel p = new OfficeParagraphModel();
        p.setId("p1");
        p.setType("p");
        assertNull(p.getModel(), "no template model until requested");
        OfficeParagraphTemplateModel m1 = p.makeModel();
        OfficeParagraphTemplateModel m2 = p.makeModel();
        assertSame(m1, m2, "makeModel must be idempotent");
        assertSame(m1, p.getModel());

        // same convention for all typed blocks with template semantics
        OfficeTitleModel title = new OfficeTitleModel();
        assertSame(title.makeModel(), title.makeModel());
        OfficeListModel list = new OfficeListModel();
        assertSame(list.makeModel(), list.makeModel());
    }

    @Test
    public void testAllBlockAndRunTypesBuildable() {
        OfficeDocModel doc = newDocWithAllBlockTypes();
        OfficeDocPageModel page = doc.getPage("body-page");

        assertEquals(8, page.getBody().size(), "all block kinds present");
        assertTrue(page.getBody().get(0) instanceof OfficeParagraphModel);
        assertTrue(page.getBody().get(1) instanceof WordTable);
        assertTrue(page.getBody().get(2) instanceof OfficeTitleModel);
        assertTrue(page.getBody().get(3) instanceof OfficeListModel);
        assertTrue(page.getBody().get(4) instanceof OfficeImageModel);
        assertTrue(page.getBody().get(5) instanceof OfficeHyperlinkModel);
        assertTrue(page.getBody().get(6) instanceof OfficeSeparatorModel);
        assertTrue(page.getBody().get(7) instanceof OfficePageBreakModel);

        // typed runs inside the paragraph
        OfficeParagraphModel p = (OfficeParagraphModel) page.getBody().get(0);
        assertEquals(4, p.getRuns().size());
        assertTrue(p.getRuns().get(0) instanceof OfficeRunModel);
        assertTrue(p.getRuns().get(1) instanceof OfficeTabModel);
        assertTrue(p.getRuns().get(2) instanceof OfficeBreakModel);
        assertEquals("page", ((OfficeBreakModel) p.getRuns().get(2)).getBrType());

        // recursive link runs
        OfficeLinkModel link = (OfficeLinkModel) p.getRuns().get(3);
        assertEquals("https://example.com", link.getHref());
        assertEquals("link text", ((OfficeRunModel) link.getRuns().get(0)).getT());
    }

    @Test
    public void testPageSetupAndAssets() {
        OfficeDocModel doc = newDocWithAllBlockTypes();
        assertEquals(11906.0, doc.getPage("body-page").getWidth(), 0.001);
        assertEquals(16838.0, doc.getPage("body-page").getHeight(), 0.001);
        assertNotNull(doc.getPage("body-page").getMargins());
        assertEquals(1417.0, doc.getPage("body-page").getMargins().getLeft(), 0.001);
        // doc-level default page size still present
        assertEquals(11906.0, doc.getWidth(), 0.001);

        OfficeImageAsset asset = doc.getAsset("img1");
        assertNotNull(asset);
        assertEquals("png", asset.getExt());
        assertEquals(4, asset.getData().toByteArray().length);
    }

    @Test
    public void testRunsKeyedListDedupById() {
        OfficeParagraphModel p = new OfficeParagraphModel();
        p.addRun(textRun("r1", "one"));
        p.addRun(textRun("r1", "one-replaced"));
        assertEquals(1, p.getRuns().size(), "same id replaces, not duplicates");
        assertEquals("one-replaced", ((OfficeRunModel) p.getRuns().get(0)).getT());
    }

    /**
     * cloneInstance follows the platform convention (same as ExcelWorkbook): copyTo is a
     * shallow copy — scalars are copied, child containers are shared by reference. Deep
     * freezing is the job of freeze(true).
     */
    @Test
    public void testCloneInstanceShallowConvention() {
        OfficeDocModel doc = newDocWithAllBlockTypes();
        OfficeDocModel copy = (OfficeDocModel) doc.cloneInstance();
        assertNotSame(doc, copy);
        assertEquals(copy.getWidth(), doc.getWidth(), 0.001);
        assertSame(copy.getPage("body-page"), doc.getPage("body-page"),
                "shallow copy: child containers shared by reference");
        assertSame(copy.getAsset("img1"), doc.getAsset("img1"));
        assertEquals("png", copy.getAsset("img1").getExt());
    }

    @Test
    public void testFreezeDisallowsMutation() {
        OfficeDocModel doc = newDocWithAllBlockTypes();
        doc.freeze(true);
        assertThrows(Exception.class, () -> doc.setWidth(1.0));
        OfficeParagraphModel p = (OfficeParagraphModel) doc.getPage("body-page").getBody().get(0);
        assertThrows(Exception.class, () -> p.addRun(textRun("x", "y")));
    }

    /**
     * True xdef smoke: raw instance XML (not produced by a model) containing every new
     * tag deserializes into the typed model classes — proves the schema-to-code wiring.
     */
    @Test
    public void testXdefDeserializesNewTags() {
        String xml = "<doc x:schema=\"" + SCHEMA + "\" xmlns:x=\"/nop/schema/xdsl.xdef\""
                + " width=\"11906\" height=\"16838\">"
                + "<assets><asset name=\"img1\" ext=\"png\"><data>AAAAAA==</data></asset></assets>"
                + "<pages><page name=\"p\" orientation=\"landscape\" width=\"16838\" height=\"11906\">"
                + "<margins left=\"1417\" top=\"1000\"/>"
                + "<body>"
                + "<title id=\"t1\"><runs><r id=\"r1\"><t>hello title</t></r></runs></title>"
                + "<list id=\"l1\" numId=\"n1\"><items>"
                + "<item id=\"i1\" level=\"1\"><runs><r id=\"r2\"><t>item text</t></r></runs></item>"
                + "</items></list>"
                + "<image id=\"img\" assetRef=\"img1\" width=\"100\" height=\"50\"/>"
                + "<hyperlink id=\"h1\" href=\"https://example.com\"><runs>"
                + "<link id=\"lk1\" href=\"inner\"><runs><r id=\"r3\"><t>inner link</t></r></runs></link>"
                + "</runs></hyperlink>"
                + "<separator id=\"s1\"/>"
                + "<pageBreak id=\"pb1\"/>"
                + "<p id=\"p1\"><runs>"
                + "<r id=\"ra\"><t>text</t></r><tab id=\"tb\"/><br id=\"br1\" brType=\"page\"/>"
                + "<link id=\"ln\" href=\"https://x\"><runs><r id=\"rc\"><t>link text</t></r></runs></link>"
                + "</runs></p>"
                + "</body>"
                + "</page></pages></doc>";

        XNode node = XNode.parse(xml);
        OfficeDocModel doc = (OfficeDocModel) new DslModelParser().parseFromNode(node);

        OfficeDocPageModel page = doc.getPage("p");
        assertEquals("landscape", page.getOrientation());

        assertEquals(7, page.getBody().size());
        OfficeTitleModel title = (OfficeTitleModel) page.getBody().get(0);
        assertEquals("hello title", ((OfficeRunModel) title.getRuns().get(0)).getT());

        OfficeListModel list = (OfficeListModel) page.getBody().get(1);
        assertEquals("n1", list.getNumId());
        OfficeListItemModel item = list.getItems().get(0);
        assertEquals(1, item.getLevel());
        assertEquals("item text", ((OfficeRunModel) item.getRuns().get(0)).getT());

        OfficeImageModel image = (OfficeImageModel) page.getBody().get(2);
        assertEquals("img1", image.getAssetRef());
        assertEquals(100.0, image.getWidth(), 0.001);

        OfficeHyperlinkModel hyperlink = (OfficeHyperlinkModel) page.getBody().get(3);
        assertEquals("https://example.com", hyperlink.getHref());
        assertTrue(hyperlink.getRuns().get(0) instanceof OfficeLinkModel,
                "nested link inside hyperlink dispatches typed");

        assertTrue(page.getBody().get(4) instanceof OfficeSeparatorModel);
        assertTrue(page.getBody().get(5) instanceof OfficePageBreakModel);

        OfficeParagraphModel p = (OfficeParagraphModel) page.getBody().get(6);
        assertTrue(p.getRuns().get(0) instanceof OfficeRunModel);
        assertTrue(p.getRuns().get(1) instanceof OfficeTabModel);
        OfficeBreakModel br = (OfficeBreakModel) p.getRuns().get(2);
        assertEquals("page", br.getBrType());
        OfficeLinkModel link = (OfficeLinkModel) p.getRuns().get(3);
        assertEquals("https://x", link.getHref());
        assertEquals("link text", ((OfficeRunModel) link.getRuns().get(0)).getT());

        OfficeImageAsset asset = doc.getAsset("img1");
        assertNotNull(asset);
        assertEquals(4, asset.getData().toByteArray().length, "base64-bytes data decodes to byte[]");
    }

    /**
     * Model → XNode (doc.xdef) → parse back keeps the typed structure: serialization
     * contract of the extended schema.
     */
    @Test
    public void testRoundTripThroughXdef() {
        OfficeDocModel doc = newDocWithAllBlockTypes();
        // WordTable (word-table.xdef) carries no type prop for union serialization; the
        // table round-trip contract is covered by WI writer/round-trip tests
        doc.getPage("body-page").getBody().remove(1);
        XNode node = DslModelHelper.dslModelToXNode(SCHEMA, doc);
        OfficeDocModel parsed = (OfficeDocModel) new DslModelParser().parseFromNode(node);

        OfficeDocPageModel page = parsed.getPage("body-page");
        assertTrue(page.getBody().get(1) instanceof OfficeTitleModel);
        assertTrue(page.getBody().get(2) instanceof OfficeListModel);
        assertTrue(page.getBody().get(3) instanceof OfficeImageModel);
        assertTrue(page.getBody().get(4) instanceof OfficeHyperlinkModel);
        assertTrue(page.getBody().get(5) instanceof OfficeSeparatorModel);
        assertTrue(page.getBody().get(6) instanceof OfficePageBreakModel);

        OfficeParagraphModel p = (OfficeParagraphModel) page.getBody().get(0);
        assertTrue(p.getRuns().get(1) instanceof OfficeTabModel);
        assertTrue(p.getRuns().get(3) instanceof OfficeLinkModel);
        assertNotNull(parsed.getAsset("img1"));
        assertEquals(4, parsed.getAsset("img1").getData().toByteArray().length);
    }

    // ------------------------------------------------------------------ helpers

    private OfficeRunModel textRun(String id, String text) {
        OfficeRunModel run = new OfficeRunModel();
        run.setId(id);
        run.setType("r");
        run.setT(text);
        return run;
    }

    private OfficeDocModel newDocWithAllBlockTypes() {
        OfficeDocModel doc = new OfficeDocModel();
        doc.setWidth(11906.0);
        doc.setHeight(16838.0);

        OfficeImageAsset asset = new OfficeImageAsset();
        asset.setName("img1");
        asset.setExt("png");
        asset.setData(ByteString.of(new byte[]{1, 2, 3, 4}));
        doc.addAsset(asset);

        List<OfficeBlock> blocks = new ArrayList<>();
        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setName("body-page");
        page.setOrientation("portrait");
        page.setWidth(11906.0);
        page.setHeight(16838.0);
        OfficePageMargins margins = new OfficePageMargins();
        margins.setTop(1417.0);
        margins.setLeft(1417.0);
        margins.setBottom(1417.0);
        margins.setRight(1417.0);
        page.setMargins(margins);

        OfficeParagraphModel p = new OfficeParagraphModel();
        p.setId("p1");
        p.setType("p");
        p.addRun(textRun("r1", "text"));
        OfficeTabModel tab = new OfficeTabModel();
        tab.setId("t1");
        tab.setType("tab");
        p.addRun(tab);
        OfficeBreakModel br = new OfficeBreakModel();
        br.setId("b1");
        br.setType("br");
        br.setBrType("page");
        p.addRun(br);
        OfficeLinkModel link = new OfficeLinkModel();
        link.setId("lk1");
        link.setType("link");
        link.setHref("https://example.com");
        link.addRun(textRun("lr1", "link text"));
        p.addRun(link);
        blocks.add(p);

        WordTable table = new WordTable();
        table.setId("tbl1");
        blocks.add(table);

        OfficeTitleModel title = new OfficeTitleModel();
        title.setId("title1");
        title.setType("title");
        title.addRun(textRun("tr1", "title text"));
        blocks.add(title);

        OfficeListModel list = new OfficeListModel();
        list.setId("list1");
        list.setType("list");
        list.setNumId("num1");
        OfficeListItemModel item = new OfficeListItemModel();
        item.setId("item1");
        item.setLevel(1);
        item.addRun(textRun("ir1", "item text"));
        list.setItems(new KeyedList<>(OfficeListItemModel::getId));
        list.getItems().add(item); // KeyedList from constructor is mutable
        blocks.add(list);

        OfficeImageModel image = new OfficeImageModel();
        image.setId("img1");
        image.setType("image");
        image.setAssetRef("img1");
        image.setWidth(100.0);
        image.setHeight(50.0);
        blocks.add(image);

        OfficeHyperlinkModel hyperlink = new OfficeHyperlinkModel();
        hyperlink.setId("h1");
        hyperlink.setType("hyperlink");
        hyperlink.setHref("https://example.com");
        hyperlink.addRun(textRun("hr1", "hyperlink text"));
        blocks.add(hyperlink);

        OfficeSeparatorModel separator = new OfficeSeparatorModel();
        separator.setId("sep1");
        separator.setType("separator");
        blocks.add(separator);

        OfficePageBreakModel pageBreak = new OfficePageBreakModel();
        pageBreak.setId("pb1");
        pageBreak.setType("pageBreak");
        blocks.add(pageBreak);

        page.setBody(blocks);
        doc.addPage(page);
        return doc;
    }
}
