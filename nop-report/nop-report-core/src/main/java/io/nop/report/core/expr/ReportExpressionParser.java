/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.report.core.expr;

import io.nop.xlang.expr.ExprFeatures;

/**
 * 在XLang表达式的基础上增加CellCoordinate语法的支持。可以支持简单的Excel公式形式，例如 SUM(A3:A5) + C1
 */
public class ReportExpressionParser extends AbstractExcelFormulaParser {
    public ReportExpressionParser() {
        setUseEvalException(true);
        enableFeatures(ExprFeatures.ALL);
    }

}