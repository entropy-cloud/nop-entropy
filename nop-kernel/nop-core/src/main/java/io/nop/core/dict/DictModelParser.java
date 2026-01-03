/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.dict;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.DeltaJsonOptions;
import io.nop.core.lang.json.JObject;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;

import java.util.List;
import java.util.Map;

public class DictModelParser extends AbstractResourceParser<DictModel> {

    @Override
    protected DictModel doParseResource(IResource resource) {
        DeltaJsonOptions options = new DeltaJsonOptions();
        options.setResourceLoader(this::loadDict);
        DictBean dict = JsonTool.loadDeltaBeanFromResource(resource, DictBean.class, options);
        DictModel model = new DictModel();
        dict.setStatic(true);
        model.setLocation(SourceLocation.fromPath(resource.getPath()));
        model.setDictBean(dict);
        return model;
    }

    private Map<String, Object> loadDict(IResource resource) {
        JObject obj = JsonTool.parseBeanFromResource(resource, JObject.class, true);
        List<JObject> list = (List<JObject>) obj.get(CoreConstants.PROP_OPTIONS);
        if (list != null) {
            // 将value作为x:id来使用
            for (JObject item : list) {
                if (item.containsKey(CoreConstants.ATTR_X_ID))
                    continue;

                ValueWithLocation vl = item.getLocValue(CoreConstants.PROP_VALUE);
                if (vl != null) {
                    item.put(CoreConstants.ATTR_X_ID, vl);
                }
            }
        }
        return obj;
    }
}
