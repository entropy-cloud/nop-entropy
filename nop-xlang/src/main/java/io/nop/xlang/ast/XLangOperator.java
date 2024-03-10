/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.commons.text.tokenizer.IToken;

public enum XLangOperator implements IToken {
    ASSIGN("="),

    ADD("+"), MINUS("-"), DIVIDE("/"), MULTIPLY("*"), MOD("%"), LT("<"), LE("<="), EQ("=="), NE("!="), GT(">"),
    GE(">="), AND("&&"), OR("||"), NOT("!"),

    IN("in"),

    BIT_AND("&", true, false), BIT_OR("|", true, false), BIT_XOR("^", true, false), BIT_NOT("~", true, false),
    BIT_LEFT_SHIFT("<<", true, false), BIT_RIGHT_SHIFT(">>", true, false), BIT_UNSIGNED_RIGHT_SHIFT(">>>", true, false),

    QUESTION("?"), COLON(":"), NULL_COALESCE("??"),

    ARROW("=>"),

    SELF_INC("++"), SELF_DEC("--"),

    SELF_ASSIGN_ADD("+=", false, true), SELF_ASSIGN_MINUS("-=", false, true), SELF_ASSIGN_DIV("/=", false, true),
    SELF_ASSIGN_MULTI("*=", false, true),

    SELF_ASSIGN_BIT_AND("&=", false, true), SELF_ASSIGN_BIT_OR("|=", false, true),
    SELF_ASSIGN_BIT_NOT("~=", false, true), SELF_ASSIGN_BIT_XOR("^=", false, true), SELF_ASSIGN_MOD("%=", false, true),
    SELF_ASSIGN_LEFT_SHIFT("<<=", false, true), SELF_ASSIGN_RIGHT_SHIFT(">>=", false, true),
    SELF_ASSIGN_UNSIGNED_RIGHT_SHIFT(">>>=", false, true),

    SELF_ASSIGN_OR("||=", false, true), SELF_ASSIGN_AND("&&=", false, true),
    SELF_ASSIGN_NULL_COALESCE("??=", false, true);

    private String text;
    private boolean bitOp;
    private boolean selfAssign;

    XLangOperator(String text) {
        this.text = text;
    }

    XLangOperator(String text, boolean bitOp, boolean selfAssign) {
        this(text);
        this.bitOp = bitOp;
        this.selfAssign = selfAssign;
    }

    public boolean isOperator() {
        return true;
    }

    public String toString() {
        return text;
    }

    public boolean isCompareOp() {
        return this == LE || this == LT || this == GT || this == GE;
    }

    public boolean isEqualityOp() {
        return this == EQ || this == NE;
    }

    public boolean isBitOp() {
        return bitOp;
    }

    public boolean isAdditiveOp() {
        return this == ADD || this == MINUS;
    }

    public boolean isMultiplicativeOp() {
        return this == MULTIPLY || this == DIVIDE || this == MOD;
    }

    public boolean isSelfAssign() {
        return selfAssign;
    }

    public String getText() {
        return text;
    }

}