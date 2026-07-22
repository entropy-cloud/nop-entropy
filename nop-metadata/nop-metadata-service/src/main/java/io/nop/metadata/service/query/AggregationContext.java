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
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.NopMetadataException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AggregationContext {

    static final Set<String> SUPPORTED_DIALECTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));

    private final MetaQueryContext ctx;
    private final MetaJoinExecutor joinExecutor;

    public AggregationContext(MetaQueryContext ctx, MetaJoinExecutor joinExecutor) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.joinExecutor = Objects.requireNonNull(joinExecutor, "joinExecutor");
    }

    public MetaQueryContext ctx() { return ctx; }

    public MetaJoinExecutor joinExecutor() { return joinExecutor; }

    private NopMetaTable table;
    private List<String> measureNames;
    private List<String> dimensionNames;
    private String joinId;
    private NopMetaTableJoin join;
    private io.nop.api.core.beans.TreeBean filter;
    private Long limit;
    private Long offset;
    private io.nop.api.core.beans.TreeBean having;
    private List<io.nop.api.core.beans.query.OrderFieldBean> orderBy;
    private MetaJoinExecutor.Endpoint leftEndpoint;
    private MetaJoinExecutor.Endpoint rightEndpoint;

    public NopMetaTable getTable() { return table; }

    public void setTable(NopMetaTable table) { this.table = table; }

    public List<String> getMeasureNames() { return measureNames; }

    public void setMeasureNames(List<String> measureNames) { this.measureNames = measureNames; }

    public List<String> getDimensionNames() { return dimensionNames; }

    public void setDimensionNames(List<String> dimensionNames) { this.dimensionNames = dimensionNames; }

    public String getJoinId() { return joinId; }

    public void setJoinId(String joinId) { this.joinId = joinId; }

    public NopMetaTableJoin getJoin() { return join; }

    public void setJoin(NopMetaTableJoin join) { this.join = join; }

    public MetaJoinExecutor.Endpoint getLeftEndpoint() { return leftEndpoint; }

    public void setLeftEndpoint(MetaJoinExecutor.Endpoint leftEndpoint) { this.leftEndpoint = leftEndpoint; }

    public MetaJoinExecutor.Endpoint getRightEndpoint() { return rightEndpoint; }

    public void setRightEndpoint(MetaJoinExecutor.Endpoint rightEndpoint) { this.rightEndpoint = rightEndpoint; }

    public io.nop.api.core.beans.TreeBean getFilter() { return filter; }

    public void setFilter(io.nop.api.core.beans.TreeBean filter) { this.filter = filter; }

    public Long getLimit() { return limit; }

    public void setLimit(Long limit) { this.limit = limit; }

    public Long getOffset() { return offset; }

    public void setOffset(Long offset) { this.offset = offset; }

    public io.nop.api.core.beans.TreeBean getHaving() { return having; }

    public void setHaving(io.nop.api.core.beans.TreeBean having) { this.having = having; }

    public List<io.nop.api.core.beans.query.OrderFieldBean> getOrderBy() { return orderBy; }

    public void setOrderBy(List<io.nop.api.core.beans.query.OrderFieldBean> orderBy) { this.orderBy = orderBy; }

    // ============================ 内类型 ============================

    public static final class MeasureSpec {
        public final String alias;
        public final String aggSql;
        public final List<Object> expressionParams;
        public final ExpressionMeasureValidator.ValidatedExpression validatedExpression;

        public MeasureSpec(String alias, String aggSql) {
            this(alias, aggSql, null, null);
        }

        public MeasureSpec(String alias, String aggSql, List<Object> expressionParams,
                           ExpressionMeasureValidator.ValidatedExpression validatedExpression) {
            this.alias = alias;
            this.aggSql = aggSql;
            this.expressionParams = expressionParams;
            this.validatedExpression = validatedExpression;
        }

        public boolean isExpression() {
            return validatedExpression != null;
        }
    }

    public static final class DimensionSpec {
        public final String alias;
        public final String column;
        public final String dimensionType;
        public final String granularity;

        public DimensionSpec(String alias, String column, String dimensionType, String granularity) {
            this.alias = alias;
            this.column = column;
            this.dimensionType = dimensionType;
            this.granularity = granularity;
        }
    }

    public static final class JoinMeasureSpec {
        public final String alias;
        public final String aggSql;
        public final String qualifiedAggCol;
        public final List<Object> expressionParams;
        public final ExpressionMeasureValidator.ValidatedExpression validatedExpression;

        public JoinMeasureSpec(String alias, String aggSql, String qualifiedAggCol) {
            this(alias, aggSql, qualifiedAggCol, null, null);
        }

        public JoinMeasureSpec(String alias, String aggSql, String qualifiedAggCol,
                               List<Object> expressionParams,
                               ExpressionMeasureValidator.ValidatedExpression validatedExpression) {
            this.alias = alias;
            this.aggSql = aggSql;
            this.qualifiedAggCol = qualifiedAggCol;
            this.expressionParams = expressionParams;
            this.validatedExpression = validatedExpression;
        }

        public boolean isExpression() {
            return validatedExpression != null;
        }
    }

    public static final class JoinDimensionSpec {
        public final String alias;
        public final String qualifiedCol;
        public final String column;
        public final String dimensionType;
        public final String granularity;

        public JoinDimensionSpec(String alias, String qualifiedCol, String column, String dimensionType, String granularity) {
            this.alias = alias;
            this.qualifiedCol = qualifiedCol;
            this.column = column;
            this.dimensionType = dimensionType;
            this.granularity = granularity;
        }
    }

    public static final class JoinField {
        public final String column;
        public final String qualifiedColumn;

        public JoinField(String column, String qualifiedColumn) {
            this.column = column;
            this.qualifiedColumn = qualifiedColumn;
        }
    }

    public abstract static class CrossDbFieldSpec {
        public final String alias;
        public final String rawKey;
        public final String side;
        public String lookupKey;

        public CrossDbFieldSpec(String alias, String rawKey, String side) {
            this.alias = alias;
            this.rawKey = rawKey;
            this.side = side;
            this.lookupKey = rawKey;
        }
    }

    public static final class CrossDbMeasureSpec extends CrossDbFieldSpec {
        public final String aggFunc;

        public CrossDbMeasureSpec(String alias, String aggFunc, String rawKey, String side) {
            super(alias, rawKey, side);
            this.aggFunc = aggFunc;
        }
    }

    public static final class CrossDbDimensionSpec extends CrossDbFieldSpec {
        public CrossDbDimensionSpec(String alias, String rawKey, String side) {
            super(alias, rawKey, side);
        }
    }

    public static final class CrossDbField {
        public final String side;
        public final String rawKey;

        public CrossDbField(String side, String rawKey) {
            this.side = side;
            this.rawKey = rawKey;
        }
    }

    public abstract static class MemAggAccumulator {
        public abstract void accumulate(Object v);

        public abstract Object result();

        public static MemAggAccumulator[] newAccumulators(List<CrossDbMeasureSpec> measures) {
            MemAggAccumulator[] accs = new MemAggAccumulator[measures.size()];
            for (int i = 0; i < measures.size(); i++) {
                accs[i] = forFunc(measures.get(i).aggFunc, measures.get(i).alias);
            }
            return accs;
        }

        static MemAggAccumulator forFunc(String aggFunc, String name) {
            if (aggFunc == null) {
                throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                        .param("aggFunc", String.valueOf(aggFunc)).param("measureName", name);
            }
            switch (aggFunc) {
                case _NopMetadataCoreConstants.AGG_FUNC_SUM:
                    return new SumAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_COUNT:
                    return new CountAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_AVG:
                    return new AvgAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_MIN:
                    return new MinAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_MAX:
                    return new MaxAcc();
                case _NopMetadataCoreConstants.AGG_FUNC_COUNT_DISTINCT:
                    return new CountDistinctAcc();
                default:
                    throw new NopMetadataException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                            .param("aggFunc", aggFunc).param("measureName", name);
            }
        }
    }

    public static final class SumAcc extends MemAggAccumulator {
        private java.math.BigDecimal sum;
        private boolean hasValue;

        @Override
        public void accumulate(Object v) {
            if (v == null) return;
            java.math.BigDecimal n = AggregationHelper.toBigDecimal(v);
            if (n != null) {
                sum = (sum == null) ? n : sum.add(n);
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? sum : null;
        }
    }

    public static final class CountAcc extends MemAggAccumulator {
        private long count;

        @Override
        public void accumulate(Object v) {
            if (v != null) count++;
        }

        @Override
        public Object result() {
            return count;
        }
    }

    public static final class AvgAcc extends MemAggAccumulator {
        private java.math.BigDecimal sum;
        private long count;

        @Override
        public void accumulate(Object v) {
            if (v == null) return;
            java.math.BigDecimal n = AggregationHelper.toBigDecimal(v);
            if (n != null) {
                sum = (sum == null) ? n : sum.add(n);
                count++;
            }
        }

        @Override
        public Object result() {
            if (count == 0) return null;
            return sum.divide(java.math.BigDecimal.valueOf(count), java.math.MathContext.DECIMAL64);
        }
    }

    public static final class MinAcc extends MemAggAccumulator {
        private Comparable<Object> min;
        private boolean hasValue;

        @Override
        @SuppressWarnings("unchecked")
        public void accumulate(Object v) {
            if (v == null) return;
            Comparable<Object> c = (Comparable<Object>) v;
            if (!hasValue || c.compareTo(min) < 0) {
                min = c;
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? min : null;
        }
    }

    public static final class MaxAcc extends MemAggAccumulator {
        private Comparable<Object> max;
        private boolean hasValue;

        @Override
        @SuppressWarnings("unchecked")
        public void accumulate(Object v) {
            if (v == null) return;
            Comparable<Object> c = (Comparable<Object>) v;
            if (!hasValue || c.compareTo(max) > 0) {
                max = c;
                hasValue = true;
            }
        }

        @Override
        public Object result() {
            return hasValue ? max : null;
        }
    }

    public static final class CountDistinctAcc extends MemAggAccumulator {
        private final Set<Object> distinct = new LinkedHashSet<>();

        @Override
        public void accumulate(Object v) {
            if (v != null) distinct.add(v);
        }

        @Override
        public Object result() {
            return (long) distinct.size();
        }
    }

    @FunctionalInterface
    public interface JoinFieldResolverFn {
        JoinField resolve(String entityFieldId, String name, String declaredSide);
    }
}
