/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.excel.imp;

import io.nop.commons.collections.KeyedList;
import io.nop.commons.util.StringHelper;
import io.nop.core.reflect.bean.BeanTool;

import java.util.List;

public class KeyedListAdapter implements IListAdapter {
    private final String keyProp;

    public KeyedListAdapter(String keyProp) {
        this.keyProp = keyProp;
    }

    @Override
    public List<Object> newList() {
        return new KeyedList<>(o -> BeanTool.instance().getProperty(o, keyProp));
    }

    @Override
    public boolean add(List<Object> list, Object value) {
        KeyedList<Object> keyedList = (KeyedList<Object>) list;
        String key = keyedList.getKey(value);
        if (key == null)
            return false;

        if (keyedList.containsKey(key))
            return false;

        return list.add(value);
    }

    public String getKey(Object value) {
        return StringHelper.toString(BeanTool.instance().getProperty(value, keyProp), null);
    }

    @Override
    public String getKeyProp() {
        return keyProp;
    }
}
