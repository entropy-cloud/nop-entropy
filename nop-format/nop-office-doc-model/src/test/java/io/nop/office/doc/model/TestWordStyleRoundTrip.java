package io.nop.office.doc.model;

import io.nop.core.initialize.CoreInitialization;
import io.nop.office.model.OfficeStyle;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordRunStyle;
import io.nop.office.model.WordTableStyle;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.xlang.xdsl.DslModelParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Named-style round-trip through the doc.xdef styles container: doc model → XNode →
 * parse back keeps the five-way style dispatch, basedOn chains, default flags and the
 * new paragraph titleLevel / list styleId fields — and the parsed model still resolves
 * through OfficeStyleResolver.
 */
public class TestWordStyleRoundTrip {
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
    public void testNamedStylesRoundTripAndResolve() {
        OfficeDocModel doc = new OfficeDocModel();
        doc.setWidth(11906.0);
        doc.setHeight(16838.0);

        WordRunStyle runBase = new WordRunStyle();
        runBase.setId("run-base");
        runBase.setType("runStyle");
        runBase.setName("Run Base");
        runBase.setFont(new io.nop.office.model.OfficeFont());
        runBase.getFont().setBold(true);
        runBase.getFont().setFontSize(10.5f);
        doc.addStyle(runBase);

        WordRunStyle runChild = new WordRunStyle();
        runChild.setId("run-child");
        runChild.setType("runStyle");
        runChild.setBasedOn("run-base");
        runChild.setFont(new io.nop.office.model.OfficeFont());
        runChild.getFont().setFontSize(14.0f);
        doc.addStyle(runChild);

        WordParagraphStyle paraStyle = new WordParagraphStyle();
        paraStyle.setId("para-title");
        paraStyle.setType("paragraphStyle");
        paraStyle.setName("Title Para");
        paraStyle.setTitleLevel(2);
        paraStyle.setAlign(io.nop.office.model.constants.OfficeHorizontalAlignment.CENTER);
        paraStyle.setDefault(true);
        doc.addStyle(paraStyle);

        WordListStyle listStyle = new WordListStyle();
        listStyle.setId("list-decimal");
        listStyle.setType("listStyle");
        listStyle.setNumFmt("decimal");
        listStyle.setLvlText("%1.");
        listStyle.setStart(4);
        doc.addStyle(listStyle);

        OfficeDocPageModel page = new OfficeDocPageModel();
        page.setName("p");
        OfficeListModel list = new OfficeListModel();
        list.setId("l1");
        list.setType("list");
        list.setNumId("num1");
        list.setStyleId("list-decimal");
        java.util.List<OfficeBlock> blocks = new java.util.ArrayList<>();
        blocks.add(list);
        page.setBody(blocks);
        doc.addPage(page);

        XNode node = DslModelHelper.dslModelToXNode(SCHEMA, doc);
        OfficeDocModel parsed = (OfficeDocModel) new DslModelParser().parseFromNode(node);

        // five-way dispatch preserved (4 kinds used here; list kind exercised separately below)
        OfficeStyle parsedRunChild = parsed.getStyle("run-child");
        assertInstanceOf(WordRunStyle.class, parsedRunChild);
        OfficeStyle parsedPara = parsed.getStyle("para-title");
        assertInstanceOf(WordParagraphStyle.class, parsedPara);
        OfficeStyle parsedList = parsed.getStyle("list-decimal");
        assertInstanceOf(WordListStyle.class, parsedList);

        WordRunStyle runChildBack = (WordRunStyle) parsedRunChild;
        assertEquals("run-base", runChildBack.getBasedOn(), "basedOn chain survives");
        assertEquals(14.0f, runChildBack.getFont().getFontSize(), 0.001);

        WordParagraphStyle paraBack = (WordParagraphStyle) parsedPara;
        assertEquals(2, paraBack.getTitleLevel(), "titleLevel survives");
        assertEquals(io.nop.office.model.constants.OfficeHorizontalAlignment.CENTER, paraBack.getAlign());
        assertTrue(paraBack.isDefault(), "default flag survives");

        WordListStyle listBack = (WordListStyle) parsedList;
        assertEquals("decimal", listBack.getNumFmt());
        assertEquals("%1.", listBack.getLvlText());
        assertEquals(4, listBack.getStart());

        // list block still references the named list style after round-trip
        OfficeListModel listBack2 = (OfficeListModel) parsed.getPage("p").getBody().get(0);
        assertEquals("list-decimal", listBack2.getStyleId());
        assertEquals("num1", listBack2.getNumId());

        // the parsed model resolves through the inheritance resolver: font whole-replace
        OfficeStyleResolver resolver = new OfficeStyleResolver(parsed);
        WordRunStyle resolved = resolver.resolveRunStyle("run-child");
        assertNotNull(resolved);
        assertEquals(14.0f, resolved.getFont().getFontSize(), 0.001);
        assertEquals("run-child", resolved.getId());
    }

    @Test
    public void testRawXmlStylesDeserializeTyped() {
        String xml = "<doc x:schema=\"" + SCHEMA + "\" xmlns:x=\"/nop/schema/xdsl.xdef\""
                + " width=\"11906\" height=\"16838\">"
                + "<styles>"
                + "<runStyle id=\"r1\" name=\"Run One\" basedOn=\"r0\" default=\"true\">"
                + "<font bold=\"true\" fontSize=\"12\"/></runStyle>"
                + "<paragraphStyle id=\"p1\" titleLevel=\"3\" default=\"true\"/>"
                + "<cellStyle id=\"c1\" backgroundColor=\"F2F2F2\"/>"
                + "<tableStyle id=\"t1\" width=\"400\"/>"
                + "<listStyle id=\"ls1\" numFmt=\"lowerRoman\" lvlText=\"(%1)\" start=\"2\" level=\"1\"/>"
                + "</styles>"
                + "<pages><page name=\"p\"/></pages></doc>";

        XNode node = XNode.parse(xml);
        OfficeDocModel parsed = (OfficeDocModel) new DslModelParser().parseFromNode(node);

        assertInstanceOf(WordRunStyle.class, parsed.getStyle("r1"));
        assertInstanceOf(WordParagraphStyle.class, parsed.getStyle("p1"));
        assertInstanceOf(WordCellStyle.class, parsed.getStyle("c1"));
        assertInstanceOf(WordTableStyle.class, parsed.getStyle("t1"));
        assertInstanceOf(WordListStyle.class, parsed.getStyle("ls1"));

        WordRunStyle run = (WordRunStyle) parsed.getStyle("r1");
        assertTrue(run.getFont().isBold());
        assertEquals(12.0f, run.getFont().getFontSize(), 0.001);
        assertEquals("r0", run.getBasedOn());
        assertTrue(run.isDefault());

        WordParagraphStyle para = (WordParagraphStyle) parsed.getStyle("p1");
        assertEquals(3, para.getTitleLevel());
        assertTrue(para.isDefault());

        WordListStyle listStyle = (WordListStyle) parsed.getStyle("ls1");
        assertEquals("lowerRoman", listStyle.getNumFmt());
        assertEquals("(%1)", listStyle.getLvlText());
        assertEquals(2, listStyle.getStart());
        assertEquals(1, listStyle.getLevel());

        // resolver works on the raw-xml-deserialized model (r1 basedOn r0 fails fast: r0 undefined)
        OfficeStyleResolver resolver = new OfficeStyleResolver(parsed);
        org.junit.jupiter.api.Assertions.assertThrows(NopOfficeDocModelException.class,
                () -> resolver.resolveRunStyle("r1"),
                "basedOn pointing at an undefined style fails fast even after xdef deserialization");
    }
}
