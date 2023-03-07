/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.benchmark.reflection;

import io.nop.benchmark.model.Stock;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.impl.MethodInvokers;

public class ReflectionInitialization {

    public static void initInvokers() {
        MethodInvokers inv = new MethodInvokers();
        inv.call0(false, "getPrice", (thisObj, context) -> {
            return ((Stock) thisObj).getPrice();
        });
        inv.call0(false, "getUrl", (thisObj, context) -> {
            return ((Stock) thisObj).getUrl();
        });
        inv.call0(false, "getRatio", (thisObj, context) -> {
            return ((Stock) thisObj).getRatio();
        });
        inv.call0(false, "getName", (thisObj, context) -> {
            return ((Stock) thisObj).getName();
        });
        inv.call0(false, "getChange", (thisObj, context) -> {
            return ((Stock) thisObj).getChange();
        });

        ReflectionManager.instance().registerInvokers(Stock.class, inv);
    }
}
