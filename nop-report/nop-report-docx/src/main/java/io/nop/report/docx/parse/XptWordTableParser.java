package io.nop.report.docx.parse;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.docx.model.WordCommentsPart;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.docx.parse.WordTableParser;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableRow;
import io.nop.report.core.build.ExcelToXptModelTransformer;
import io.nop.report.core.build.XptModelInitializer;
import io.nop.report.docx.renderer.XptWordTableRenderer;
import io.nop.report.docx.support.WordTableToExcelTableTransformer;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdsl.DslModelHelper;

import static io.nop.report.docx.ReportDocxConstants.VAR_XPT_NODE;

public class XptWordTableParser extends WordTableParser {
    private final XLangCompileTool cp;

    private WordCommentsPart commentsPart;

    public XptWordTableParser(XLangCompileTool cp) {
        this.cp = cp;
    }

    public static XptWordTableParser fromCompileScope(IXLangCompileScope scope) {
        return new XptWordTableParser(new XLangCompileTool(scope));
    }

    public ITextTemplateOutput compileTable(XNode tblNode, WordOfficePackage pkg, boolean dump) {
        this.commentsPart = pkg.getComments();

        WordTable wordTable = parseTable(tblNode);
        ExcelTable table = WordTableToExcelTableTransformer.transform(wordTable);
        ExcelToXptModelTransformer.INSTANCE.transformTable(table);
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("WordTable");
        sheet.setTable(table);

        sheet.makeModel().prop_set(VAR_XPT_NODE, tblNode);

        new XptModelInitializer(cp).buildSheetModel(sheet);

        if(dump){
            dumpXptModel(sheet);
        }

        return new XptWordTableRenderer(sheet);
    }

    void dumpXptModel(ExcelSheet sheet){
        ExcelWorkbook wk = new ExcelWorkbook();
        wk.addSheet(sheet);

        XNode node = DslModelHelper.dslModelToXNode(ExcelConstants.XDSL_SCHEMA_WORKBOOK, wk);
        node.dump();
    }

    @Override
    protected boolean parseCell(XNode node, WordTableCell cell) {
        XNode ref = node.findByTag("w:commentReference");
        if (ref != null) {
            String id = ref.attrText("w:id");
            if (commentsPart != null) {
                String comment = commentsPart.getComment(id);
                cell.setComment(comment);
            }
        }
        cell.prop_set(VAR_XPT_NODE, node);
        return super.parseCell(node, cell);
    }

    @Override
    protected WordTableRow parseRow(int rowIndex, XNode node, WordTable tbl) {
        WordTableRow row = super.parseRow(rowIndex, node, tbl);
        row.prop_set(VAR_XPT_NODE, node);
        return row;
    }
}
