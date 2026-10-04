/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.enums;

import io.nop.commons.type.StdSqlType;

public enum SqlOperator {
    BIT_XOR("^", 50, true),

    MULTIPLY("*", 60), DIVIDE("/", 60), MOD("%", 60),

    ADD("+", 70), MINUS("-", 70),

    BIT_LEFT_SHIFT("<<", 80, true), BIT_RIGHT_SHIFT(">>", 80, true), BIT_AND("&", 90, true), BIT_OR("|", 100, true),

    IS("is", 110), LT("<", 110), LE("<=", 110), EQ("=", 110), NE("<>", 110), GT(">", 110), GE(">=", 110),

    // WI8（plan 27；裁定 ai-dev/design/eql-cross-db-compat/2026-10-04-wi8-phase0-adjudications.md §六.1）：
    // null-safe 相等（MySQL `<=>`，承载 IS NOT DISTINCT FROM 的规范 AST 形态）。手写枚举零生成。
    // 优先级 110 与比较算子同级；isCompareOp() 纳入（全仓唯一生产消费点 SqlParamTypeResolver
    // 的参数类型回退解析与 EQ 同路）；reverse() 自反（a<=>b ⇔ b<=>a，不得落 null）；
    // getLeftSqlType/getRightSqlType/getResultSqlType 与 EQ 一致走默认分支落 OTHER（类型域不约束）。
    SAFE_EQ("<=>", 110),

    LIKE("like", 110), RLIKE("rlike", 110), ILIKE("ilike", 110),

    BIT_NOT("~", 130, true),

    AND("and", 140), OR("or", 160), NOT("not", 160);

    private String text;
    private int priority;
    private boolean bitOp;

    SqlOperator(String text, int priority) {
        this.text = text;
        this.priority = priority;
    }

    SqlOperator(String text, int priority, boolean bitOp) {
        this(text, priority);
        this.bitOp = bitOp;
    }

    public int getPriority() {
        return priority;
    }

    public String toString() {
        return text;
    }

    public boolean isCompareOp() {
        return this == LE || this == LT || this == GT || this == GE || this == EQ || this == NE || this == SAFE_EQ;
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

    public boolean isStringOp() {
        return this == LIKE || this == RLIKE;
    }

    public boolean isBooleanOp() {
        return this == AND || this == OR || this == NOT;
    }

    public String getText() {
        return text;
    }

    public StdSqlType getLeftSqlType() {
        if (isBitOp())
            return StdSqlType.BIGINT;
        if (isStringOp())
            return StdSqlType.VARCHAR;
        if (isBooleanOp())
            return StdSqlType.BOOLEAN;

        if (isAdditiveOp() || isMultiplicativeOp())
            return StdSqlType.DECIMAL;
        return StdSqlType.OTHER;
    }

    public StdSqlType getRightSqlType() {
        if (isBitOp())
            return StdSqlType.BIGINT;
        if (isStringOp())
            return StdSqlType.VARCHAR;
        if (isBooleanOp())
            return StdSqlType.BOOLEAN;
        if (this == MOD)
            return StdSqlType.INTEGER;
        if (isAdditiveOp() || isMultiplicativeOp())
            return StdSqlType.DECIMAL;
        return StdSqlType.OTHER;
    }

    public StdSqlType getResultSqlType() {
        if (isBitOp())
            return StdSqlType.BIGINT;
        if (isStringOp())
            return StdSqlType.VARCHAR;
        if (isBooleanOp())
            return StdSqlType.BOOLEAN;

        if (isAdditiveOp() || isMultiplicativeOp())
            return StdSqlType.DECIMAL;
        return StdSqlType.OTHER;
    }

    public SqlOperator reverse() {
        switch (this) {
            case EQ:
                return SqlOperator.EQ;
            case NE:
                return SqlOperator.NE;
            case GT:
                return SqlOperator.LT;
            case GE:
                return SqlOperator.LE;
            case LT:
                return SqlOperator.GT;
            case LE:
                return SqlOperator.GE;
            case SAFE_EQ:
                // null-safe 相等自反：a<=>b ⇔ b<=>a。不得静默落 default → null
                return SqlOperator.SAFE_EQ;
            default:
                return null;
        }
    }
}
