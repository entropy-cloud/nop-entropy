package io.nop.biz.crud.importexport;

import io.nop.core.context.IServiceContext;

public interface IBizEntityImporter {
    void importFile(String filePath, String bizObjName, BizEntityImportOptions options,
                    IServiceContext context);
}
