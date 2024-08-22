package io.nop.graphql.core.engine;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.core.lang.eval.IEvalScope;

import java.util.Map;

public interface IGraphQLFetcher {

    @EvalMethod
    IGraphQLSourceFetcher forSource(IEvalScope scope,
                                    Object source, String sourceType);

    @EvalMethod
    IGraphQLSourceFetcher forSource(IEvalScope scope,
                                    Object source);

    interface IGraphQLSourceFetcher {
        Object fetch(String fieldName);

        Map<String, Object> fetchAll(FieldSelectionBean selectionBean);
    }
}
