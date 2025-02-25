package io.nop.search.lucene;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.util.IVariableScope;
import io.nop.core.model.query.FilterBeanVisitor;
import io.nop.core.model.query.FilterOp;
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
        // Implement comparison logic based on filterOp
        String field = getName(filter);
        Object value = getValue(filter);
        return new TermQuery(new Term(field, String.valueOf(value)));
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