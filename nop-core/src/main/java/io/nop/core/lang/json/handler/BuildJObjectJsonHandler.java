/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.handler;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.JArray;
import io.nop.core.lang.json.JObject;

import java.util.List;
import java.util.Map;

public class BuildJObjectJsonHandler extends BuildObjectJsonHandler {
    @Override
    protected List<Object> newArray(SourceLocation loc) {
        JArray array = new JArray(loc);
        if (getComment() != null)
            array.setComment(getComment());
        return array;
    }

    @Override
    protected Map<String, Object> newObject(SourceLocation loc) {
        JObject obj = new JObject(loc);
        if (getComment() != null)
            obj.setComment(getComment());
        return obj;
    }

    @Override
    protected void addToList(List<Object> list, SourceLocation loc, Object value) {
        list.add(ValueWithLocation.of(loc, value));
    }

    @Override
    protected void addToMap(Map<String, Object> map, SourceLocation loc, String key, Object value) {
        map.put(key, ValueWithLocation.of(loc, value));
    }
}