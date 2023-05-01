/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.initialize;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.report.core.expr.ReportExpressionParser;
import io.nop.report.core.functions.ReportFunctionProvider;
import io.nop.xlang.api.IFunctionProvider;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.xdef.IStdDomainOptions;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.domain.XplStdDomainHandlers;

public class TemplateReportExprStdDomainHandler extends XplStdDomainHandlers.AbstractExprType {
    public static TemplateReportExprStdDomainHandler INSTANCE = new TemplateReportExprStdDomainHandler();

    @Override
    public String getName() {
        return XDefConstants.STD_DOMAIN_T_REPORT_EXPR;
    }

    @Override
    public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
        String source = (String) text;

        Expression expr = new ReportExpressionParser().parseTemplateExpr(loc, source, false, ExprPhase.eval);

        XLangOutputMode oldMode = cp.getOutputMode();
        cp.outputMode(XLangOutputMode.none);
        IFunctionProvider provider = cp.getScope().getFunctionProvider();
        try {
            cp.getScope().setFunctionProvider(ReportFunctionProvider.INSTANCE);
            return cp.buildEvalAction(expr);
        } catch (Exception e) {
            throw newPropError(loc, propName, source).cause(e);
        } finally {
            cp.getScope().setFunctionProvider(provider);
            cp.outputMode(oldMode);
        }
    }

    @Override
    public Object parseXmlChild(IStdDomainOptions options, XNode body, XLangCompileTool cp) {
        return parseProp(options, body.content().getLocation(), "body", body.contentText(), cp);
    }
}