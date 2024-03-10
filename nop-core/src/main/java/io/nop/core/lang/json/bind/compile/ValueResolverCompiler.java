/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.IValueResolverCompiler;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;
import io.nop.core.lang.json.bind.resolver.FixedValueResolver;
import io.nop.core.lang.json.bind.resolver.ListValueResolver;
import io.nop.core.lang.json.bind.resolver.MapValueResolver;
import io.nop.core.lang.json.utils.SourceLocationHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.core.CoreErrors.ARG_TYPE;
import static io.nop.core.CoreErrors.ARG_VALUE_PATH;
import static io.nop.core.CoreErrors.ERR_JSON_BIND_EXPR_INVALID_TYPE;

public class ValueResolverCompiler implements IValueResolverCompiler {

    public static final ValueResolverCompiler INSTANCE = new ValueResolverCompiler();

    @Override
    public IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        if (value instanceof IValueResolver)
            return (IValueResolver) value;

        if (value instanceof Map)
            return compileMap(loc, (Map<String, Object>) value, options);
        if (value instanceof Collection)
            return compileList((Collection<Object>) value, options);

        return compileValue(loc, value, options);
    }

    private IValueResolver compileList(Collection<Object> value,
                                       ValueResolverCompileOptions options) {
        List<IValueResolver> list = new ArrayList<>(value.size());
        int index = 0;
        for (Object item : value) {
            SourceLocation itemLoc = SourceLocationHelper.getElementLocation(value, index);
            IValueResolver valueResolver = compile(itemLoc, item, options);
            if (valueResolver == null) {
                if (options.isIgnoreNull())
                    continue;
                valueResolver = FixedValueResolver.NULL_RESOLVER;
            }
            list.add(valueResolver);
            index++;
        }
        return new ListValueResolver(options.isIgnoreNull(), list);
    }

    private IValueResolver compileMap(SourceLocation loc, Map<String, Object> value,
                                      ValueResolverCompileOptions options) {
        String prefix = (String) value.get(PREFIX_KEY);
        if (!StringHelper.isEmpty(prefix)) {
            IValueResolverCompiler compiler = getCompiler(loc, prefix, options);
            if (compiler != null)
                return compiler.compile(loc, value, options);
        }

        Map<String, IValueResolver> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            String name = entry.getKey();
            SourceLocation propLoc = SourceLocationHelper.getPropLocation(value, name);
            IValueResolver valueResolver = compile(propLoc, entry.getValue(), options);
            if (valueResolver == null) {
                if (options.isIgnoreNull())
                    continue;
                valueResolver = FixedValueResolver.NULL_RESOLVER;
            }
            map.put(name, valueResolver);
        }
        return new MapValueResolver(options.isIgnoreNull(), map);
    }

    IValueResolverCompiler getCompiler(SourceLocation loc, String type, ValueResolverCompileOptions options) {
        IValueResolverCompiler compiler = options.getRegistry().getResolverCompiler(type);
        if (compiler == null) {
            if (!options.isIgnoreUnknown()) {
                throw new NopException(ERR_JSON_BIND_EXPR_INVALID_TYPE).loc(loc).param(ARG_TYPE, type)
                        .param(ARG_VALUE_PATH, options.getJsonPathString());
            }
        }
        return compiler;
    }

    private IValueResolver compileValue(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        if (value == null)
            return null;

        if (value instanceof String) {
            String text = value.toString();
            if (text.length() <= 1)
                return FixedValueResolver.valueOf(text);

            if (text.charAt(0) == BIND_EXPR_SYMBOL) {
                if (text.charAt(1) == BIND_EXPR_SYMBOL)
                    return FixedValueResolver.valueOf(text.substring(1));
                String type = text;
                String config = "";
                int pos = text.indexOf(':');
                if (pos >= 0) {
                    config = text.substring(pos + 1).trim();
                    type = text.substring(1, pos);
                }
                IValueResolverCompiler compiler = getCompiler(loc, type, options);
                if (compiler == null) {
                    return FixedValueResolver.valueOf(text);
                }
                return compiler.compile(loc, config, options);
            }
        }
        return FixedValueResolver.valueOf(value);
    }
}
