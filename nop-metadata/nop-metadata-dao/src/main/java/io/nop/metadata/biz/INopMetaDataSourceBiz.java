
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.core.dto.CollectCatalogResultDTO;
import io.nop.metadata.core.dto.SyncExternalTablesResultDTO;
import io.nop.metadata.core.dto.TestConnectionResultDTO;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.orm.biz.ICrudBiz;


/**
 * NopMetaDataSource BizModel 契约接口。
 *
 * <p>跨模块 {@code @Inject INopMetaDataSourceBiz} 调用入口：
 * testConnection / syncExternalTables / collectCatalog / collectCatalogForTable。
 *
 * <p>接口返回具体 {@code @DataBean} DTO 供 GraphQL schema 推导强类型字段。
 */
public interface INopMetaDataSourceBiz extends ICrudBiz<NopMetaDataSource> {

    @BizMutation
    TestConnectionResultDTO testConnection(@Name("dataSourceId") String dataSourceId, IServiceContext context);

    @BizMutation
    SyncExternalTablesResultDTO syncExternalTables(@Name("dataSourceId") String dataSourceId,
                                                    @Optional @Name("schemaPattern") String schemaPattern,
                                                    IServiceContext context);

    @BizMutation
    CollectCatalogResultDTO collectCatalog(@Name("dataSourceId") String dataSourceId,
                                            @Optional @Name("schemaPattern") String schemaPattern,
                                            IServiceContext context);

    @BizMutation
    CollectCatalogResultDTO collectCatalogForTable(@Name("metaTableId") String metaTableId,
                                                    @Optional @Name("schemaPattern") String schemaPattern,
                                                    IServiceContext context);
}
