/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.initialize.impl;

import io.nop.api.core.json.JSON;
import io.nop.commons.collections.ListFunctions;
import io.nop.commons.lang.impl.Cancellable;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.ICoreInitializer;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.reflect.ReflectionManager;

import java.time.LocalDate;
import java.util.List;

/**
 * 向反射系统注册帮助函数
 */
public class ReflectionHelperMethodInitializer implements ICoreInitializer {
    private Cancellable cancellable = new Cancellable();

    @Override
    public int order() {
        return CoreConstants.INITIALIZER_PRIORITY_REGISTER_REFLECTION;
    }

    @Override
    public void initialize() {
        if (JSON.getProvider() == null)
            JSON.registerProvider(JsonTool.instance());

        cancellable.append(ReflectionManager.instance().registerHelperMethods(List.class, ListFunctions.class, null));

        cancellable.append(ReflectionManager.instance().registerHelperMethods(String.class, StringHelper.class, "$"));

        cancellable.append(ReflectionManager.instance().registerHelperMethods(LocalDate.class, DateHelper.class, "$"));
    }

    @Override
    public void destroy() {
        cancellable.cancel();
    }
}