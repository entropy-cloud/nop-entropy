/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

public class XLangASTHelper {
    public static boolean allowMandatoryChain(Expression x) {
        switch (x.getASTKind()) {
            case Identifier:
            case MemberExpression:
            case CallExpression:
            case BraceExpression:
                return true;
        }
        return false;
    }

    /**
     * 是否允许作为MemberExpression的obj部分
     */
    public static boolean allowMember(Expression x) {
        switch (x.getASTKind()) {
            case Identifier:
            case Literal:
            case MemberExpression:
            case CallExpression:
            case ChainExpression:
            case BraceExpression:
            case CustomExpression:
                return true;
            default:
        }
        return false;
    }

    public static boolean allowCall(Expression x) {
        switch (x.getASTKind()) {
            case Identifier:
            case MemberExpression:
            case CallExpression:
            case ChainExpression:
                return true;
            default:
        }
        return false;
    }
}