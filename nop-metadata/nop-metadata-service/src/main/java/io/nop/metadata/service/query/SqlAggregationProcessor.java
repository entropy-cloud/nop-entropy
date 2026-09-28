
package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.util.List;
import java.util.Map;

/**
 * SQL 表聚合处理器（ENTITY_KIND_SQL_VIEW）。
 * 与 ExternalAggregationProcessor 共享 buildExternalAggregationSql / collectBindParams 等静态方法。
 */
public class SqlAggregationProcessor implements AggregationProcessor {

    @Override
    public List<Map<String, Object>> execute(AggregationContext context) {
        // 校验 entityKind 一定是 SQL
        String entityKind = context.getTable().getEntityKind();
        if (!_NopMetadataCoreConstants.ENTITY_KIND_SQL_VIEW.equals(entityKind)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_TABLE_TYPE)
                    .param(NopMetadataErrors.ARG_TABLE_TYPE, entityKind);
        }
        // 逻辑与 ExternalAggregationProcessor 一致，复用其静态加载方法
        return new ExternalAggregationProcessor().execute(context);
    }
}
