/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.dialect.json;

import io.nop.dataset.binder.IDataParameters;

import java.nio.charset.StandardCharsets;

public class H2JsonTypeHandler extends DefaultJsonTypeHandler {

    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setBytes(index, null);
        } else {
            String str = value.toString();
            params.setBytes(index, str.getBytes(StandardCharsets.UTF_8));
        }
    }
}
