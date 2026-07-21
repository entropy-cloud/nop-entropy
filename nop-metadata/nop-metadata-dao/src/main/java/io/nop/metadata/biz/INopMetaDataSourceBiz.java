
package io.nop.metadata.biz;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.orm.biz.ICrudBiz;

import java.util.Map;


/**
 * NopMetaDataSource BizModel 契约接口（plan 307 DTO migration）。
 *
 * <p>跨模块 {@code @Inject INopMetaDataSourceBiz} 调用入口：
 * testConnection / syncExternalTables / collectCatalog / collectCatalogForTable。
 *
 * <p>接口返回 {@code Map<String, Object>} 保持 nop-metadata-dao 无 nop-metadata-service 依赖；
 * BizModel 实现类返回具体 {@code @DataBean} DTO 供 GraphQL schema 推导强类型字段。
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
