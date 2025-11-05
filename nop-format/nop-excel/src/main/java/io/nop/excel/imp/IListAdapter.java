/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp;

import java.util.List;

public interface IListAdapter {
    List<Object> newList();

    boolean add(List<Object> list, Object value);

    String getKey(Object value);

    String getKeyProp();
}