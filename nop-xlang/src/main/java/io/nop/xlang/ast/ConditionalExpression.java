/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
// package io.nop.xlang.ast;
//
// import io.nop.api.core.util.Guard;
// import io.nop.api.core.util.SourceLocation;
// import io.nop.xlang.ast._gen._ConditionalExpression;
//
// import java.util.List;
//
// import static io.nop.xlang.ast.XLangASTBuilder.initConditional;
//
// public class ConditionalExpression extends _ConditionalExpression implements IConditionalExpression {
// @Override
// public IConditionalExpression createInstance() {
// ConditionalExpression expr = new ConditionalExpression();
// return expr;
// }
//
// public static ConditionalExpression valueOf(SourceLocation loc, Expression test, Expression consequent,
// Expression alternate) {
// Guard.notNull(test, "test is null");
// Guard.notNull(consequent, "consequent is null");
//
// ConditionalExpression node = new ConditionalExpression();
// node.setLocation(loc);
// node.setTest(test);
// node.setConsequent(consequent);
// node.setAlternate(alternate);
// return node;
// }
//
// public static ConditionalExpression valueOf(SourceLocation loc, Expression test, List<Expression> exprs) {
// ConditionalExpression node = new ConditionalExpression();
// node.setLocation(loc);
// node.setTest(test);
// initConditional(node, exprs, 0);
// return node;
// }
//
// public static ConditionalExpression valueOf(SourceLocation loc, List<Expression> exprs) {
// ConditionalExpression node = new ConditionalExpression();
// node.setLocation(loc);
// node.setTest(exprs.get(0));
// initConditional(node, exprs, 1);
// return node;
// }
// }