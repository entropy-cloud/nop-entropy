package io.nop.excel.renderer;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.excel.model.ExcelWorkbook;

public interface IExcelWorkbookGenerator {
    ExcelWorkbook generateWorkbook(Object obj, IEvalScope scope);
}
