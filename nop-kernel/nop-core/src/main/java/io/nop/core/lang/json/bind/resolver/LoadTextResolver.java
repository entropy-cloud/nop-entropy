/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.bind.resolver;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.core.lang.json.bind.IValueResolver;
import io.nop.core.lang.json.bind.ValueResolverCompileOptions;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;

import static io.nop.core.CoreErrors.ARG_TYPE;
import static io.nop.core.CoreErrors.ARG_VALUE;
import static io.nop.core.CoreErrors.ERR_JSON_BIND_OPTIONS_NOT_VALID_VPATH;

/**
 * 根据资源路径装载文本文件内容，例如 @load:/my.txt
 */
public class LoadTextResolver implements IValueResolver {
    private final String path;
    private final boolean resolveI18n;

    public LoadTextResolver(String path, boolean resolveI18n) {
        this.path = path;
        this.resolveI18n = resolveI18n;
    }

    @Override
    public String resolveValue(IEvalContext ctx) {
        IResource resource = VirtualFileSystem.instance().getResource(path);
        String text = ResourceHelper.readText(resource, null);
        if (resolveI18n) {
            String locale = ContextProvider.currentLocale();
            text = I18nMessageManager.instance().resolveI18nVar(locale, text);
        }
        return text;
    }

    public static IValueResolver compile(SourceLocation loc, Object value, ValueResolverCompileOptions options) {
        String config = ValueResolverCompileHelper.getStringConfig("load", loc, value);
        if (StringHelper.isEmpty(config)) {
            return null;
        }
        if (!StringHelper.isValidVPath(config))
            throw new NopException(ERR_JSON_BIND_OPTIONS_NOT_VALID_VPATH).loc(loc).param(ARG_TYPE, "load")
                    .param(ARG_VALUE, value);

        return new LoadTextResolver(config.trim(), true);
    }
}