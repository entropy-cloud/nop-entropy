/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.expr;

public class ExprFeatures {
    public static final int LAMBDA_FUNCTION = 0x1;
    public static final int FUNCTION_DEF = 0x2;
    public static final int STATEMENT = 0x1 << 3;
    public static final int FUNCTION_CALL = 0x1 << 4;
    public static final int OBJECT_CALL = 0x1 << 5;
    public static final int BIT_OP = 0x1 << 6;
    public static final int SELF_ASSIGN = 0x1 << 7;
    public static final int CP_EXPR = 0x1 << 8;
    public static final int TAG_FUNC = 0x1 << 9;
    public static final int JSON = 0x1 << 10;
    public static final int OBJECT_PROP = 0x1 << 11;
    public static final int ARRAY_INDEX = 0x1 << 12;
    public static final int SELF_INC = 0x1 << 13;
    public static final int IMPORT = 0x1 << 14;
    public static final int NEW = 0x1 << 15;

    public static final int ALL = LAMBDA_FUNCTION | FUNCTION_DEF | STATEMENT | FUNCTION_CALL | OBJECT_CALL | BIT_OP
            | SELF_ASSIGN | CP_EXPR | TAG_FUNC | JSON | OBJECT_PROP | ARRAY_INDEX | SELF_INC | IMPORT | NEW;

    public static final int SIMPLE = FUNCTION_CALL | OBJECT_CALL | BIT_OP | JSON | OBJECT_PROP | ARRAY_INDEX;
}
