/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.api;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.sql.ISqlGenerator;
import io.nop.core.lang.sql.SQL;
import io.nop.core.lang.xml.IXNodeGenerator;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.xlang.utils.ExprEvalHelper;

import java.io.Writer;

public abstract class AbstractEvalAction
        implements IEvalAction, IEvalPredicate, ITextTemplateOutput, ISqlGenerator, IXNodeGenerator {

    @Override
    public boolean passConditions(IEvalContext ctx) {
        return ConvertHelper.toTruthy(invoke(ctx));
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) {
        ExprEvalHelper.generateToWriter(this::invoke, out, context);
    }

    public Object generateXjson(IEvalContext context) {
        return ExprEvalHelper.generateXjson(this::invoke, context);
    }

    @Override
    public SQL generateSql(IEvalContext context) {
        return ExprEvalHelper.generateSql(this::invoke, context);
    }

    @Override
    public XNode generateNode(IEvalContext context) {
        return ExprEvalHelper.generateNode(this::invoke, context);
    }
}
