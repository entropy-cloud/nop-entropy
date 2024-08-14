package io.nop.report.core.engine.renderer;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.IExcelSheet;
import io.nop.report.core.coordinate.CellLayerCoordinate;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedSheet;

import java.io.File;

public class HtmlRenderHelper {
    public static String getHtml(ExcelWorkbook workbook, IExcelSheet sheet) {
        return new HtmlReportRendererFactory.HtmlTemplate(workbook,
                (ctx, action) -> action.accept(sheet)).generateText(DisabledEvalScope.INSTANCE);
    }

    public static void dumpHtml(ExcelWorkbook workbook, IExcelSheet sheet, String fileName) {
        String dumpDir = "./target";
        if (workbook.getModel() != null && !StringHelper.isEmpty(workbook.getModel().getDumpDir()))
            dumpDir = workbook.getModel().getDumpDir();
        IResource resource = new FileResource(new File(new File(dumpDir), fileName));
        sheet = copySheet(sheet);
        ResourceHelper.writeText(resource, getHtml(workbook, sheet));
    }

    public static ExpandedSheet copySheet(IExcelSheet sheet) {
        ExpandedSheet copy = new ExpandedSheet(sheet);
        copy.getTable().forEachRealCell((cell, rowIndex, colIndex) -> {
            ExpandedCell ec = (ExpandedCell) cell;
            if (ec.getExpandValue() != null) {
                String text = ec.getExpandValue().toString();
                ec.setValue(text);
            }
            CellLayerCoordinate coord = ec.getLayerCoordinate();
            if (coord != null && coord.hasParent()) {
                ec.setValue(StringHelper.toString(ec.getValue(), "") + "\n<-" + coord);
            }
            return ProcessResult.CONTINUE;
        });
        return copy;
    }
}