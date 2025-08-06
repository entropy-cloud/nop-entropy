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
import com.intellij.openapi.project.Project;
import io.nop.idea.plugin.utils.LookupElementHelper;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
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
        // Note: TODO 全项目搜索的性能极差，暂时不启用补全
//        Project project = context.getProject();
//
//        return ProjectFileHelper.findAllNopVfsPaths(project)
//                                .stream()
//                                .sorted()
//                                .filter(path -> path.endsWith(".xdef"))
//                                .map(LookupElementHelper::lookupString)
//                                .toArray(LookupElement[]::new);
        return LookupElement.EMPTY_ARRAY;
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof XLangFileLiveTemplateContextType;
    }
}
