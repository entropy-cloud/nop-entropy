/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.lang.json.utils;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.commons.util.StringHelper;

import java.util.ArrayList;
import java.util.List;

public class JsonVisitState {
    private final Object root;
    private final List<Object> jsonPath = new ArrayList<>();

    public JsonVisitState(Object root) {
        this.root = root;
    }

    public Object getRoot(){
        return root;
    }
    
    public String getJsonPathString() {
        return StringHelper.join(jsonPath, ".");
    }

    public List<Object> getJsonPath() {
        return jsonPath;
    }

    public String getJsonField() {
        return ConvertHelper.toString(getLastKey());
    }

    public Object getLastKey() {
        if (jsonPath.isEmpty())
            return null;
        return jsonPath.get(jsonPath.size() - 1);
    }

    public void enter(Object key) {
        this.jsonPath.add(key);
    }

    public void leave() {
        this.jsonPath.remove(jsonPath.size() - 1);
    }
}
