package io.nop.report.core.imp;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectSaver;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.IExcelWorkbookGenerator;
import io.nop.ooxml.xlsx.imp.XlsxObjectLoader;
import io.nop.report.core.util.ExcelReportHelper;

public class XlsxDslModelLoader extends XlsxObjectLoader
        implements IResourceObjectSaver<Object>, IExcelWorkbookGenerator {

    public XlsxDslModelLoader(String impModelPath) {
        super(impModelPath);
    }

    @Override
    public void saveObjectToResource(IResource resource, Object obj) {
        ExcelReportHelper.saveXlsxObject(getImpPath(), resource, obj);
    }

    @Override
    public ExcelWorkbook generateWorkbook(Object obj, IEvalScope scope) {
        return ExcelReportHelper.generateExcelWorkbook(getImpPath(), obj, scope);
    }
}
