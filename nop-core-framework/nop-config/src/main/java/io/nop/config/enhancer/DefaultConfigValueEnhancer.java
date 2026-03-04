/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.enhancer;

import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.IConfigValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.StaticValue;
import io.nop.commons.crypto.ITextCipher;
import io.nop.config.expr.ConfigExpressionResolver;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.query.SwitchValue;

import static io.nop.config.ConfigConstants.CFG_SEC_PREFIX;
import static io.nop.config.ConfigConstants.CFG_SWITCH_PREFIX;

/**
 * 默认的配置值增强器实现
 * <p>
 * 支持以下增强功能：
 * <ul>
 *   <li>{@code @switch:} 前缀 - 条件开关值</li>
 *   <li>{@code @sec:} 前缀 - 加密值解密</li>
 *   <li>{@code ${...}} 表达式 - 配置变量引用（Spring 风格）</li>
 * </ul>
 */
public class DefaultConfigValueEnhancer implements IConfigValueEnhancer {

    private final ITextCipher cipher;

    public DefaultConfigValueEnhancer(ITextCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public <T> IConfigValue<T> enhance(Object value, Class<T> targetClass) {
        // 旧接口调用，不支持表达式解析
        return doEnhance(value, targetClass, null);
    }

    @Override
    public <T> IConfigValue<T> enhance(Object value, Class<T> targetClass,
                                        IConfigProvider configProvider) {
        return doEnhance(value, targetClass, configProvider);
    }

    private <T> IConfigValue<T> doEnhance(Object value, Class<T> targetClass,
                                           IConfigProvider configProvider) {
        if (value instanceof String) {
            String str = (String) value;

            // 1. 处理 @switch: 前缀（条件开关值）
            if (str.startsWith(CFG_SWITCH_PREFIX)) {
                SwitchValue switchValue = buildSwitchValue(
                        str.substring(CFG_SWITCH_PREFIX.length()));
                switchValue.normalizeValue(targetClass);
                return (IConfigValue<T>) switchValue;
            }

            // 2. 处理 @sec: 前缀（加密值）
            if (str.startsWith(CFG_SEC_PREFIX)) {
                value = cipher.decrypt(str.substring(CFG_SEC_PREFIX.length()));
            } else if (configProvider != null && str.contains("${")) {
                // 3. 处理 ${...} 表达式（需要 configProvider）
                ConfigExpressionResolver resolver = new ConfigExpressionResolver(
                        configProvider, false);
                value = resolver.resolve(str);
            }
        }

        // 类型转换
        if (targetClass != null && targetClass != Object.class) {
            value = ConvertHelper.convertTo(targetClass, value, NopException::new);
        }

        return StaticValue.valueOf((T) value);
    }

    private SwitchValue buildSwitchValue(String text) {
        return JsonTool.parseBeanFromText(text, SwitchValue.class);
    }
}
