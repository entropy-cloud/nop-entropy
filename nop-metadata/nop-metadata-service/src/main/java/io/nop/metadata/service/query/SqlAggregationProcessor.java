package io.nop.metadata.service.query;

import io.nop.metadata.core._NopMetadataCoreConstants;

import java.util.List;
import java.util.Map;

/**
 * SQL 表聚合处理器（TABLE_TYPE_SQL）。
 * 与 ExternalAggregationProcessor 共享 buildExternalAggregationSql / collectBindParams 等静态方法。
 */
public class SqlAggregationProcessor implements AggregationProcessor {

    @Override
    public List<Map<String, Object>> execute(AggregationContext context) {
        // 校验 tableType 一定是 SQL
        String tableType = context.getTable().getTableType();
        if (!_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            throw new IllegalArgumentException("SqlAggregationProcessor requires TABLE_TYPE_SQL, got: " + tableType);
        }
        // 逻辑与 ExternalAggregationProcessor 一致，复用其静态加载方法
        return new ExternalAggregationProcessor().execute(context);
    }
}
