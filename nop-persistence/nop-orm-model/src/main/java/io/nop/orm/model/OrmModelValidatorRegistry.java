/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ORM 模型扩展校验器的可配置注册表。模型内核初始化（{@link OrmModel#init()}）末尾统一驱动
 * 已注册的 {@link IOrmModelValidator}；注册表为空时零行为变更。
 *
 * <p>发现途径：META-INF/services（ServiceLoader 懒加载，跨模块自动发现）+ {@link #register} 显式注册
 * （对齐 {@code EvalExprProvider.registerDefaultExprParser} 的静态注册表先例）。
 * 校验器抛出的 {@link NopException} 原样向外传播（启动即失败），注册表不做任何静默消化。
 */
public class OrmModelValidatorRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(OrmModelValidatorRegistry.class);

    private static final CopyOnWriteArrayList<IOrmModelValidator> VALIDATORS = new CopyOnWriteArrayList<>();

    private static volatile boolean serviceLoaderLoaded;

    public static List<IOrmModelValidator> getValidators() {
        loadServiceLoaders();
        return VALIDATORS;
    }

    public static void register(IOrmModelValidator validator) {
        if (validator == null)
            return;
        loadServiceLoaders();
        VALIDATORS.addIfAbsent(validator);
    }

    public static void unregister(IOrmModelValidator validator) {
        VALIDATORS.remove(validator);
    }

    /**
     * 模型内核初始化完成后调用。校验器异常原样传播，不在此处捕获。
     */
    public static void validate(OrmModel model) {
        for (IOrmModelValidator validator : getValidators()) {
            validator.validate(model);
        }
    }

    private static void loadServiceLoaders() {
        if (serviceLoaderLoaded)
            return;
        synchronized (OrmModelValidatorRegistry.class) {
            if (serviceLoaderLoaded)
                return;
            for (IOrmModelValidator validator : ServiceLoader.load(IOrmModelValidator.class)) {
                LOG.info("nop.orm.model.register-validator:{}", validator.getClass().getName());
                VALIDATORS.addIfAbsent(validator);
            }
            serviceLoaderLoaded = true;
        }
    }
}
