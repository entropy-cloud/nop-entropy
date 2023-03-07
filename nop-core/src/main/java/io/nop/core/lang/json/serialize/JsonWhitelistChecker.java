/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.serialize;

import io.nop.core.lang.json.JsonWhitelist;
import io.nop.core.reflect.bean.IBeanModel;

public class JsonWhitelistChecker {
    private static JsonWhitelistChecker _instance = new JsonWhitelistChecker();

    public static void registerInstance(JsonWhitelistChecker checker) {
        _instance = checker;
    }

    public static JsonWhitelistChecker instance() {
        return _instance;
    }

    public boolean isAllowSerialize(boolean onlyDataBean, Object bean, IBeanModel beanModel) {
        if (!onlyDataBean)
            return true;
        return beanModel.isDataBean() && !JsonWhitelist.DEFAULTS.contains(beanModel.getClassName());
    }
}