/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.template;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 获取项目内所有可访问的 xdef 资源路径
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-06
 */
public class XLangSchemaPathMacro extends Macro {

    @Override
    public @NonNls String getName() {
        return "schemaPath";
    }

    @Override
    public @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
        return null;
    }

    @Override
    public LookupElement @Nullable [] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
        return ProjectFileHelper.getCachedNopXDefVfsPaths(context.getProject())
                                .stream()
                                .map(LookupElementHelper::lookupString)
                                .toArray(LookupElement[]::new);
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof XLangFileLiveTemplateContextType;
    }
}
