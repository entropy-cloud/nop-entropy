/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.dialect.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(name = "template", value = SqlTemplateModel.class),
        @JsonSubTypes.Type(name = "native", value = SqlNativeFunctionModel.class),
        @JsonSubTypes.Type(name = "function", value = SqlFunctionModel.class),})
public interface ISqlFunctionModel {
    String getType();

    String getName();

    String getDescription();

    String getTestSql();
}
