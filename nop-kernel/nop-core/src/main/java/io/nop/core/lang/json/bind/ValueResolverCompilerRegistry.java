/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind;

import io.nop.core.lang.json.bind.resolver.ConfigValueResolver;
import io.nop.core.lang.json.bind.resolver.EmptyTextResolver;
import io.nop.core.lang.json.bind.resolver.I18nTextResolver;
import io.nop.core.lang.json.bind.resolver.LoadTextResolver;
import io.nop.core.lang.json.bind.resolver.ScopeVarResolver;
import io.nop.core.lang.json.bind.resolver.UuidResolver;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValueResolverCompilerRegistry {
    private final Map<String, IValueResolverCompiler> resolvers = new ConcurrentHashMap<>();

    public static ValueResolverCompilerRegistry DEFAULT = new ValueResolverCompilerRegistry();

    static {
        DEFAULT.addResolverCompiler("uuid", UuidResolver::compile);
        DEFAULT.addResolverCompiler("var", ScopeVarResolver::compile);
        DEFAULT.addResolverCompiler("i18n", I18nTextResolver::compile);
        DEFAULT.addResolverCompiler("cfg", ConfigValueResolver::compile);
        DEFAULT.addResolverCompiler("load", LoadTextResolver::compile);
        DEFAULT.addResolverCompiler("empty", EmptyTextResolver::compile);
    }

    public ValueResolverCompilerRegistry copy() {
        ValueResolverCompilerRegistry registry = new ValueResolverCompilerRegistry();
        registry.resolvers.putAll(resolvers);
        return registry;
    }

    public IValueResolverCompiler getResolverCompiler(String type) {
        return resolvers.get(type);
    }

    public void addResolverCompiler(String type, IValueResolverCompiler resolver) {
        resolvers.put(type, resolver);
    }

    public void removeResolverCompiler(String type, IValueResolverCompiler resolver) {
        resolvers.remove(type, resolver);
    }

    public void removeResolverCompiler(String type) {
        resolvers.remove(type);
    }
}