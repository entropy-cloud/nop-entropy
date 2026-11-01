/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dyn.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizLoader;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.ContextSource;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.BizConstants;
import io.nop.core.context.IServiceContext;
import io.nop.dyn.service.codegen.DynCodeGen;
import jakarta.inject.Inject;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.dyn.dao.entity.NopDynEntityMeta;
import io.nop.dyn.dao.entity.NopDynPropMeta;
import io.nop.dyn.biz.INopDynEntityMetaBiz;

import java.util.ArrayList;
import java.util.List;

@BizModel("NopDynEntityMeta")
public class NopDynEntityMetaBizModel extends CrudBizModel<NopDynEntityMeta> implements INopDynEntityMetaBiz {

    @Inject
    DynCodeGen codeGen;

    @Inject
    io.nop.dao.api.IDaoProvider daoProvider;

    public NopDynEntityMetaBizModel() {
        setEntityName(NopDynEntityMeta.class.getName());
    }

    /**
     * 实体级删除缓存失效（I-04/C6-101，plan 2525 裁定⑨）。dao 删除不同步父对象内存集合——
     * 先移除再重生成，已删实体不会残留在重新生成的模型中。**形态偏离说明（对裁定⑨函数级
     * 先例的镜像修正）**：实体级删除不能走实体级 generateBizModel——删除后 prepareBizObject
     * 的 requireEntityModel(已删实体) 必然失败（实体已不在模块 orm 模型中，与函数级删除的本质
     * 差异：函数不是 orm 模型成员）；重生成走模块级通道 generateForModule（orm 模型随当前 DB
     * 状态重建，已删实体自然排除），再触发 ORM 全量重载（refreshOrmModel=true 语义由
     * reloadModel(true) 承载）。偏离已在 red-green 总册与 plan 勘误登记。
     */
    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopDynEntityMeta entity, @Name("action") String action,
                                     IServiceContext context) {
        super.afterEntityChange(entity, action, context);

        if (BizConstants.METHOD_DELETE.equals(action)) {
            // module关联可能未加载（session内按example重查的删除路径）：null守卫，集合同步仅在关联就绪时执行
            io.nop.dyn.dao.entity.NopDynModule module = entity.getModule();
            if (module != null)
                module.getEntityMetas().remove(entity);
            io.nop.dyn.dao.entity.NopDynModule moduleEntity = module;
            if (moduleEntity == null)
                moduleEntity = daoProvider.daoFor(io.nop.dyn.dao.entity.NopDynModule.class)
                        .getEntityById(entity.getModuleId());
            codeGen.generateForModule(moduleEntity);
            codeGen.reloadModel(true);
        }
    }

    @BizLoader(autoCreateField = true)
    public List<DictOptionBean> allProps(@ContextSource NopDynEntityMeta entityMeta) {
        List<DictOptionBean> options = new ArrayList<>();
        DictOptionBean option = new DictOptionBean();
        option.setValue("id");
        option.setLabel("ID");
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
