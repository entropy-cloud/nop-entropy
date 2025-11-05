/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.json;

import io.nop.dataset.binder.IDataParameters;

public class MySqlJsonTypeHandler extends DefaultJsonTypeHandler {
    @Override
    public void setValue(IDataParameters params, int index, Object value) {
        if (value == null) {
            params.setString(index, null);
        } else {
            String str = value.toString();
            params.setString(index, str);
        }
    }
}
