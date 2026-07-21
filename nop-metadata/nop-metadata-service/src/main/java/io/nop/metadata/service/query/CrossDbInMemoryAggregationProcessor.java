/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.NopMetadataErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.metadata.service.query.AggregationContext.*;
import static io.nop.metadata.service.query.AggregationHelper.*;

public class CrossDbInMemoryAggregationProcessor implements AggregationProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(CrossDbInMemoryAggregationProcessor.class);

    @Override
    public List<Map<String, Object>> execute(AggregationContext context) {
        NopMetaTable table = context.getTable();
        List<String> measureNames = context.getMeasureNames();
        List<String> dimensionNames = context.getDimensionNames();
        TreeBean filter = context.getFilter();
        String joinId = context.getJoinId();
        Long limit = context.getLimit();
        Long offset = context.getOffset();
        TreeBean having = context.getHaving();
        List<OrderFieldBean> orderBy = context.getOrderBy();
        MetaQueryContext ctx = context.ctx();
        NopMetaTableJoin join = context.getJoin();
        MetaJoinExecutor.Endpoint leftEp = context.getLeftEndpoint();
        MetaJoinExecutor.Endpoint rightEp = context.getRightEndpoint();

        // Self-join guards
        if (leftEp.isEntity() && rightEp.isEntity()) {
            if (equalsStr(leftEp.entity.getMetaEntityId(), rightEp.entity.getMetaEntityId())) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_SELF_JOIN)
                        .param("joinId", joinId).param("entityId", leftEp.entity.getMetaEntityId());
            }
        } else if (!leftEp.isEntity() && !rightEp.isEntity()) {
            if (equalsStr(leftEp.table.getMetaTableId(), rightEp.table.getMetaTableId())) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_SELF_JOIN)
                        .param("joinId", joinId).param("entityId", leftEp.table.getMetaTableId());
            }
        }

        CrossDbFieldResolver resolver = new CrossDbFieldResolver(leftEp, rightEp, join, joinId, table, ctx);
        List<CrossDbMeasureSpec> measures = loadCrossDbMeasures(table, measureNames, ctx, resolver);
        List<CrossDbDimensionSpec> dims = loadCrossDbDimensions(table, dimensionNames, ctx, resolver);

        MetaJoinExecutor joinExecutor = context.joinExecutor();
        Map<String, Object> joinResult = joinExecutor.executeJoin(table, joinId, filter, null, 0L, ctx);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mergedRows = joinResult.get("items") instanceof List
                ? (List<Map<String, Object>>) joinResult.get("items")
                : new ArrayList<>();

        String alias = crossDbAliasOf(join);
        if (!mergedRows.isEmpty()) {
            Map<String, Object> sample = mergedRows.get(0);
            resolveAndValidateLookupKeys(measures, alias, sample, table, joinId, "measure");
            resolveAndValidateLookupKeys(dims, alias, sample, table, joinId, "dimension");
        }

        List<Map<String, Object>> items = memoryGroupBy(mergedRows, measures, dims);

        if (having != null) {
            if (MetaAggregationExecutor.containsHavingArithmeticLeaf(having)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_EXPR_MEMORY_NOT_COMPUTABLE)
                        .param("metaTableId", table.getMetaTableId())
                        .param("expr", "<having arithmetic in cross-DB memory path>");
            }
            Map<String, String> nameToAlias = buildCrossDbNameToAliasTable(measures, dims, measureNames,
                    dimensionNames, table);
            MemoryFilterEvaluator evaluator = new MemoryFilterEvaluator(having, nameToAlias, table, measureNames,
                    dimensionNames);
            items = evaluator.filter(items);
        }

        if (orderBy != null && !orderBy.isEmpty()) {
            Map<String, String> nameToAlias = buildCrossDbNameToAliasTable(measures, dims, measureNames,
                    dimensionNames, table);
            items = MemoryOrderByComparator.sort(items, orderBy, nameToAlias, table, measureNames, dimensionNames);
        }

        return truncateCrossDb(items, limit, offset);
    }

    private static List<CrossDbMeasureSpec> loadCrossDbMeasures(NopMetaTable table, List<String> names,
                                                                  MetaQueryContext ctx, CrossDbFieldResolver resolver) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<CrossDbMeasureSpec> specs = new ArrayList<>(all.size());
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_MEMORY_NOT_COMPUTABLE)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", m.getMeasureName())
                        .param("joinId", resolver.joinId());
            }
            CrossDbField f = resolver.resolve(m.getEntityFieldId(), m.getMeasureName(), m.getSide(), "measure");
            specs.add(new CrossDbMeasureSpec(safeAlias(m.getMeasureName()), m.getAggFunc(), f.rawKey, f.side));
        }
        return specs;
    }

    private static List<CrossDbDimensionSpec> loadCrossDbDimensions(NopMetaTable table, List<String> names,
                                                                     MetaQueryContext ctx, CrossDbFieldResolver resolver) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<CrossDbDimensionSpec> specs = new ArrayList<>(all.size());
        for (NopMetaTableDimension d : all) {
            CrossDbField f = resolver.resolve(d.getEntityFieldId(), d.getDimensionName(), d.getSide(), "dimension");
            specs.add(new CrossDbDimensionSpec(safeAlias(d.getDimensionName()), f.rawKey, f.side));
        }
        return specs;
    }
}
