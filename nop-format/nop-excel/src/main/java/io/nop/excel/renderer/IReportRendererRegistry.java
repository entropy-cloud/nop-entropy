package io.nop.excel.renderer;

import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.excel.model.ExcelWorkbook;

public interface IReportRendererRegistry {
    ITemplateOutput getRendererForExcel(ExcelWorkbook wk, String renderType);
}
