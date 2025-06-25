package io.nop.search.lucene;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.util.IVariableScope;
import io.nop.core.model.query.FilterBeanVisitor;
import io.nop.core.model.query.FilterOp;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.util.BytesRef;

public class LuceneFilterBeanTransformer extends FilterBeanVisitor<Query> {
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

        if (filterOp == FilterOp.EQ) {
            // 等于：TermQuery 精确匹配
            return new TermQuery(new Term(field, String.valueOf(value)));
        } else if (filterOp == FilterOp.NE) {
            // 不等于：MUST_NOT + MatchAllDocsQuery
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(new TermQuery(new Term(field, String.valueOf(value))), BooleanClause.Occur.MUST_NOT);
            builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
            return builder.build();
        } else if (filterOp == FilterOp.GT || filterOp == FilterOp.GE ||
                filterOp == FilterOp.LT || filterOp == FilterOp.LE) {
            // 范围查询（GT/GE/LT/LE）
            if (value instanceof Number) {
                // 数字类型用 PointRangeQuery
                double numValue = ((Number) value).doubleValue();
                if (filterOp == FilterOp.GT) {
                    return DoublePoint.newRangeQuery(field, numValue, Double.POSITIVE_INFINITY);
                } else if (filterOp == FilterOp.GE) {
                    return DoublePoint.newRangeQuery(field, numValue, Double.POSITIVE_INFINITY);
                } else if (filterOp == FilterOp.LT) {
                    return DoublePoint.newRangeQuery(field, Double.NEGATIVE_INFINITY, numValue);
                } else { //LE
                    return DoublePoint.newRangeQuery(field, Double.NEGATIVE_INFINITY, numValue);
                }
            } else {
                // 字符串类型用 TermRangeQuery
                String strValue = String.valueOf(value);
                if (filterOp == FilterOp.GT) {
                    return TermRangeQuery.newStringRange(field, strValue, null, false, true);
                } else if (filterOp == FilterOp.GE) {
                    return TermRangeQuery.newStringRange(field, strValue, null, true, true);
                } else if (filterOp == FilterOp.LT) {
                    return TermRangeQuery.newStringRange(field, null, strValue, true, false);
                } else { // LE
                    return TermRangeQuery.newStringRange(field, null, strValue, true, true);
                }
            }
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
}