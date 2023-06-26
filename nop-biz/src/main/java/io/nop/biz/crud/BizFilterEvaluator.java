package io.nop.biz.crud;

import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.util.IVariableScope;
import io.nop.biz.BizConstants;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.model.query.BeanVariableScope;
import io.nop.core.model.query.FilterBeanEvaluator;
import io.nop.xlang.api.AbstractEvalAction;
import io.nop.xlang.api.XLang;

public class BizFilterEvaluator extends FilterBeanEvaluator {
    private final String libPath;
    private final IServiceContext context;

    public BizFilterEvaluator(String libPath, IServiceContext context) {
        this.libPath = libPath;
        this.context = context;
    }

    public BizFilterEvaluator(IServiceContext context) {
        this(BizConstants.XLIB_BIZ_CHECK, context);
    }

    public boolean testForEntity(ITreeBean filter, Object entity) {
        BizExprHelper.resolveBizExpr(filter, context);
        return Boolean.TRUE.equals(visitRoot(filter, new BeanVariableScope(entity)));
    }

    @Override
    public Boolean visitUnknown(String op, ITreeBean filter, IVariableScope scope) {
        String tagName = filter.getTagName();
        if (!StringHelper.startsWithNamespace(tagName, BizConstants.XLIB_NS_BIZ))
            return super.visitUnknown(op, filter, scope);

        // biz标签库中的标签
        AbstractEvalAction tag = XLang.getTagAction(libPath, tagName.substring(BizConstants.XLIB_NS_BIZ.length()));
        return tag.passConditions(context);
    }
}
