/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.binder;

import io.nop.commons.CommonConstants;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.type.StdSqlType;

public class EncodedDataParameterBinder implements IDataParameterBinder {
    private final IDataParameterBinder binder;
    private final ITextCipher cipher;

    public EncodedDataParameterBinder(IDataParameterBinder binder, ITextCipher cipher) {
        this.binder = binder;
        this.cipher = cipher;
    }

    @Override
    public StdSqlType getStdSqlType() {
        return StdSqlType.VARCHAR;
    }

    @Override
    public Object getValue(IDataParameters params, int index) {
        Object value = binder.getValue(params, index);
        if (value == null)
            return null;
        String str = value.toString();
        if (str.startsWith(CommonConstants.ENC_VALUE_PREFIX)) {
            String text = str.substring(CommonConstants.ENC_VALUE_PREFIX.length());
            return cipher.decrypt(text);
        }
        return str;
    }

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value != null) {
            String text = value.toString();
            String str = cipher.encrypt(text);
            value = CommonConstants.ENC_VALUE_PREFIX + str;
        }
        binder.setValue(params, index, value);
    }
}
