/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.delta;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.xdef.IXDefNode;
import io.nop.xlang.xdef.XDefOverride;
import io.nop.xlang.xdsl.XDslKeys;

import static io.nop.xlang.XLangErrors.ARG_OVERRIDE;
import static io.nop.xlang.XLangErrors.ERR_XDSL_INVALID_OVERRIDE_ATTR;
import static io.nop.xlang.xdef.XDefOverride.APPEND;
import static io.nop.xlang.xdef.XDefOverride.BOUNDED_MERGE;
import static io.nop.xlang.xdef.XDefOverride.MERGE;
import static io.nop.xlang.xdef.XDefOverride.MERGE_REPLACE;
import static io.nop.xlang.xdef.XDefOverride.MERGE_SUPER;
import static io.nop.xlang.xdef.XDefOverride.PREPEND;
import static io.nop.xlang.xdef.XDefOverride.REMOVE;
import static io.nop.xlang.xdef.XDefOverride.REPLACE;

public class OverrideHelper {
    private static final XDefOverride[][] s_mapping = new XDefOverride[count()][count()];

    static {
        d(null, REMOVE, REMOVE);
        d(null, REPLACE, REPLACE);

        // 结果为null表示不允许执行这种合并

        // APPEND
        d(REMOVE, APPEND, REPLACE);
        d(REPLACE, APPEND, REPLACE);
        d(APPEND, APPEND, APPEND);
        d(PREPEND, APPEND, MERGE_SUPER);
        d(MERGE, APPEND, REPLACE); // 存在问题
        d(MERGE_SUPER, APPEND, MERGE_SUPER);
        d(MERGE_REPLACE, APPEND, MERGE_REPLACE);
        d(BOUNDED_MERGE, APPEND, REPLACE); // 存在问题

        // PREPEND
        d(REMOVE, PREPEND, REPLACE);
        d(REPLACE, PREPEND, REPLACE);
        d(APPEND, PREPEND, MERGE_SUPER);
        d(PREPEND, PREPEND, PREPEND);
        d(MERGE, PREPEND, REPLACE); // 存在问题
        d(MERGE_SUPER, PREPEND, MERGE_SUPER);
        d(MERGE_REPLACE, PREPEND, MERGE_REPLACE);
        d(BOUNDED_MERGE, PREPEND, REPLACE); // 存在问题

        // MERGE
        d(REMOVE, MERGE, REPLACE);
        d(REPLACE, MERGE, REPLACE);
        d(APPEND, MERGE, null);
        d(PREPEND, MERGE, null);
        d(MERGE, MERGE, MERGE); // 结果为null表示不允许执行这种合并
        d(MERGE_SUPER, MERGE, null);
        d(MERGE_REPLACE, MERGE, MERGE_REPLACE);
        d(BOUNDED_MERGE, MERGE, BOUNDED_MERGE);

        // MERGE_SUPER
        d(REMOVE, MERGE_SUPER, REPLACE);
        d(REPLACE, MERGE_SUPER, REPLACE);
        d(APPEND, MERGE_SUPER, MERGE_SUPER);
        d(PREPEND, MERGE_SUPER, MERGE_SUPER);
        d(MERGE, MERGE_SUPER, null); // 结果为null表示不允许执行这种合并
        d(MERGE_SUPER, MERGE_SUPER, MERGE_SUPER);
        d(MERGE_REPLACE, MERGE_SUPER, MERGE_REPLACE);
        d(BOUNDED_MERGE, MERGE_SUPER, null);

        // MERGE_REPLACE
        d(REMOVE, MERGE_REPLACE, REPLACE);
        d(REPLACE, MERGE_REPLACE, REPLACE);
        d(APPEND, MERGE_REPLACE, MERGE_REPLACE);
        d(PREPEND, MERGE_REPLACE, MERGE_REPLACE);
        d(MERGE, MERGE_REPLACE, MERGE_REPLACE); // 结果为null表示不允许执行这种合并
        d(MERGE_SUPER, MERGE_REPLACE, MERGE_REPLACE);
        d(MERGE_REPLACE, MERGE_REPLACE, MERGE_REPLACE);
        d(BOUNDED_MERGE, MERGE_REPLACE, MERGE_REPLACE);

        // BOUNDED_MERGE
        d(REMOVE, BOUNDED_MERGE, REPLACE);
        d(REPLACE, BOUNDED_MERGE, REPLACE);
        d(APPEND, BOUNDED_MERGE, null);
        d(PREPEND, BOUNDED_MERGE, null);
        d(MERGE, BOUNDED_MERGE, BOUNDED_MERGE); // 结果为null表示不允许执行这种合并
        d(MERGE_SUPER, BOUNDED_MERGE, null);
        d(MERGE_REPLACE, BOUNDED_MERGE, BOUNDED_MERGE);
        d(BOUNDED_MERGE, BOUNDED_MERGE, BOUNDED_MERGE);
    }

