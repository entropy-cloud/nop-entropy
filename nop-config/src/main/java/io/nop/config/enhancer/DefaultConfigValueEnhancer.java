/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.enhancer;

import io.nop.api.core.config.IConfigValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.StaticValue;
import io.nop.commons.crypto.ITextCipher;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.model.query.SwitchValue;

import static io.nop.config.ConfigConstants.CFG_SEC_PREFIX;
import static io.nop.config.ConfigConstants.CFG_SWITCH_PREFIX;

public class DefaultConfigValueEnhancer implements IConfigValueEnhancer {
    private final ITextCipher cipher;

    public DefaultConfigValueEnhancer(ITextCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public <T> IConfigValue<T> enhance(Object value, Class<T> targetClass) {
        if (value instanceof String) {
            String str = value.toString();
            if (str.startsWith(CFG_SWITCH_PREFIX)) {
                SwitchValue switchValue = buildSwitchValue(str.substring(CFG_SWITCH_PREFIX.length()));
                switchValue.normalizeValue(targetClass);
                return (IConfigValue<T>) switchValue;
            }

            if (str.startsWith(CFG_SEC_PREFIX)) {
                value = cipher.decrypt(str.substring(CFG_SEC_PREFIX.length()));
            }
        }

        if (targetClass != null)
            value = convertValue(value, targetClass);

        return StaticValue.valueOf((T) value);
    }

    private SwitchValue buildSwitchValue(String text) {
        return (SwitchValue) JsonTool.parseBeanFromText(text, SwitchValue.class);
    }

    private Object convertValue(Object value, Class<?> targetClass) {
        if (targetClass == null || targetClass == Object.class)
            return value;
        return ConvertHelper.convertTo(targetClass, value, NopException::new);
    }
}
