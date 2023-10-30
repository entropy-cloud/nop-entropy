/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._Literal;

public class Literal extends _Literal implements MetaValue {

    public String getStringValue() {
        return ConvertHelper.toString(getValue());
    }

    public static Literal valueOf(SourceLocation loc, Object value) {
        Literal node = new Literal();
        node.setLocation(loc);
        node.setValue(value);
        return node;
    }

    public static Literal booleanValue(SourceLocation loc, boolean b) {
        Literal ret = new Literal();
        ret.setLocation(loc);
        ret.setValue(b);
        return ret;
    }

    public static Literal stringValue(SourceLocation loc, String str) {
        Literal ret = new Literal();
        ret.setLocation(loc);
        ret.setValue(str);
        return ret;
    }

    public static Literal nullValue(SourceLocation loc) {
        Literal ret = new Literal();
        ret.setLocation(loc);
        return ret;
    }

    public static Literal numberValue(SourceLocation loc, Number num) {
        Literal ret = new Literal();
        ret.setLocation(loc);
        ret.setValue(num);
        return ret;
    }

    public static boolean isStringLiteral(Expression expr) {
        if (expr instanceof Literal)
            return ((Literal) expr).getValue() instanceof String;
        return false;
    }
}