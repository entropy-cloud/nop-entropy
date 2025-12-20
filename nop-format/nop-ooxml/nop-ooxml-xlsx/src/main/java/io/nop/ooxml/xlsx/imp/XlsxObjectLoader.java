/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.xlsx.imp;

import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.IResourceObjectLoader;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.excel.imp.ImportExcelParser;
import io.nop.excel.imp.model.ImportModel;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.ooxml.xlsx.util.ExcelHelper;
import io.nop.xlang.api.XLang;

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

    public String getImpPath() {
        return impPath;
    }

    public boolean isReturnDynamicObject() {
        return returnDynamicObject;
    }

    public void setReturnDynamicObject(boolean returnDynamicObject) {
        this.returnDynamicObject = returnDynamicObject;
    }

    @Override
    public Object loadObjectFromPath(String path) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        return loadObjectFromResource(resource);
    }

    @Override
    public Object loadObjectFromResource(IResource resource) {
        return parseFromResource(resource, XLang.newEvalScope());
    }

    public Object parseFromResource(IResource resource, IEvalScope scope) {
        ImportModel importModel = getImportModel();
        ExcelWorkbook wk = ExcelHelper.parseExcel(resource);
        ImportExcelParser parser = new ImportExcelParser(importModel, XLang.newCompileTool().allowUnregisteredScopeVar(true), scope);
        parser.setReturnDynamicObject(returnDynamicObject);
        return parser.parseFromWorkbook(wk);
    }
}
