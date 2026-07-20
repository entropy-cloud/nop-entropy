/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.query;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IEntityDao;
import io.nop.dataset.IDataRow;
import io.nop.dataset.IDataSet;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.dao.entity.NopMetaTableFilter;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.NopMetadataErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聚合查询共享上下文：集中承载 service 依赖 + per-request 状态 + 内类型 + 静态辅助方法。
 * <p>从 {@link MetaAggregationExecutor} 抽取，供 7 个 Processor 共用。
 */
public class AggregationContext {
    private static final Logger LOG = LoggerFactory.getLogger(AggregationContext.class);

    static final Set<String> SUPPORTED_DIALECTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("H2", "MySQL", "PostgreSQL")));

    // ============================ service 依赖 ============================

    private final MetaQueryContext ctx;
    private final MetaJoinExecutor joinExecutor;

    public AggregationContext(MetaQueryContext ctx, MetaJoinExecutor joinExecutor) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.joinExecutor = Objects.requireNonNull(joinExecutor, "joinExecutor");
    }

    public MetaQueryContext ctx() {
        return ctx;
    }

    public MetaJoinExecutor joinExecutor() {
        return joinExecutor;
    }

    // ============================ per-request 状态 ============================

    private NopMetaTable table;
    private List<String> measureNames;
    private List<String> dimensionNames;
    private TreeBean filter;
    private String joinId;
    private Long limit;
    private Long offset;
    private TreeBean having;
    private List<OrderFieldBean> orderBy;

    public NopMetaTable getTable() {
        return table;
    }

    public void setTable(NopMetaTable table) {
        this.table = table;
    }

    public List<String> getMeasureNames() {
        return measureNames;
    }

    public void setMeasureNames(List<String> measureNames) {
        this.measureNames = measureNames;
    }

    public List<String> getDimensionNames() {
        return dimensionNames;
    }

    public void setDimensionNames(List<String> dimensionNames) {
        this.dimensionNames = dimensionNames;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public String getJoinId() {
        return joinId;
    }

    public void setJoinId(String joinId) {
        this.joinId = joinId;
    }

    public Long getLimit() {
        return limit;
    }

    public void setLimit(Long limit) {
        this.limit = limit;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public TreeBean getHaving() {
        return having;
    }

    public void setHaving(TreeBean having) {
        this.having = having;
    }

    public List<OrderFieldBean> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<OrderFieldBean> orderBy) {
        this.orderBy = orderBy;
    }

    private NopMetaTableJoin join;
    private MetaJoinExecutor.Endpoint leftEndpoint;
    private MetaJoinExecutor.Endpoint rightEndpoint;

    public NopMetaTableJoin getJoin() { return join; }

    public void setJoin(NopMetaTableJoin join) { this.join = join; }

    public MetaJoinExecutor.Endpoint getLeftEndpoint() { return leftEndpoint; }

    public void setLeftEndpoint(MetaJoinExecutor.Endpoint leftEndpoint) { this.leftEndpoint = leftEndpoint; }

    public MetaJoinExecutor.Endpoint getRightEndpoint() { return rightEndpoint; }

    public void setRightEndpoint(MetaJoinExecutor.Endpoint rightEndpoint) { this.rightEndpoint = rightEndpoint; }

    // ============================ 内类型 ============================

    /** 指标规格：别名 + 聚合 SQL 表达式 + expression 信息（expression 型 Measure 承载）。 */
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

    /** 维度规格：别名 + 列表达式 + 类型 + granularity。 */
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

    /** JOIN Measure 规格：别名 + 聚合 SQL 表达式（含 table-qualified 列）+ 限定列（供白名单校验）+ expression 信息。 */
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

    /** JOIN Dimension 规格：别名 + table-qualified 列表达式 + 裸物理列（granularity 白名单校验用）+ 类型 + granularity。 */
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

    /** JOIN 字段解析结果：物理列 + table-qualified 限定列（l.col / r.col）。 */
    public static final class JoinField {
        public final String column;
        public final String qualifiedColumn;

        public JoinField(String column, String qualifiedColumn) {
            this.column = column;
            this.qualifiedColumn = qualifiedColumn;
        }
    }

    /** 跨库字段规格基类：alias（输出列名）+ rawKey + side + lookupKey（合并行实际 key，解析后填充）。 */
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

    /** 跨库 Measure 规格：alias + aggFunc + rawKey + side。 */
    public static final class CrossDbMeasureSpec extends CrossDbFieldSpec {
        public final String aggFunc;

        public CrossDbMeasureSpec(String alias, String aggFunc, String rawKey, String side) {
            super(alias, rawKey, side);
            this.aggFunc = aggFunc;
        }
    }

    /** 跨库 Dimension 规格：alias + rawKey + side。 */
    public static final class CrossDbDimensionSpec extends CrossDbFieldSpec {
        public CrossDbDimensionSpec(String alias, String rawKey, String side) {
            super(alias, rawKey, side);
        }
    }

    /** 跨库字段解析结果：side（left/right）+ rawKey（entity=fieldName / table=物理列名）。 */
    public static final class CrossDbField {
        public final String side;
        public final String rawKey;

        public CrossDbField(String side, String rawKey) {
            this.side = side;
            this.rawKey = rawKey;
        }
    }

    /** 内存聚合累加器。 */
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
                throw new NopException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
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
                    throw new NopException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                            .param("aggFunc", aggFunc).param("measureName", name);
            }
        }
    }

    /** sum：累加数值（BigDecimal 防溢出），null 跳过。 */
    public static final class SumAcc extends MemAggAccumulator {
        private java.math.BigDecimal sum;
        private boolean hasValue;

        @Override
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
            java.math.BigDecimal n = toBigDecimal(v);
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

    /** count：非空值计数（null 跳过）。 */
    public static final class CountAcc extends MemAggAccumulator {
        private long count;

        @Override
        public void accumulate(Object v) {
            if (v != null) {
                count++;
            }
        }

        @Override
        public Object result() {
            return count;
        }
    }

    /** avg：sum/count，count=0 → null（非伪造 0）。 */
    public static final class AvgAcc extends MemAggAccumulator {
        private java.math.BigDecimal sum;
        private long count;

        @Override
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
            java.math.BigDecimal n = toBigDecimal(v);
            if (n != null) {
                sum = (sum == null) ? n : sum.add(n);
                count++;
            }
        }

        @Override
        public Object result() {
            if (count == 0) {
                return null;
            }
            return sum.divide(java.math.BigDecimal.valueOf(count), java.math.MathContext.DECIMAL64);
        }
    }

    /** min：比较取最小（Comparable），null 跳过，全 null → null。 */
    public static final class MinAcc extends MemAggAccumulator {
        private Comparable<Object> min;
        private boolean hasValue;

        @Override
        @SuppressWarnings("unchecked")
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
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

    /** max：比较取最大（Comparable），null 跳过，全 null → null。 */
    public static final class MaxAcc extends MemAggAccumulator {
        private Comparable<Object> max;
        private boolean hasValue;

        @Override
        @SuppressWarnings("unchecked")
        public void accumulate(Object v) {
            if (v == null) {
                return;
            }
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

    /** countDistinct：内存去重（LinkedHashSet），结果 = 去重后基数（null 不计入）。 */
    public static final class CountDistinctAcc extends MemAggAccumulator {
        private final Set<Object> distinct = new LinkedHashSet<>();

        @Override
        public void accumulate(Object v) {
            if (v != null) {
                distinct.add(v);
            }
        }

        @Override
        public Object result() {
            return (long) distinct.size();
        }
    }

    /**
     * JOIN 字段归属解析器（entity 路径）：按 entityFieldId → NopMetaEntityField.metaEntityId 判定字段属于左/右 entity，
     * 解析物理列并构造限定别名（l.<col> / r.<col>）。
     */
    public static final class JoinFieldResolver {
        private final String leftEntityId;
        private final String rightEntityId;
        private final String joinId;
        private final NopMetaTable table;
        private final MetaQueryContext ctx;

        public JoinFieldResolver(String leftEntityId, String rightEntityId, String joinId,
                                 NopMetaTable table, MetaQueryContext ctx) {
            this.leftEntityId = leftEntityId;
            this.rightEntityId = rightEntityId;
            this.joinId = joinId;
            this.table = table;
            this.ctx = ctx;
        }

        public JoinField resolve(String entityFieldId, String name, String declaredSide, String refKind) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
            NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
            if (field == null || field.getColumnCode() == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId);
            }
            String fieldMetaEntityId = field.getMetaEntityId();
            String column = field.getColumnCode();
            String resolvedSide;
            String alias;
            if (equalsStr(fieldMetaEntityId, leftEntityId)) {
                resolvedSide = "left";
                alias = "l";
            } else if (equalsStr(fieldMetaEntityId, rightEntityId)) {
                resolvedSide = "right";
                alias = "r";
            } else {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("leftEntityId", String.valueOf(leftEntityId))
                        .param("rightEntityId", String.valueOf(rightEntityId))
                        .param("joinId", joinId);
            }
            if (declaredSide != null && !declaredSide.isEmpty()
                    && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name)
                        .param("declaredSide", declaredSide)
                        .param("resolvedSide", resolvedSide)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("joinId", joinId);
            }
            return new JoinField(column, alias + "." + column);
        }

        public String leftEntityId() {
            return leftEntityId;
        }

        public String rightEntityId() {
            return rightEntityId;
        }

        public Set<String> resolveEntityColumns(String metaEntityId) {
            IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, metaEntityId));
            List<NopMetaEntityField> fields = fieldDao.findAllByQuery(q);
            Set<String> cols = new LinkedHashSet<>();
            for (NopMetaEntityField f : fields) {
                if (f.getColumnCode() != null) {
                    cols.add(f.getColumnCode());
                }
            }
            return cols;
        }

        private static boolean equalsStr(String a, String b) {
            return (a == null) ? b == null : a.equals(b);
        }
    }

    /**
     * JOIN 字段侧别解析器（external/sql 路径）：按 Measure/Dimension 的 side（必填）绑定端点，
     * 解析物理列（= entityFieldId 列名字符串）并校验列名存在于所绑定端点的解析字段集合，构造限定别名（l.<col> / r.<col>）。
     */
    public static final class JoinExternalSideResolver {
        private final NopMetaTable leftTable;
        private final NopMetaTable rightTable;
        private final Set<String> leftCols;
        private final Set<String> rightCols;
        private final String joinId;
        private final NopMetaTable ownerTable;

        public JoinExternalSideResolver(NopMetaTable leftTable, NopMetaTable rightTable,
                                        Set<String> leftCols, Set<String> rightCols,
                                        String joinId, NopMetaTable ownerTable) {
            this.leftTable = leftTable;
            this.rightTable = rightTable;
            this.leftCols = leftCols;
            this.rightCols = rightCols;
            this.joinId = joinId;
            this.ownerTable = ownerTable;
        }

        public Set<String> leftColumns() {
            return leftCols;
        }

        public Set<String> rightColumns() {
            return rightCols;
        }

        public JoinField resolve(String columnName, String name, String declaredSide) {
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            if (columnName == null || columnName.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(columnName));
            }
            String alias;
            String endpointType;
            Set<String> cols;
            if ("left".equalsIgnoreCase(declaredSide)) {
                alias = "l";
                endpointType = String.valueOf(leftTable.getTableType());
                cols = leftCols;
            } else if ("right".equalsIgnoreCase(declaredSide)) {
                alias = "r";
                endpointType = String.valueOf(rightTable.getTableType());
                cols = rightCols;
            } else {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "unknown")
                        .param("column", columnName).param("joinId", joinId);
            }
            if (!containsIgnoreCase(cols, columnName)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", endpointType)
                        .param("column", columnName).param("joinId", joinId);
            }
            return new JoinField(columnName, alias + "." + columnName);
        }

        private static boolean containsIgnoreCase(Set<String> cols, String name) {
            if (cols == null || name == null) {
                return false;
            }
            for (String c : cols) {
                if (c != null && c.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * JOIN 字段侧别解析器（混合端点路径，plan 1500-1 D1.5）。
     * 端点组合：一端 entity，另一端 external/sql table。
     */
    public static final class JoinMixedSideResolver {
        private final NopMetaEntity entityEndpoint;
        private final Map<String, String> entityPropToCol;
        private final NopMetaTable tableEndpoint;
        private final Set<String> tableCols;
        private final boolean entityOnLeft;
        private final String joinId;
        private final NopMetaTable ownerTable;
        private final MetaQueryContext ctx;

        public JoinMixedSideResolver(NopMetaEntity entityEndpoint, Map<String, String> entityPropToCol,
                                     NopMetaTable tableEndpoint, Set<String> tableCols,
                                     boolean entityOnLeft, String joinId, NopMetaTable ownerTable,
                                     MetaQueryContext ctx) {
            this.entityEndpoint = entityEndpoint;
            this.entityPropToCol = entityPropToCol;
            this.tableEndpoint = tableEndpoint;
            this.tableCols = tableCols;
            this.entityOnLeft = entityOnLeft;
            this.joinId = joinId;
            this.ownerTable = ownerTable;
            this.ctx = ctx;
        }

        public Set<String> leftColumns() {
            return entityOnLeft ? entityColumnCodes() : tableCols;
        }

        public Set<String> rightColumns() {
            return entityOnLeft ? tableCols : entityColumnCodes();
        }

        private Set<String> entityColumnCodes() {
            Set<String> cols = new LinkedHashSet<>();
            if (entityPropToCol != null) {
                cols.addAll(entityPropToCol.values());
            }
            return cols;
        }

        public JoinField resolve(String entityFieldId, String name, String declaredSide) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            NopMetaEntityField field = tryLoadEntityField(entityFieldId);
            if (field != null && equalsStr(field.getMetaEntityId(), entityEndpoint.getMetaEntityId())) {
                String column = field.getColumnCode();
                if (column == null || column.isEmpty()) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                            .param("metaTableId", ownerTable.getMetaTableId())
                            .param("name", name).param("entityFieldId", entityFieldId);
                }
                String resolvedSide = entityOnLeft ? "left" : "right";
                String alias = entityOnLeft ? "l" : "r";
                if (declaredSide != null && !declaredSide.isEmpty()
                        && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                            .param("metaTableId", ownerTable.getMetaTableId())
                            .param("name", name)
                            .param("declaredSide", declaredSide)
                            .param("resolvedSide", resolvedSide)
                            .param("fieldMetaEntityId", String.valueOf(field.getMetaEntityId()))
                            .param("joinId", joinId);
                }
                return new JoinField(column, alias + "." + column);
            }
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            String expectedSide = entityOnLeft ? "right" : "left";
            String alias;
            if (expectedSide.equalsIgnoreCase(declaredSide)) {
                alias = entityOnLeft ? "r" : "l";
            } else {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "entity")
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            if (!containsIgnoreCase(tableCols, entityFieldId)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", String.valueOf(tableEndpoint.getTableType()))
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            return new JoinField(entityFieldId, alias + "." + entityFieldId);
        }

        private NopMetaEntityField tryLoadEntityField(String entityFieldId) {
            try {
                IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
                return fieldDao.getEntityById(entityFieldId);
            } catch (Exception e) {
                return null;
            }
        }

        private static boolean equalsStr(String a, String b) {
            return (a == null) ? b == null : a.equals(b);
        }

        private static boolean containsIgnoreCase(Set<String> cols, String name) {
            if (cols == null || name == null) {
                return false;
            }
            for (String c : cols) {
                if (c != null && c.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** JOIN 字段解析函数式契约。 */
    @FunctionalInterface
    public interface JoinFieldResolverFn {
        JoinField resolve(String entityFieldId, String name, String declaredSide);
    }

    /**
     * 跨库 JOIN 聚合字段解析器（plan 1500-2 D10）：按端点组合解析 measure/dimension 的 side + rawKey per D10 命名空间规则。
     */
    public static final class CrossDbFieldResolver {
        private final MetaJoinExecutor.Endpoint leftEp;
        private final MetaJoinExecutor.Endpoint rightEp;
        private final NopMetaTableJoin join;
        private final String joinId;
        private final NopMetaTable ownerTable;
        private final MetaQueryContext ctx;

        private final boolean entityEntity;
        private final boolean tableTable;
        private final NopMetaEntity entityEndpoint;
        private final boolean entityOnLeft;
        private final NopMetaTable tableEndpoint;
        private Set<String> tableCols;
        private Set<String> leftCols;
        private Set<String> rightCols;

        public CrossDbFieldResolver(MetaJoinExecutor.Endpoint leftEp, MetaJoinExecutor.Endpoint rightEp,
                                    NopMetaTableJoin join, String joinId, NopMetaTable ownerTable, MetaQueryContext ctx) {
            this.leftEp = leftEp;
            this.rightEp = rightEp;
            this.join = join;
            this.joinId = joinId;
            this.ownerTable = ownerTable;
            this.ctx = ctx;
            this.entityEntity = leftEp.isEntity() && rightEp.isEntity();
            this.tableTable = !leftEp.isEntity() && !rightEp.isEntity();
            this.entityOnLeft = leftEp.isEntity();
            this.entityEndpoint = entityEntity ? null : (leftEp.isEntity() ? leftEp.entity : rightEp.entity);
            this.tableEndpoint = entityEntity ? null : (leftEp.isEntity() ? rightEp.table : leftEp.table);
        }

        public CrossDbField resolve(String entityFieldId, String name, String declaredSide, String fieldKind) {
            if (entityEntity) {
                return resolveEntityEntity(entityFieldId, name, declaredSide);
            }
            if (tableTable) {
                return resolveTableTable(entityFieldId, name, declaredSide);
            }
            return resolveMixed(entityFieldId, name, declaredSide);
        }

        public String joinId() {
            return joinId;
        }

        private CrossDbField resolveEntityEntity(String entityFieldId, String name, String declaredSide) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
            NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
            if (field == null || field.getFieldName() == null || field.getFieldName().isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId);
            }
            String fieldMetaEntityId = field.getMetaEntityId();
            String resolvedSide;
            if (equalsStr(fieldMetaEntityId, leftEp.entity.getMetaEntityId())) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
            } else if (equalsStr(fieldMetaEntityId, rightEp.entity.getMetaEntityId())) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
            } else {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_SIDE_UNRESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", entityFieldId)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("leftEntityId", String.valueOf(leftEp.entity.getMetaEntityId()))
                        .param("rightEntityId", String.valueOf(rightEp.entity.getMetaEntityId()))
                        .param("joinId", joinId);
            }
            if (declaredSide != null && !declaredSide.isEmpty()
                    && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name)
                        .param("declaredSide", declaredSide)
                        .param("resolvedSide", resolvedSide)
                        .param("fieldMetaEntityId", String.valueOf(fieldMetaEntityId))
                        .param("joinId", joinId);
            }
            return new CrossDbField(resolvedSide, field.getFieldName());
        }

        private CrossDbField resolveTableTable(String columnName, String name, String declaredSide) {
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            if (columnName == null || columnName.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(columnName));
            }
            String resolvedSide;
            Set<String> cols;
            if (_NopMetadataCoreConstants.JOIN_SIDE_LEFT.equalsIgnoreCase(declaredSide)) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
                cols = ensureLeftCols();
            } else if (_NopMetadataCoreConstants.JOIN_SIDE_RIGHT.equalsIgnoreCase(declaredSide)) {
                resolvedSide = _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
                cols = ensureRightCols();
            } else {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "unknown")
                        .param("column", columnName).param("joinId", joinId);
            }
            if (!containsIgnoreCase(cols, columnName)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", String.valueOf(
                                _NopMetadataCoreConstants.JOIN_SIDE_LEFT.equalsIgnoreCase(declaredSide)
                                        ? leftEp.table.getTableType() : rightEp.table.getTableType()))
                        .param("column", columnName).param("joinId", joinId);
            }
            return new CrossDbField(resolvedSide, columnName);
        }

        private CrossDbField resolveMixed(String entityFieldId, String name, String declaredSide) {
            if (entityFieldId == null || entityFieldId.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
            }
            NopMetaEntityField field = tryLoadEntityField(entityFieldId);
            if (field != null && field.getFieldName() != null && !field.getFieldName().isEmpty()
                    && equalsStr(field.getMetaEntityId(), entityEndpoint.getMetaEntityId())) {
                String resolvedSide = entityOnLeft
                        ? _NopMetadataCoreConstants.JOIN_SIDE_LEFT : _NopMetadataCoreConstants.JOIN_SIDE_RIGHT;
                if (declaredSide != null && !declaredSide.isEmpty()
                        && !declaredSide.equalsIgnoreCase(resolvedSide)) {
                    throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_ENTITY_SIDE_MISMATCH)
                            .param("metaTableId", ownerTable.getMetaTableId())
                            .param("name", name)
                            .param("declaredSide", declaredSide)
                            .param("resolvedSide", resolvedSide)
                            .param("fieldMetaEntityId", String.valueOf(field.getMetaEntityId()))
                            .param("joinId", joinId);
                }
                return new CrossDbField(resolvedSide, field.getFieldName());
            }
            if (declaredSide == null || declaredSide.isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_SIDE_REQUIRED)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("joinId", joinId);
            }
            String expectedSide = entityOnLeft
                    ? _NopMetadataCoreConstants.JOIN_SIDE_RIGHT : _NopMetadataCoreConstants.JOIN_SIDE_LEFT;
            if (!expectedSide.equalsIgnoreCase(declaredSide)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", "entity")
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            Set<String> cols = ensureMixedTableCols();
            if (!containsIgnoreCase(cols, entityFieldId)) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                        .param("metaTableId", ownerTable.getMetaTableId())
                        .param("name", name).param("side", declaredSide)
                        .param("endpointTableType", String.valueOf(tableEndpoint.getTableType()))
                        .param("column", entityFieldId).param("joinId", joinId);
            }
            return new CrossDbField(declaredSide.toLowerCase(java.util.Locale.ROOT), entityFieldId);
        }

        private Set<String> ensureLeftCols() {
            if (leftCols == null) {
                leftCols = resolveTableColumnNames(leftEp.table,
                        ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
            }
            return leftCols;
        }

        private Set<String> ensureRightCols() {
            if (rightCols == null) {
                rightCols = resolveTableColumnNames(rightEp.table,
                        ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
            }
            return rightCols;
        }

        private Set<String> ensureMixedTableCols() {
            if (tableCols == null) {
                tableCols = resolveTableColumnNames(tableEndpoint,
                        ctx.daoProvider().daoFor(NopMetaEntityField.class), ctx);
            }
            return tableCols;
        }

        private NopMetaEntityField tryLoadEntityField(String entityFieldId) {
            try {
                return ctx.daoProvider().daoFor(NopMetaEntityField.class).getEntityById(entityFieldId);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ============================ 静态辅助方法 ============================

    public static String safeAlias(String name) {
        if (name == null) {
            return "v";
        }
        String s = name.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0)) && s.charAt(0) != '_') {
            s = "v_" + s;
        }
        return s.toUpperCase(Locale.ROOT);
    }

    public static Map<String, Object> buildResult(List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items != null ? items : new ArrayList<>());
        return result;
    }

    public static String aggSqlOf(String aggFunc, String column, String measureName) {
        if (aggFunc == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                    .param("aggFunc", String.valueOf(aggFunc)).param("measureName", measureName);
        }
        switch (aggFunc) {
            case _NopMetadataCoreConstants.AGG_FUNC_SUM:
                return "SUM(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_COUNT:
                return "COUNT(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_AVG:
                return "AVG(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_MIN:
                return "MIN(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_MAX:
                return "MAX(" + column + ")";
            case _NopMetadataCoreConstants.AGG_FUNC_COUNT_DISTINCT:
                return "COUNT(DISTINCT " + column + ")";
            default:
                throw new NopException(NopMetadataErrors.ERR_AGGR_AGG_FUNC_UNSUPPORTED)
                        .param("aggFunc", aggFunc).param("measureName", measureName);
        }
    }

    public static List<Map<String, Object>> executeJdbcQuery(Connection conn, String sql, List<Object> filterParams,
                                                              Long limit, Long offset, String metaTableId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement st = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object p : filterParams) {
                st.setObject(idx++, p);
            }
            if (limit != null) {
                st.setObject(idx++, limit);
            }
            if (offset != null && offset > 0) {
                st.setObject(idx++, offset);
            }
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= columnCount; c++) {
                        String label = meta.getColumnLabel(c);
                        if (label == null || label.isEmpty()) {
                            label = meta.getColumnName(c);
                        }
                        row.put(label, rs.getObject(c));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED)
                    .param("metaTableId", metaTableId)
                    .param("error", messageOf(e))
                    .cause(e);
        }
    }

    public static List<Map<String, Object>> collectRows(IDataSet ds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (IDataRow row : ds) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < row.getFieldCount(); i++) {
                map.put(row.getMeta().getFieldName(i), row.getObject(i));
            }
            rows.add(map);
        }
        return rows;
    }

    public static String requireName(String value, String what) {
        if (value == null || value.trim().isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED).param("error", what + " is empty");
        }
        return value;
    }

    public static Set<String> resolveTableColumnNames(NopMetaTable table, IEntityDao<NopMetaEntityField> fieldDao,
                                                       MetaQueryContext ctx) {
        List<ResolvedTableField> fields = ctx.fieldResolver().resolve(table, fieldDao);
        Set<String> names = new LinkedHashSet<>(fields.size());
        for (ResolvedTableField f : fields) {
            names.add(f.getName());
        }
        return names;
    }

    public static String resolveExternalFieldOrThrow(Set<String> columns, String field, NopMetaTable table,
                                                      String side, String joinId) {
        if (field == null || field.isEmpty() || !containsIgnoreCase(columns, field)) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_JOIN_FIELD_NOT_ON_SIDE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "join-field")
                    .param("side", side)
                    .param("endpointTableType", String.valueOf(table.getTableType()))
                    .param("column", String.valueOf(field))
                    .param("joinId", joinId);
        }
        return field;
    }

    public static NopMetaDataSource resolveSharedDataSourceOrThrow(NopMetaTable table, MetaQueryContext ctx, String joinId) {
        IEntityDao<NopMetaDataSource> dsDao = ctx.daoProvider().daoFor(NopMetaDataSource.class);
        try {
            return ctx.dataSourceResolver().resolveActiveOrThrow(dsDao, table.getQuerySpace());
        } catch (NopException e) {
            if (e.getParam("joinId") == null) {
                e.param("joinId", joinId).param("tableId", table.getMetaTableId());
            }
            throw e;
        }
    }

    public static Map<String, String> resolveEntityColumns(NopMetaEntity entity, MetaQueryContext ctx) {
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, entity.getMetaEntityId()));
        List<NopMetaEntityField> fields = fieldDao.findAllByQuery(q);
        Map<String, String> propToCol = new LinkedHashMap<>();
        for (NopMetaEntityField f : fields) {
            if (f.getFieldName() != null && f.getColumnCode() != null) {
                propToCol.put(f.getFieldName(), f.getColumnCode());
            }
        }
        return propToCol;
    }

    public static TreeBean rewriteFilterToColumns(TreeBean filter, Map<String, String> propToCol) {
        if (filter == null) {
            return null;
        }
        TreeBean copy = new TreeBean(filter.getTagName());
        if (filter.getAttrs() != null) {
            copy.setAttrs(new LinkedHashMap<>(filter.getAttrs()));
        }
        Object name = copy.getAttr("name");
        if (name != null && propToCol.containsKey(name.toString())) {
            copy.setAttr("name", propToCol.get(name.toString()));
        }
        if (filter.getChildren() != null) {
            List<TreeBean> children = new ArrayList<>();
            for (TreeBean child : filter.getChildren()) {
                children.add(rewriteFilterToColumns(child, propToCol));
            }
            copy.setChildren(children);
        }
        return copy;
    }

    public static String resolveEntityFieldColumn(String entityFieldId, String name, NopMetaTable table,
                                                   MetaQueryContext ctx, Map<String, String> propToCol) {
        if (entityFieldId == null || entityFieldId.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", String.valueOf(entityFieldId));
        }
        IEntityDao<NopMetaEntityField> fieldDao = ctx.daoProvider().daoFor(NopMetaEntityField.class);
        NopMetaEntityField field = fieldDao.getEntityById(entityFieldId);
        if (field == null || field.getColumnCode() == null) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_FIELD_NOT_RESOLVED)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", name).param("entityFieldId", entityFieldId);
        }
        return field.getColumnCode();
    }

    public static Map<String, String> buildNameToExprTable(List<MeasureSpec> measures, List<DimensionSpec> dims,
                                                            List<String> measureNames, List<String> dimensionNames,
                                                            NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).aggSql);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: dims/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < dims.size(); i++) {
            map.put(dimensionNames.get(i), dims.get(i).column);
        }
        return map;
    }

    public static Map<String, String> buildJoinNameToExprTable(List<JoinMeasureSpec> measures,
                                                                List<JoinDimensionSpec> dims,
                                                                List<String> measureNames,
                                                                List<String> dimensionNames, NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: join measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).aggSql);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: join dims/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < dims.size(); i++) {
            map.put(dimensionNames.get(i), dims.get(i).qualifiedCol);
        }
        return map;
    }

    public static Function<String, String> nameResolverFor(Map<String, String> nameToExpr,
                                                            NopMetaTable table,
                                                            List<String> measureNames,
                                                            List<String> dimensionNames,
                                                            String clause) {
        return name -> {
            if (!FilterToSqlTranslator.IDENTIFIER_PATTERN.matcher(name).matches()) {
                return name;
            }
            String expr = nameToExpr.get(name);
            if (expr == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", name)
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (expr.indexOf('?') >= 0) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", name)
                        .param("clause", clause);
            }
            return expr;
        };
    }

    public static String buildOrderByClause(List<OrderFieldBean> orderBy, Map<String, String> nameToExpr,
                                             NopMetaTable table, List<String> measureNames,
                                             List<String> dimensionNames, String clause) {
        if (orderBy == null || orderBy.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderBy.size(); i++) {
            OrderFieldBean f = orderBy.get(i);
            String name = f.getName();
            String expr = nameToExpr.get(name);
            if (expr == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_ORDER_BY_UNKNOWN_NAME)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", String.valueOf(name))
                        .param("selectedMeasures", String.valueOf(measureNames))
                        .param("selectedDimensions", String.valueOf(dimensionNames));
            }
            if (expr.indexOf('?') >= 0) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXPRESSION_HAVING_ORDER_BY_UNSUPPORTED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("measureName", String.valueOf(name))
                        .param("clause", clause);
            }
            if (i > 0) {
                sb.append(",");
            }
            sb.append(expr).append(f.isDesc() ? " DESC" : " ASC");
            Boolean nullsFirst = f.getNullsFirst();
            if (nullsFirst != null) {
                sb.append(nullsFirst ? " NULLS FIRST" : " NULLS LAST");
            }
        }
        return sb.toString();
    }

    public static List<NopMetaTableMeasure> loadMeasures(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        IEntityDao<NopMetaTableMeasure> dao = ctx.daoProvider().daoFor(NopMetaTableMeasure.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableMeasure.PROP_NAME_metaTableId, table.getMetaTableId()));
        List<NopMetaTableMeasure> all = dao.findAllByQuery(q);
        Map<String, NopMetaTableMeasure> byName = new LinkedHashMap<>();
        for (NopMetaTableMeasure m : all) {
            byName.put(m.getMeasureName(), m);
        }
        List<NopMetaTableMeasure> result = new ArrayList<>();
        for (String name : names) {
            NopMetaTableMeasure m = byName.get(name);
            if (m == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_MEASURE_NOT_FOUND)
                        .param("metaTableId", table.getMetaTableId()).param("measureName", name);
            }
            result.add(m);
        }
        return result;
    }

    public static List<NopMetaTableDimension> loadDimensions(NopMetaTable table, List<String> names, MetaQueryContext ctx) {
        IEntityDao<NopMetaTableDimension> dao = ctx.daoProvider().daoFor(NopMetaTableDimension.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTableDimension.PROP_NAME_metaTableId, table.getMetaTableId()));
        List<NopMetaTableDimension> all = dao.findAllByQuery(q);
        Map<String, NopMetaTableDimension> byName = new LinkedHashMap<>();
        for (NopMetaTableDimension d : all) {
            byName.put(d.getDimensionName(), d);
        }
        List<NopMetaTableDimension> result = new ArrayList<>();
        for (String name : names) {
            NopMetaTableDimension d = byName.get(name);
            if (d == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_DIMENSION_NOT_FOUND)
                        .param("metaTableId", table.getMetaTableId()).param("dimensionName", name);
            }
            result.add(d);
        }
        return result;
    }

    public static String endpointTypeOf(MetaJoinExecutor.Endpoint ep) {
        if (ep.isEntity) {
            return "entity";
        }
        return ep.table == null ? "unknown" : String.valueOf(ep.table.getTableType());
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>>[] newArrayHolder() {
        return (List<Map<String, Object>>[]) new List<?>[1];
    }

    public static boolean containsIgnoreCase(Set<String> cols, String name) {
        if (cols == null || name == null) {
            return false;
        }
        for (String c : cols) {
            if (c != null && c.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean equalsStr(String a, String b) {
        return (a == null) ? b == null : a.equals(b);
    }

    public static String crossDbAliasOf(NopMetaTableJoin join) {
        String a = join.getAlias();
        return (a != null && !a.trim().isEmpty()) ? a : "right";
    }

    public static Object getCaseInsensitiveObj(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static String findKeyIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(key)) {
                return k;
            }
        }
        return null;
    }

    public static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            return null;
        }
    }

    public static String messageOf(Throwable t) {
        String m = t.getMessage();
        return m != null ? m : t.getClass().getName();
    }

    public static String externalTableFromForJoin(NopMetaTable table, String alias) {
        FilterToSqlTranslator.validateIdentifier(alias);
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("error", "sql table sourceSql is empty");
            }
            return "(" + sourceSql + ") " + alias;
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName + " " + alias;
    }

    public static String buildEntityFromClause(String physicalTable, String schema, String alias) {
        FilterToSqlTranslator.validateIdentifier(alias);
        if (schema != null && !schema.trim().isEmpty()) {
            FilterToSqlTranslator.validateIdentifier(schema);
            return schema + "." + physicalTable + " " + alias;
        }
        return physicalTable + " " + alias;
    }

    public static boolean isEntityTableVisible(DatabaseMetaData metaData, String schema, String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return false;
        }
        return checkTableExists(metaData, schema, tableName)
                || checkTableExists(metaData, schema, tableName.toUpperCase(Locale.ROOT))
                || checkTableExists(metaData, schema, tableName.toLowerCase(Locale.ROOT));
    }

    public static boolean checkTableExists(DatabaseMetaData metaData, String schema, String tableName) {
        try (ResultSet rs = metaData.getTables(null, schema, tableName, null)) {
            while (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            LOG.warn("checkTableExists: getTables failed (treated as not visible): schema={}, table={}",
                    schema, tableName, e);
        }
        return false;
    }

    public static java.math.BigDecimal toBigDecimal(Object v) {
        if (v instanceof java.math.BigDecimal) {
            return (java.math.BigDecimal) v;
        }
        if (v instanceof java.math.BigInteger) {
            return new java.math.BigDecimal((java.math.BigInteger) v);
        }
        if (v instanceof Number) {
            return java.math.BigDecimal.valueOf(((Number) v).doubleValue());
        }
        return null;
    }

    // ============================ 外部/sql 路径辅助（ExternalAggregationProcessor / SqlAggregationProcessor 共用）============================

    /** external: {@code FROM <tableName>}；sql: {@code FROM (<sourceSql>) _t}。标识符/表名经白名单校验。 */
    public static String buildFromClause(NopMetaTable table) {
        if (_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(table.getTableType())) {
            String sourceSql = table.getSourceSql();
            if (sourceSql == null || sourceSql.trim().isEmpty()) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_EXEC_FAILED)
                        .param("metaTableId", table.getMetaTableId())
                        .param("error", "sql table sourceSql is empty");
            }
            return "(" + sourceSql + ") _t";
        }
        String tableName = table.getTableName();
        FilterToSqlTranslator.validateIdentifier(tableName);
        return tableName;
    }

    /**
     * 构建 external/sql 路径聚合 SQL。
     */
    public static String buildExternalAggregationSql(NopMetaTable table, List<MeasureSpec> measures,
                                                      List<DimensionSpec> dims, TreeBean filter, TreeBean having,
                                                      List<OrderFieldBean> orderBy, Map<String, String> nameToExpr,
                                                      List<String> measureNames, List<String> dimensionNames,
                                                      Long limit, Long offset,
                                                      String dialect, MetaQueryContext ctx) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            DimensionSpec d = dims.get(i);
            String expr;
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                expr = GranularityBucketing.translate(d.granularity, d.column, dialect, d.alias);
            } else {
                FilterToSqlTranslator.validateIdentifier(d.column);
                expr = d.column;
            }
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (MeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }

        String fromClause = buildFromClause(table);
        sql.append(" FROM ").append(fromClause);

        if (filter != null) {
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            if (tf.getSql() != null && !tf.getSql().isEmpty()) {
                sql.append(" WHERE ").append(tf.getSql());
            }
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (having != null) {
            MetaAggregationExecutor.preprocessHavingArithmetic(having, nameToExpr, table, measureNames, dimensionNames);
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            if (hf.getSql() != null && !hf.getSql().isEmpty()) {
                sql.append(" HAVING ").append(hf.getSql());
            }
        }
        String orderByClause = buildOrderByClause(orderBy, nameToExpr, table, measureNames, dimensionNames, "ORDER_BY");
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        SqlPagination.appendLimitOffset(sql, limit, offset, dialect);
        return sql.toString();
    }

    /**
     * 收集 PreparedStatement 绑定参数，与 SQL 中 ? 出现顺序一致。
     */
    public static List<Object> collectBindParams(List<MeasureSpec> measures, List<DimensionSpec> dims, TreeBean filter,
                                                  TreeBean having, Map<String, String> nameToExpr, MetaQueryContext ctx,
                                                  NopMetaTable table,
                                                  List<String> measureNames, List<String> dimensionNames) {
        List<Object> params = new ArrayList<>();
        for (MeasureSpec m : measures) {
            if (m.expressionParams != null) {
                params.addAll(m.expressionParams);
            }
        }
        if (filter != null) {
            FilterToSqlTranslator.TranslatedFilter tf = ctx.filterTranslator().translate(filter);
            params.addAll(tf.getParams());
        }
        if (having != null) {
            FilterToSqlTranslator.TranslatedFilter hf = ctx.filterTranslator().translate(having,
                    nameResolverFor(nameToExpr, table, measureNames, dimensionNames, "HAVING"));
            params.addAll(hf.getParams());
        }
        return params;
    }

    // ============================ JOIN SQL 构造辅助 ============================

    /** 构造 external↔external JOIN 聚合 SQL。 */
    public static StringBuilder buildExternalExternalJoinSql(List<JoinMeasureSpec> measures, List<JoinDimensionSpec> dims,
                                                              String leftFrom, String rightFrom, String leftJoinCol,
                                                              String rightJoinCol, NopMetaTableJoin join,
                                                              FilterToSqlTranslator.TranslatedFilter filterTf,
                                                              FilterToSqlTranslator.TranslatedFilter havingTf,
                                                              String orderByClause,
                                                              String dialect, Long limit, Long offset) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            JoinDimensionSpec d = dims.get(i);
            String expr;
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                expr = GranularityBucketing.translate(d.granularity, d.qualifiedCol, dialect, d.alias);
                FilterToSqlTranslator.validateIdentifier(d.column);
            } else {
                expr = d.qualifiedCol;
            }
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (JoinMeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }
        sql.append(" FROM ").append(leftFrom);
        String joinKeyword = _NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(join.getJoinType())
                ? " INNER JOIN " : " LEFT JOIN ";
        sql.append(joinKeyword).append(rightFrom)
                .append(" ON l.").append(leftJoinCol)
                .append(" = r.").append(rightJoinCol);

        if (filterTf != null && filterTf.getSql() != null && !filterTf.getSql().isEmpty()) {
            sql.append(" WHERE ").append(filterTf.getSql());
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (havingTf != null && havingTf.getSql() != null && !havingTf.getSql().isEmpty()) {
            sql.append(" HAVING ").append(havingTf.getSql());
        }
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        SqlPagination.appendLimitOffset(sql, limit, offset, dialect);
        return sql;
    }

    /** 构造混合端点同库 JOIN 聚合 SQL。 */
    public static StringBuilder buildMixedSameDbJoinSql(List<JoinMeasureSpec> measures, List<JoinDimensionSpec> dims,
                                                         String entityFrom, String tableFrom,
                                                         String entityAlias, String tableAlias,
                                                         String entityJoinColumn, String tableJoinColumn,
                                                         NopMetaTableJoin join,
                                                         FilterToSqlTranslator.TranslatedFilter filterTf,
                                                         FilterToSqlTranslator.TranslatedFilter havingTf,
                                                         String orderByClause,
                                                         String dialect, Long limit, Long offset) {
        StringBuilder sql = new StringBuilder("SELECT ");
        List<String> groupExprs = new ArrayList<>();
        for (int i = 0; i < dims.size(); i++) {
            JoinDimensionSpec d = dims.get(i);
            String expr;
            if (_NopMetadataCoreConstants.DIMENSION_TYPE_TEMPORAL.equals(d.dimensionType) && d.granularity != null) {
                expr = GranularityBucketing.translate(d.granularity, d.qualifiedCol, dialect, d.alias);
                FilterToSqlTranslator.validateIdentifier(d.column);
            } else {
                expr = d.qualifiedCol;
            }
            sql.append(i == 0 ? "" : ",").append(expr).append(" AS ").append(d.alias);
            groupExprs.add(expr);
        }
        for (JoinMeasureSpec m : measures) {
            sql.append(",").append(m.aggSql).append(" AS ").append(m.alias);
        }
        sql.append(" FROM ").append(entityFrom);
        String joinKeyword = _NopMetadataCoreConstants.JOIN_TYPE_INNER.equals(join.getJoinType())
                ? " INNER JOIN " : " LEFT JOIN ";
        sql.append(joinKeyword).append(tableFrom)
                .append(" ON ").append(entityAlias).append(".").append(entityJoinColumn)
                .append(" = ").append(tableAlias).append(".").append(tableJoinColumn);

        if (filterTf != null && filterTf.getSql() != null && !filterTf.getSql().isEmpty()) {
            sql.append(" WHERE ").append(filterTf.getSql());
        }
        sql.append(" GROUP BY ").append(String.join(",", groupExprs));
        if (havingTf != null && havingTf.getSql() != null && !havingTf.getSql().isEmpty()) {
            sql.append(" HAVING ").append(havingTf.getSql());
        }
        if (!orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }
        SqlPagination.appendLimitOffset(sql, limit, offset, dialect);
        return sql;
    }

    // ============================ 跨库内存聚合辅助（CrossDbInMemoryAggregationProcessor 共用）============================

    /** 跨库 memoryGroupBy + resolveAndValidateLookupKeys + truncateCrossDb + buildCrossDbNameToAliasTable。 */

    public static Map<String, String> buildCrossDbNameToAliasTable(List<CrossDbMeasureSpec> measures,
                                                                     List<CrossDbDimensionSpec> dims,
                                                                     List<String> measureNames,
                                                                     List<String> dimensionNames, NopMetaTable table) {
        Map<String, String> map = new LinkedHashMap<>();
        if (measures.size() != measureNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: cross-db measures/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < measures.size(); i++) {
            map.put(measureNames.get(i), measures.get(i).alias);
        }
        if (dims.size() != dimensionNames.size()) {
            throw new NopException(NopMetadataErrors.ERR_AGGR_HAVING_UNKNOWN_NAME)
                    .param("metaTableId", table.getMetaTableId())
                    .param("name", "<internal>: cross-db dims/names length mismatch")
                    .param("selectedMeasures", String.valueOf(measureNames))
                    .param("selectedDimensions", String.valueOf(dimensionNames));
        }
        for (int i = 0; i < dims.size(); i++) {
            map.put(dimensionNames.get(i), dims.get(i).alias);
        }
        return map;
    }

    /**
     * 解析合并行实际 lookup key + 校验存在（Anti-Hollow #24 核心）。
     */
    public static void resolveAndValidateLookupKeys(List<? extends CrossDbFieldSpec> specs, String alias,
                                                      Map<String, Object> sampleRow, NopMetaTable table, String joinId,
                                                      String fieldKind) {
        for (CrossDbFieldSpec spec : specs) {
            String rawKey = spec.rawKey;
            String lookupKey;
            if (_NopMetadataCoreConstants.JOIN_SIDE_RIGHT.equalsIgnoreCase(spec.side)) {
                String prefixed = alias + "_" + rawKey;
                String actualPrefixed = findKeyIgnoreCase(sampleRow, prefixed);
                lookupKey = actualPrefixed != null ? actualPrefixed : rawKey;
            } else {
                lookupKey = rawKey;
            }
            String actual = findKeyIgnoreCase(sampleRow, lookupKey);
            if (actual == null) {
                throw new NopException(NopMetadataErrors.ERR_AGGR_CROSS_DB_FIELD_KEY_MISSING)
                        .param("metaTableId", table.getMetaTableId())
                        .param("name", spec.alias)
                        .param("fieldKind", fieldKind)
                        .param("rawKey", String.valueOf(rawKey))
                        .param("lookupKey", String.valueOf(lookupKey))
                        .param("rowKeys", sampleRow.keySet())
                        .param("joinId", joinId);
            }
            spec.lookupKey = actual;
        }
    }

    /**
     * 内存 GROUP BY：按 dimension 值分组 → 按 aggFunc 内存累加 → 输出 items。
     */
    public static List<Map<String, Object>> memoryGroupBy(List<Map<String, Object>> rows,
                                                           List<CrossDbMeasureSpec> measures,
                                                           List<CrossDbDimensionSpec> dims) {
        LinkedHashMap<String, Map<String, Object>> groupDims = new LinkedHashMap<>();
        LinkedHashMap<String, MemAggAccumulator[]> groupAccs = new LinkedHashMap<>();

        for (Map<String, Object> row : rows) {
            StringBuilder keyBuilder = new StringBuilder();
            Object[] dimValues = new Object[dims.size()];
            for (int i = 0; i < dims.size(); i++) {
                Object v = getCaseInsensitiveObj(row, dims.get(i).lookupKey);
                dimValues[i] = v;
                if (i > 0) {
                    keyBuilder.append('\u0001');
                }
                keyBuilder.append(v == null ? "\u0000" : String.valueOf(v));
            }
            String groupKey = keyBuilder.toString();

            Map<String, Object> gRow = groupDims.get(groupKey);
            MemAggAccumulator[] accs = groupAccs.get(groupKey);
            if (gRow == null) {
                gRow = new LinkedHashMap<>();
                for (int i = 0; i < dims.size(); i++) {
                    gRow.put(dims.get(i).alias, dimValues[i]);
                }
                groupDims.put(groupKey, gRow);
                accs = MemAggAccumulator.newAccumulators(measures);
                groupAccs.put(groupKey, accs);
            }
            for (int i = 0; i < measures.size(); i++) {
                Object val = getCaseInsensitiveObj(row, measures.get(i).lookupKey);
                accs[i].accumulate(val);
            }
        }

        List<Map<String, Object>> items = new ArrayList<>(groupDims.size());
        for (Map.Entry<String, Map<String, Object>> e : groupDims.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>(e.getValue());
            MemAggAccumulator[] accs = groupAccs.get(e.getKey());
            for (int i = 0; i < measures.size(); i++) {
                item.put(measures.get(i).alias, accs[i].result());
            }
            items.add(item);
        }
        return items;
    }

    /** 合并后截断提示（D5 分页：内存合并无全局序，limit/offset 仅截断提示）。 */
    public static List<Map<String, Object>> truncateCrossDb(List<Map<String, Object>> rows, Long limit, Long offset) {
        int from = 0;
        if (offset != null && offset > 0) {
            if (offset > Integer.MAX_VALUE) {
                throw new NopException(NopMetadataErrors.ERR_PAGINATION_OFFSET_TOO_LARGE).param("offset", offset);
            }
            from = offset.intValue();
        }
        if (from > rows.size()) {
            from = rows.size();
        }
        int to = rows.size();
        if (limit != null) {
            if (limit > Integer.MAX_VALUE) {
                throw new NopException(NopMetadataErrors.ERR_PAGINATION_LIMIT_TOO_LARGE).param("limit", limit);
            }
            to = Math.min(rows.size(), from + limit.intValue());
        }
        return new ArrayList<>(rows.subList(from, to));
    }

    // ============================ JOIN 公共辅助方法 (share between processors) ============================

    public static List<JoinMeasureSpec> loadJoinMeasuresWithResolver(NopMetaTable table, List<String> names,
                                                                       MetaQueryContext ctx,
                                                                       JoinFieldResolverFn resolver,
                                                                       Set<String> leftCols, Set<String> rightCols) {
        List<NopMetaTableMeasure> all = loadMeasures(table, names, ctx);
        List<JoinMeasureSpec> specs = new ArrayList<>();
        for (NopMetaTableMeasure m : all) {
            if (m.getExpression() != null && !m.getExpression().trim().isEmpty()) {
                ExpressionMeasureValidator.ValidatedExpression ve =
                        ExpressionMeasureValidator.validateStatic(m.getExpression(),
                                ExpressionMeasureValidator.ValidationOptions.joinStrict(leftCols, rightCols),
                                table.getMetaTableId(), m.getMeasureName());
                specs.add(new JoinMeasureSpec(safeAlias(m.getMeasureName()),
                        aggSqlOf(m.getAggFunc(), ve.sqlFragment, m.getMeasureName()),
                        "<expression>", ve.params, ve));
                continue;
            }
            JoinField f = resolver.resolve(m.getEntityFieldId(), m.getMeasureName(), m.getSide());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinMeasureSpec(safeAlias(m.getMeasureName()),
                    aggSqlOf(m.getAggFunc(), f.qualifiedColumn, m.getMeasureName()), f.qualifiedColumn));
        }
        return specs;
    }

    public static List<JoinDimensionSpec> loadJoinDimensionsWithResolver(NopMetaTable table, List<String> names,
                                                                          MetaQueryContext ctx,
                                                                          JoinFieldResolverFn resolver) {
        List<NopMetaTableDimension> all = loadDimensions(table, names, ctx);
        List<JoinDimensionSpec> specs = new ArrayList<>();
        for (NopMetaTableDimension d : all) {
            JoinField f = resolver.resolve(d.getEntityFieldId(), d.getDimensionName(), d.getSide());
            FilterToSqlTranslator.validateIdentifier(f.column);
            specs.add(new JoinDimensionSpec(safeAlias(d.getDimensionName()), f.qualifiedColumn, f.column,
                    d.getDimensionType(), d.getGranularity()));
        }
        return specs;
    }
}
