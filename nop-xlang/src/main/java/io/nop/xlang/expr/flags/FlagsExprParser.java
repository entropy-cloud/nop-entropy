package io.nop.xlang.expr.flags;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.predicate.NotPredicate;
import io.nop.commons.functional.predicate.PredicateHelper;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.expr.simple.AbstractTokenExprParser;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class FlagsExprParser extends AbstractTokenExprParser<Predicate<Set<String>>> {

    @Override
    protected Predicate<Set<String>> newLogicExpr(SourceLocation loc, XLangOperator op,
                                                  List<Predicate<Set<String>>> exprs) {
        if (exprs.size() == 1)
            return exprs.get(0);

        if (op == XLangOperator.AND) {
            return PredicateHelper.buildAndPredicate(exprs);
        } else {
            return PredicateHelper.buildOrPredicate(exprs);
        }
    }

    protected Predicate<Set<String>> tokenExpr(TextScanner sc) {
        String name = sc.nextConfigVar();
        sc.skipBlank();
        // 作为表达式返回，因此不能为null
        return new MatchFlagPredicate(name);
    }

    protected Predicate<Set<String>> newUnaryExpr(SourceLocation loc, XLangOperator op, Predicate<Set<String>> x) {
        return new NotPredicate<>(x);
    }
}
