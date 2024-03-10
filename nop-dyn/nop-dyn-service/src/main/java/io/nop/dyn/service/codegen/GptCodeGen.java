/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.codegen;

import io.nop.core.lang.xml.XNode;
import io.nop.gpt.core.response.XmlResponseParser;
import io.nop.gpt.orm.GptOrmModelParser;
import io.nop.orm.model.OrmModel;

public class GptCodeGen {
    public OrmModel generateOrmModel(String response) {
        XNode node = new XmlResponseParser().parseResponse(response);
        OrmModel ormModel = new GptOrmModelParser().parseOrmModel(node);
        return ormModel;
    }
}
