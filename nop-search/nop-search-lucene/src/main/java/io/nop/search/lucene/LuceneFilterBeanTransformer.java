package io.nop.search.lucene;

import io.nop.api.core.beans.FilterBeanConstants;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IVariableScope;
import io.nop.core.model.query.FilterBeanVisitor;
import io.nop.core.model.query.FilterOp;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;

import java.util.Set;

import static io.nop.search.lucene.LuceneErrors.ARG_FIELD;
import static io.nop.search.lucene.LuceneErrors.ARG_VALUE;
import static io.nop.search.lucene.LuceneErrors.ERR_LUCENE_FIELD_NOT_FILTERABLE;
import static io.nop.search.lucene.LuceneErrors.ERR_LUCENE_INVALID_FILTER_VALUE;

public class LuceneFilterBeanTransformer extends FilterBeanVisitor<Query> {
    /**
     * 以 LongPoint 建索引的数值字段，查询必须用 LongPoint 系列构造。
     */
    private final Set<String> longPointFields;

    /**
     * 仅存储、未建索引的字段，不支持过滤，直接报错。
     */
    private final Set<String> storedOnlyFields;

    public LuceneFilterBeanTransformer() {
        this(LuceneSearchEngine.LONG_POINT_FIELDS, LuceneSearchEngine.STORED_ONLY_FIELDS);
    }

    public LuceneFilterBeanTransformer(Set<String> longPointFields, Set<String> storedOnlyFields) {
        this.longPointFields = longPointFields == null ? Set.of() : longPointFields;
        this.storedOnlyFields = storedOnlyFields == null ? Set.of() : storedOnlyFields;
    }

    @Override
    protected Query visitFixedValue(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String field = getName(filter);
        Object value = getValue(filter);
        return new TermQuery(new Term(field, String.valueOf(value)));
    }

    @Override
    public Query visitAlwaysTrue(ITreeBean filter, IVariableScope scope) {
        return new MatchAllDocsQuery();
    }

    @Override
    public Query visitAlwaysFalse(ITreeBean filter, IVariableScope scope) {
        return new BooleanQuery.Builder().build(); // Empty BooleanQuery acts as always false
    }

    @Override
    protected Query visitCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        String field = getName(filter);
        Object value = getValue(filter);

        checkFieldFilterable(field, filter);

        if (longPointFields.contains(field)) {
            return visitLongCompareOp(filterOp, filter, scope, field, value);
        }

        if (filterOp == FilterOp.EQ) {
            // 等于：TermQuery 精确匹配
            return new TermQuery(new Term(field, String.valueOf(value)));
        } else if (filterOp == FilterOp.NE) {
            // 不等于：MUST_NOT + MatchAllDocsQuery
            return notQuery(new TermQuery(new Term(field, String.valueOf(value))));
        } else if (filterOp == FilterOp.GT || filterOp == FilterOp.GE ||
                filterOp == FilterOp.LT || filterOp == FilterOp.LE) {
            // 范围查询（GT/GE/LT/LE）
            String strValue = String.valueOf(value);
            if (filterOp == FilterOp.GT) {
                return TermRangeQuery.newStringRange(field, strValue, null, false, true);
            } else if (filterOp == FilterOp.GE) {
                return TermRangeQuery.newStringRange(field, strValue, null, true, true);
            } else if (filterOp == FilterOp.LT) {
                return TermRangeQuery.newStringRange(field, null, strValue, true, false);
            } else { //LE
                return TermRangeQuery.newStringRange(field, null, strValue, true, true);
            }
        }

