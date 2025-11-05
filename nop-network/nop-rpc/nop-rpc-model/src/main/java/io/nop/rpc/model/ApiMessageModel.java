/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.commons.lang.ITagSetSupport;
import io.nop.rpc.model._gen._ApiMessageModel;

import static io.nop.rpc.model.RpcModelConstants.OPTION_EXAMPLE;

public class ApiMessageModel extends _ApiMessageModel implements IWithOptions, ITagSetSupport {
    public ApiMessageModel() {

    }


    public String getExample() {
        return (String) getOptionValue(OPTION_EXAMPLE);
    }

    public void setExample(String example) {
        setOptionValue(OPTION_EXAMPLE, example);
    }
}
