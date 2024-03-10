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
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.component.parse.AbstractResourceParser;

public class DictModelParser extends AbstractResourceParser<DictModel> {

    @Override
    protected DictModel doParseResource(IResource resource) {
        DictBean dict = JsonTool.parseBeanFromResource(resource, DictBean.class);
        DictModel model = new DictModel();
        dict.setStatic(true);
        model.setLocation(SourceLocation.fromPath(resource.getPath()));
        model.setDictBean(dict);
        return model;
    }
}
