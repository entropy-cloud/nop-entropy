
package io.nop.sys.service.entity;

import io.nop.api.core.annotations.biz.BizAction;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.sys.biz.INopSysCompactExtFieldBiz;
import io.nop.sys.dao.entity.NopSysCompactExtField;
import io.nop.sys.service.impl.SysCompactExtFieldHelper;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

@BizModel("NopSysCompactExtField")
public class NopSysCompactExtFieldBizModel extends CrudBizModel<NopSysCompactExtField> implements INopSysCompactExtFieldBiz {
    /**
     * 紧凑扩展字段映射调整后必须重建helper内存缓存：position/defaultValue变更不刷新
     * 会导致getExtValue按旧position解析extFlags读到其他字段的值（静默数据错读）。
     */
    @Inject
    @Nullable
    protected SysCompactExtFieldHelper compactExtFieldHelper;

    public NopSysCompactExtFieldBizModel(){
        setEntityName(NopSysCompactExtField.class.getName());
    }

    @BizAction
    @Override
    protected void afterEntityChange(@Name("entity") NopSysCompactExtField entity, @Name("action") String action,
                                     IServiceContext context) {
        super.afterEntityChange(entity, action, context);
        if (compactExtFieldHelper != null)
            compactExtFieldHelper.refreshCache();
    }
}
