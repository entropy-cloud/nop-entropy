/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.service.NopMetadataErrors;

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
            throw new NopException(NopMetadataErrors.ERR_AGGR_UNSUPPORTED_TABLE_TYPE)
                    .param(NopMetadataErrors.ARG_TABLE_TYPE, tableType);
        }
        // 逻辑与 ExternalAggregationProcessor 一致，复用其静态加载方法
        return new ExternalAggregationProcessor().execute(context);
    }
}