    private static int count() {
        return XDefOverride.values().length;
    }

    // 定义override操作之后合并节点上的override属性
    static void d(XDefOverride overrideA, XDefOverride overrideB, XDefOverride result) {
        // overrideA为空表示对所有overrideA都适用
        if (overrideA == null) {
            for (int i = 0; i < count(); i++) {
                d(i, overrideB.ordinal(), result);
            }
        } else if (overrideB == null) {
            for (int i = 0; i < count(); i++) {
                d(overrideA.ordinal(), i, result);
            }
        } else {
            d(overrideA.ordinal(), overrideB.ordinal(), result);
        }
    }

    static void d(int a, int b, XDefOverride result) {
        Guard.checkArgument(s_mapping[a][b] == null, "result is already defined");
        s_mapping[a][b] = result;
    }

    /**
     * 两次相邻的override操作满足结合律
     *
     * @return 先后应用两次override，等价于按照结果进行一次override
     */
    public static XDefOverride mergedOverride(XDefOverride overrideA, XDefOverride overrideB) {
        return s_mapping[overrideA.ordinal()][overrideB.ordinal()];
    }

    public static XDefOverride getOverride(XNode node, String key) {
        String str = node.attrText(key);
        if (!StringHelper.isEmpty(str)) {
            XDefOverride override = XDefOverride.fromText(str);
            if (override == null)
                throw new NopException(ERR_XDSL_INVALID_OVERRIDE_ATTR).param(ARG_OVERRIDE, str);
            return override;
        }
        return null;
    }

    public static XDefOverride getOverride(XNode node, XDslKeys keys, boolean forPrototype, IXDefNode defNode,
                                           XDefOverride defaultOverride) {
        XDefOverride override = getOverride(node, forPrototype ? keys.PROTOTYPE_OVERRIDE : keys.OVERRIDE);
        if (override == null) {
            if (defNode != null) {
                override = defNode.getXdefDefaultOverride();
            }
        }
        if (override == null)
            override = defaultOverride;
        return override;
    }
    //
    // public static String getKeyAttr(XNode xa, XNode xb, IXDefNode defNode) {
    // if (defNode != null)
    // return defNode.getKeyAttr();
    //
    // String keyAttr = xb.attrText(XDslConstants.KEY_V_ID);
    // if (!StringHelper.isEmpty(keyAttr)) {
    // return XDslConstants.KEY_V_ID + ':' + keyAttr;
    // }
    //
    // keyAttr = xa.attrText(XDslConstants.KEY_ID);
    // if (!StringHelper.isEmpty(keyAttr))
    // return XDslConstants.KEY_ID + ':' + keyAttr;
    //
    // keyAttr = xa.attrText(XDslConstants.KEY_NAME);
    // if (!StringHelper.isEmpty(keyAttr))
    // return XDslConstants.KEY_NAME + ':' + keyAttr;
    //
    // return null;
    // }
}
