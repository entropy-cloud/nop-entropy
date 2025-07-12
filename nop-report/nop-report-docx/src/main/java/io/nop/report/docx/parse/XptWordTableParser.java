package io.nop.report.docx.parse;

import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelColumnConfig;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelTable;
import io.nop.excel.util.UnitsHelper;
import io.nop.ooxml.docx.parse.WordTableParser;
import io.nop.report.core.build.ExcelToXptModelTransformer;
import io.nop.report.core.build.XptModelInitializer;
import io.nop.report.docx.renderer.XptWordTableRenderer;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLangCompileTool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static io.nop.report.docx.ReportDocxConstants.VAR_DOCX_NODE;

public class XptWordTableParser extends WordTableParser {
    public static final double DEFAULT_WIDTH = 72.0; // 72 pt = 1 inch

    private final XLangCompileTool cp;

    public XptWordTableParser(XLangCompileTool cp) {
        this.cp = cp;
    }

    public static XptWordTableParser fromCompileScope(IXLangCompileScope scope) {
        return new XptWordTableParser(new XLangCompileTool(scope));
    }

    public ITextTemplateOutput compileTable(XNode tblNode) {
        ExcelTable table = parseTable(tblNode);
        ExcelToXptModelTransformer.INSTANCE.transformTable(table);
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("WordTable");
        sheet.setTable(table);

        new XptModelInitializer(cp).buildSheetModel(sheet);

        return new XptWordTableRenderer(sheet);
    }

    @Override
    public ExcelTable parseTable(XNode tblNode) {
        List<ExcelColumnConfig> cols = parseCols(tblNode);
        ExcelTable table = super.parseTable(tblNode);
        table.setCols(cols);
        table.prop_set(VAR_DOCX_NODE, tblNode);

        return table;
    }

    /**
     * 解析 Word 表格的列配置（从 <w:tbl> 节点提取 <w:gridCol>）
     *
     * @param tblNode OOXML 中的 <w:tbl> 节点（XNode 类型）
     * @return 列配置列表
     */
    private List<ExcelColumnConfig> parseCols(XNode tblNode) {
        // 2. 获取 <w:tblGrid> 下的所有 <w:gridCol> 节点
        XNode tblGrid = tblNode.childByTag("w:tblGrid");
        if (tblGrid == null) {
            return new ArrayList<>();
        }

        // 3. 使用 Stream API 遍历并解析列配置
        return tblGrid.getChildren().stream()
                .filter(colNode -> "w:gridCol".equals(colNode.getTagName()))
                .map(this::parseGridColNode)
                .collect(Collectors.toList());
    }

    /**
     * 解析单个 <w:gridCol> 节点为 ExcelColumnConfig
     */
    private ExcelColumnConfig parseGridColNode(XNode gridColNode) {
        ExcelColumnConfig config = new ExcelColumnConfig();

        // 3.1 解析列宽（w:w 属性，单位：twips）
        String widthTwips = gridColNode.attrText("w:w");
        if (widthTwips != null) {
            config.setWidth(
                    UnitsHelper.twipsToPoints(Integer.parseInt(widthTwips))
            );
        } else {
            config.setWidth(DEFAULT_WIDTH); // 默认宽度
        }

        // 3.2 解析其他属性（如隐藏状态、样式等）
        config.setHidden("true".equalsIgnoreCase(
                gridColNode.attrText("data-hidden"))
        );

        return config;
    }
}
