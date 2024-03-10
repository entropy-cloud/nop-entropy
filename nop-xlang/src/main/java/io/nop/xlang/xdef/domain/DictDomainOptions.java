/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.beans.DictBean;
import io.nop.core.context.IEvalContext;
import io.nop.core.dict.DictProvider;
import io.nop.xlang.xdef.IStdDomainOptions;

public class DictDomainOptions implements IStdDomainOptions {
    private final String dictName;
    private DictBean dictBean;

    public DictDomainOptions(String dictName) {
        this.dictName = dictName;
    }

    public String getDictName() {
        return dictName;
    }

    @Override
    public String toString() {
        return dictName;
    }

    public DictBean loadDictBean(IEvalContext context) {
        if (dictBean == null) {
            DictBean dict = DictProvider.instance().getDict(null, dictName, null, context);
            if (dict != null && dict.isStatic()) {
                dictBean = dict;
            }
            return dict;
        }
        return dictBean;
    }
}