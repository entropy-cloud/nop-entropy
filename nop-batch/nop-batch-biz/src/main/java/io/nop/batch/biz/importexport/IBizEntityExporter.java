package io.nop.batch.biz.importexport;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.core.context.IServiceContext;

import java.util.concurrent.CompletionStage;

public interface IBizEntityExporter {
    CompletionStage<WebContentBean> exportByQuery(
            String bizObjName, BizEntityExportConfig config,
            FieldSelectionBean selection, IServiceContext context);
}