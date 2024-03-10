/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp;

import java.util.ArrayList;
import java.util.List;

public class ArrayListAdapter implements IListAdapter {
    public static final ArrayListAdapter INSTANCE = new ArrayListAdapter();

    @Override
    public List<Object> newList() {
        return new ArrayList<>();
    }

    @Override
    public boolean add(List<Object> list, Object value) {
        return list.add(value);
    }

    @Override
    public String getKey(Object value) {
        return null;
    }

    @Override
    public String getKeyProp() {
        return null;
    }
}
