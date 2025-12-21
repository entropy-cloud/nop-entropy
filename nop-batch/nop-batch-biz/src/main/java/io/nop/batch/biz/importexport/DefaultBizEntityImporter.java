package io.nop.batch.biz.importexport;

import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;

import java.util.concurrent.CompletionStage;

public class DefaultBizEntityImporter implements IBizEntityImporter {

    @Override
    public CompletionStage<BizEntityImportResponseBean> importFile(IResource resource,
                                                                   String bizObjName,
                                                                   BizEntityImportConfig config,
                                                                   IServiceContext context) {
        return null;
    }
}
