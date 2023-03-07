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
import io.nop.report.core.XptConstants;
import io.nop.report.core.expr.ReportExpressionParser;
import io.nop.report.core.functions.ReportFunctionProvider;
import io.nop.xlang.api.IFunctionProvider;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.xdef.IStdDomainOptions;
import io.nop.xlang.xdef.domain.XplStdDomainHandlers;

public class ReportExprStdDomainHandler extends XplStdDomainHandlers.AbstractExprType {
    public static ReportExprStdDomainHandler INSTANCE = new ReportExprStdDomainHandler();

    @Override
    public String getName() {
        return XptConstants.STD_DOMAIN_REPORT_EXPR;
    }

    @Override
    public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text, XLangCompileTool cp) {
        String source = (String) text;

        Expression expr = new ReportExpressionParser().parseExpr(loc, source);

        XLangOutputMode oldMode = cp.getOutputMode();
        cp.outputMode(XLangOutputMode.none);
        IFunctionProvider provider = cp.getScope().getFunctionProvider();
        try {
            cp.getScope().setFunctionProvider(ReportFunctionProvider.INSTANCE);
            return cp.buildActionForExpr(expr);
        } catch (Exception e) {
            throw newPropError(loc, propName, source);
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