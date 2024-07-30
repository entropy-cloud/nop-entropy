package io.nop.biz.crud.importexport;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.core.context.IServiceContext;

import java.util.concurrent.CompletionStage;

public interface IBizEntityExporter {
    CompletionStage<WebContentBean> exportByQuery(
            String bizObjName, QueryBean query,
            String exportFormat, boolean exportAll,
            FieldSelectionBean selection, IServiceContext context);
}