        throw new UnsupportedOperationException("Unsupported comparison operator: " + filterOp);
    }

    /**
     * 数值字段以 LongPoint 建索引（见 LuceneSearchEngine.addNumericField），必须用 LongPoint 查询。
     * GT/LT 不含边界，GE/LE 含边界。
     */
    protected Query visitLongCompareOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope,
                                       String field, Object value) {
        double numValue = toDoubleValue(field, filter, value);

        if (filterOp == FilterOp.EQ) {
            if (!isIntegralInLongRange(numValue))
                return visitAlwaysFalse(filter, scope);
            return LongPoint.newExactQuery(field, (long) numValue);
        } else if (filterOp == FilterOp.NE) {
            if (!isIntegralInLongRange(numValue))
                return new MatchAllDocsQuery();
            return notQuery(LongPoint.newExactQuery(field, (long) numValue));
        } else if (filterOp == FilterOp.GT) {
            if (numValue >= Long.MAX_VALUE)
                return visitAlwaysFalse(filter, scope);
            return LongPoint.newRangeQuery(field, floorPlusOne(numValue), Long.MAX_VALUE);
        } else if (filterOp == FilterOp.GE) {
            if (numValue >= Long.MAX_VALUE)
                return visitAlwaysFalse(filter, scope);
            return LongPoint.newRangeQuery(field, ceilToLong(numValue), Long.MAX_VALUE);
        } else if (filterOp == FilterOp.LT) {
            if (numValue <= Long.MIN_VALUE)
                return visitAlwaysFalse(filter, scope);
            return LongPoint.newRangeQuery(field, Long.MIN_VALUE, ceilMinusOne(numValue));
        } else if (filterOp == FilterOp.LE) {
            if (numValue < Long.MIN_VALUE)
                return visitAlwaysFalse(filter, scope);
            return LongPoint.newRangeQuery(field, Long.MIN_VALUE, floorToLong(numValue));
        }

        throw new UnsupportedOperationException("Unsupported comparison operator: " + filterOp);
    }

    @Override
    protected Query visitAssertOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        // Implement assert logic based on filterOp
        return visitUnknown(filter.getTagName(), filter, scope);
    }

    @Override
    protected Query visitBetweenOp(FilterOp filterOp, ITreeBean filter, IVariableScope scope) {
        // Implement between logic based on filterOp
        String field = getName(filter);
        checkFieldFilterable(field, filter);

        if (longPointFields.contains(field)) {
            double minValue = toDoubleValue(field, filter, getMin(filter));
            double maxValue = toDoubleValue(field, filter, getMax(filter));

            boolean excludeMin = isExcludeMin(filter);
            // 直接读取 excludeMax 属性：基类 FilterBeanVisitor.isExcludeMax 误读 excludeMin（上游缺陷），
            // 数值分支按正确语义处理
            boolean excludeMax = ConvertHelper.toPrimitiveBoolean(
                    filter.getAttr(FilterBeanConstants.FILTER_ATTR_EXCLUDE_MAX), NopException::new);

            // 折算为闭区间边界
            long lo = excludeMin ? floorPlusOne(minValue) : ceilToLong(minValue);
            long hi = excludeMax ? ceilMinusOne(maxValue) : floorToLong(maxValue);
            if (lo > hi)
                return visitAlwaysFalse(filter, scope);
            return LongPoint.newRangeQuery(field, lo, hi);
        }

        Object lowerValue = getMin(filter);
        Object upperValue = getMax(filter);
        boolean excludeMin = isExcludeMin(filter);
        boolean excludeMax = isExcludeMax(filter);

        return new TermRangeQuery(field, new BytesRef(String.valueOf(lowerValue)), new BytesRef(String.valueOf(upperValue)), !excludeMin, excludeMax);
    }

    @Override
    public Query visitAnd(ITreeBean filter, IVariableScope scope) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (ITreeBean child : filter.getChildren()) {
            builder.add(visit(child, scope), BooleanClause.Occur.MUST);
        }
        return builder.build();
    }

    @Override
    public Query visitOr(ITreeBean filter, IVariableScope scope) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (ITreeBean child : filter.getChildren()) {
            builder.add(visit(child, scope), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    @Override
    public Query visitNot(ITreeBean filter, IVariableScope scope) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (ITreeBean child : filter.getChildren()) {
            builder.add(visit(child, scope), BooleanClause.Occur.MUST_NOT);
        }
        return builder.build();
    }

    /**
     * 仅存储未建索引的字段（如 path）无法过滤：静默不命中或全量命中都是错误结果，因此直接报错。
     */
    protected void checkFieldFilterable(String field, ITreeBean filter) {
        if (storedOnlyFields.contains(field)) {
            throw new NopException(ERR_LUCENE_FIELD_NOT_FILTERABLE)
                    .param(ARG_FIELD, field)
                    .source(filter);
        }
    }

    /**
     * 取补集：纯 MUST_NOT 子句在 Lucene 中匹配空集，必须补 MatchAllDocsQuery 锚点
     */
    protected Query notQuery(Query query) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(query, BooleanClause.Occur.MUST_NOT);
        builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
        return builder.build();
    }

    protected double toDoubleValue(String field, ITreeBean filter, Object value) {
        double d;
        if (value instanceof Number) {
            d = ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                d = Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException e) {
                throw invalidFilterValue(field, filter, value);
            }
        } else {
            throw invalidFilterValue(field, filter, value);
        }
        if (!Double.isFinite(d))
            throw invalidFilterValue(field, filter, value);
        return d;
    }

    protected NopException invalidFilterValue(String field, ITreeBean filter, Object value) {
        return new NopException(ERR_LUCENE_INVALID_FILTER_VALUE)
                .param(ARG_FIELD, field)
                .param(ARG_VALUE, value)
                .source(filter);
    }

    /**
     * 数值是否为 long 范围内的整数。long 字段与非整数、或超出 long 范围的值不可能精确相等
     */
    protected static boolean isIntegralInLongRange(double d) {
        return d == Math.floor(d) && d >= Long.MIN_VALUE && d < Long.MAX_VALUE;
    }

    /**
     * 大于 d 的最小 long（d 已保证为有限数且小于 Long.MAX_VALUE）
     */
    protected static long floorPlusOne(double d) {
        long v = floorToLong(d);
        return v == Long.MAX_VALUE ? v : v + 1;
    }

    /**
     * 小于 d 的最大 long（d 已保证为有限数且大于 Long.MIN_VALUE）
     */
    protected static long ceilMinusOne(double d) {
        long v = ceilToLong(d);
        return v == Long.MIN_VALUE ? v : v - 1;
    }

    protected static long floorToLong(double d) {
        return (long) Math.floor(d);
    }

    protected static long ceilToLong(double d) {
        return (long) Math.ceil(d);
    }
}
