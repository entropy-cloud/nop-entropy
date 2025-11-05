/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.convert.ITypeConverter;
import io.nop.api.core.exceptions.NopException;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;

import java.util.concurrent.CompletionStage;

import static io.nop.graphql.core.GraphQLErrors.ARG_FIELD_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;

public class ConvertTypeFetcher implements IDataFetcher {
    private final ITypeConverter converter;
    private final IDataFetcher fetcher;

    public ConvertTypeFetcher(ITypeConverter converter, IDataFetcher fetcher) {
        this.converter = converter;
        this.fetcher = fetcher;
    }

    @Override
    public Object get(IDataFetchingEnvironment env) {
        Object value = fetcher.get(env);
        if (value instanceof CompletionStage)
            return ((CompletionStage<?>) value).thenApply(v -> convertType(v, env));
        return convertType(value, env);
    }

    private Object convertType(Object value, IDataFetchingEnvironment env) {
        return converter.convert(value, err -> new NopException(err).param(ARG_OBJ_NAME, env.getObjName())
                .param(ARG_FIELD_NAME, env.getSelection().getName()));
    }
}
