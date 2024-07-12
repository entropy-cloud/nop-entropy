/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynPropMeta;

import java.util.ArrayList;
import java.util.List;

@BizModel("NopDynEntityMeta")
public class NopDynEntityMetaBizModel extends CrudBizModel<NopDynEntityMeta> {
    public NopDynEntityMetaBizModel() {
        setEntityName(NopDynEntityMeta.class.getName());
    }

    @BizLoader(autoCreateField = true)
    public List<DictOptionBean> allProps(@ContextSource NopDynEntityMeta entityMeta) {
        List<DictOptionBean> options = new ArrayList<>();
        DictOptionBean option = new DictOptionBean();
        option.setValue("id");
        option.setValue("ID");
        options.add(option);

        for (NopDynPropMeta propMeta : entityMeta.getPropMetas()) {
            DictOptionBean prop = new DictOptionBean();
            prop.setValue(propMeta.getPropName());
            prop.setLabel(propMeta.getDisplayName());
            options.add(prop);
        }
        return options;
    }
}
