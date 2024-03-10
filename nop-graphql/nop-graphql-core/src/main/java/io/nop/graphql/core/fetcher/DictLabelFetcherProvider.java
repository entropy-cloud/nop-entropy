/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.fetcher;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IDataFetcher;
import io.nop.graphql.core.IDataFetchingEnvironment;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLObjectDefinition;
import io.nop.xlang.xmeta.IObjPropMeta;

import java.util.function.BiFunction;

import static io.nop.graphql.core.GraphQLConfigs.CFG_GRAPHQL_CHECK_DICT_WHEN_INIT;
import static io.nop.graphql.core.GraphQLErrors.ARG_DICT_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_OBJ_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_PROP_NAME;
import static io.nop.graphql.core.GraphQLErrors.ARG_VALUE_PROP_NAME;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_DICT;
import static io.nop.graphql.core.GraphQLErrors.ERR_GRAPHQL_UNKNOWN_DICT_VALUE_PROP;

public class DictLabelFetcherProvider implements IDataFetcherProvider {
    public static DictLabelFetcherProvider INSTANCE = new DictLabelFetcherProvider();

    public boolean provideFetcher(GraphQLObjectDefinition objDef, GraphQLFieldDefinition fieldDef) {
        String name = fieldDef.getName();
        IObjPropMeta propMeta = fieldDef.getPropMeta();
        String dictName = null;
        String dictValueProp = null;
        if (propMeta != null) {
            dictValueProp = (String) propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_DICT_VALUE_PROP);
            if (dictValueProp != null) {
                dictName = (String) propMeta.prop_get(GraphQLConstants.ATTR_GRAPHQL_DICT_NAME);
            }
        }

        if (dictName != null && dictValueProp != null) {
            GraphQLFieldDefinition valueProp = objDef.getField(dictValueProp);
            if (valueProp == null)
                throw new NopException(ERR_GRAPHQL_UNKNOWN_DICT_VALUE_PROP).source(fieldDef)
                        .param(ARG_OBJ_NAME, objDef.getName()).param(ARG_PROP_NAME, name)
                        .param(ARG_VALUE_PROP_NAME, dictValueProp);

            IDataFetcher fetcher = valueProp.getFetcher();
            if (fetcher == null)
                fetcher = BeanPropertyFetcher.INSTANCE;
            if (CFG_GRAPHQL_CHECK_DICT_WHEN_INIT.get() && !DictProvider.instance().existsDict(dictName))
                throw new NopException(ERR_GRAPHQL_UNKNOWN_DICT).source(fieldDef).param(ARG_DICT_NAME, dictName);

            // BeanPropertyFetcher使用的是上下文中的字段名，所以不能使用
            if (fetcher == BeanPropertyFetcher.INSTANCE)
                fetcher = new SpecificPropertyFetcher(valueProp.getName());

            fetcher = new TransformFetcher(fetcher, loadLabel(dictName));

            fieldDef.setFetcher(fetcher);
        }
        return false;
    }

    static BiFunction<Object, IDataFetchingEnvironment, Object> loadLabel(String dictName) {
        return (value, env) -> {
            String locale = env.getExecutionContext().getContext().getLocale();
            DictBean dict = DictProvider.instance().getDict(locale, dictName, env.getCache(), env.getExecutionContext());
            if (dict == null)
                return StringHelper.toString(value, null);
            return dict.getLabelByValue(value);
        };
    }
}