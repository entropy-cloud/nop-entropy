/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.json.delta;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.eval.IPredicateEvaluator;
import io.nop.core.lang.json.JObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JsonFeatureSwitch {
    public static final JsonFeatureSwitch INSTANCE = new JsonFeatureSwitch();

    public Object process(Object o, IPredicateEvaluator evaluator) {
        if (o instanceof List) {
            return processList((List<Object>) o, evaluator);
        } else if (o instanceof Map) {
            return processMap((Map<String, Object>) o, evaluator);
        } else {
            return o;
        }
    }

    private List<Object> processList(List<Object> o, IPredicateEvaluator evaluator) {
        for (int i = 0, n = o.size(); i < n; i++) {
            Object value = o.get(i);
            if (value != null) {
                value = process(value, evaluator);
                if (value == null) {
                    o.remove(i);
                    i--;
                    n--;
                }
            }
        }
        return o;
    }

    public Map<String, Object> processMap(Map<String, Object> map, IPredicateEvaluator evaluator) {
        String on = (String) map.remove(CoreConstants.ATTR_FEATURE_ON);
        String off = (String) map.remove(CoreConstants.ATTR_FEATURE_OFF);
        if (!StringHelper.isEmpty(on)) {
            if (!evaluator.evaluate(getLocation(map, CoreConstants.ATTR_FEATURE_ON), on)) {
                return null;
            }
        }

        if (!StringHelper.isEmpty(off)) {
            if (evaluator.evaluate(getLocation(map, CoreConstants.ATTR_FEATURE_OFF), off))
                return null;
        }

        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            if (entry.getValue() != null) {
                Object value = process(entry.getValue(), evaluator);
                if (value == null)
                    it.remove();
            }
        }
        return map;
    }

    SourceLocation getLocation(Map<String, Object> map, String key) {
        if (map instanceof JObject)
            return ((JObject) map).getLocation(key);
        return null;
    }

}
