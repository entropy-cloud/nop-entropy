
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.orm.biz.ICrudBiz;

import java.util.Map;


/**
 * NopMetaDataSource BizModel 契约接口（plan 2026-07-19-1250-3 Phase 1）。
 *
 * <p>跨模块 {@code @Inject INopMetaDataSourceBiz} 调用入口：
 * testConnection / syncExternalTables / collectCatalog / collectCatalogForTable。
 */
public interface INopMetaDataSourceBiz extends ICrudBiz<NopMetaDataSource> {

    @BizMutation
    Map<String, Object> testConnection(@Name("dataSourceId") String dataSourceId, IServiceContext context);

    @BizMutation
    Map<String, Object> syncExternalTables(@Name("dataSourceId") String dataSourceId,
                                            @Optional @Name("schemaPattern") String schemaPattern,
                                            IServiceContext context);

    @BizMutation
    Map<String, Object> collectCatalog(@Name("dataSourceId") String dataSourceId,
                                        @Optional @Name("schemaPattern") String schemaPattern,
                                        IServiceContext context);

    @BizMutation
    Map<String, Object> collectCatalogForTable(@Name("metaTableId") String metaTableId,
                                                @Optional @Name("schemaPattern") String schemaPattern,
                                                IServiceContext context);
}
