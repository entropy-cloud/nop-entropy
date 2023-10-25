/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.xlsx.imp;

import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.excel.imp.ImportExcelParser;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.parse.ExcelWorkbookParser;

public class XlsxObjectLoader implements IResourceObjectLoader<Object> {
    private ImportModel importModel;
    private String impPath;
    private boolean returnDynamicObject;

    public XlsxObjectLoader(String impModelPath) {
        this.impPath = impModelPath;
    }

    public XlsxObjectLoader(ImportModel importModel) {
        this.importModel = importModel;
    }

    public void setImportModel(ImportModel importModel) {
        this.importModel = importModel;
    }

    public ImportModel getImportModel() {
        if (importModel == null) {
            importModel = (ImportModel) ResourceComponentManager.instance().loadComponentModel(impPath);
        }
        return importModel;
    }

    public boolean isReturnDynamicObject() {
        return returnDynamicObject;
    }

    public void setReturnDynamicObject(boolean returnDynamicObject) {
        this.returnDynamicObject = returnDynamicObject;
    }

    @Override
    public Object loadObjectFromPath(String path) {
        ImportModel importModel = getImportModel();
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromVirtualPath(path);
        ImportExcelParser parser = new ImportExcelParser(importModel);
        parser.setReturnDynamicObject(returnDynamicObject);
        return parser.parseFromWorkbook(wk);
    }

    public Object parseFromResource(IResource resource) {
        ImportModel importModel = getImportModel();
        ExcelWorkbook wk = new ExcelWorkbookParser().parseFromResource(resource);
        ImportExcelParser parser = new ImportExcelParser(importModel);
        parser.setReturnDynamicObject(returnDynamicObject);
        return parser.parseFromWorkbook(wk);
    }
}
