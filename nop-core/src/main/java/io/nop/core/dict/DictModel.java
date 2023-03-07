/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.dict;

import io.nop.api.core.beans.DictBean;
import io.nop.core.resource.component.AbstractComponentModel;

public class DictModel extends AbstractComponentModel {
    private DictBean dictBean;

    public DictModel() {
    }

    public DictModel(DictBean dictBean) {
        this.dictBean = dictBean;
    }

    public DictBean getDictBean() {
        return dictBean;
    }

    public void setDictBean(DictBean dictBean) {
        this.dictBean = dictBean;
    }
}
