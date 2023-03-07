/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.annotations.biz;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class BizMakerCheckerMeta {
    private final String tryMethod;
    private final String cancelMethod;

    public BizMakerCheckerMeta(@JsonProperty("tryMethod") String tryMethod,
                               @JsonProperty("cancelMethod") String cancelMethod) {
        this.tryMethod = tryMethod;
        this.cancelMethod = cancelMethod;
    }

    public String getTryMethod() {
        return tryMethod;
    }

    public String getCancelMethod() {
        return cancelMethod;
    }
}