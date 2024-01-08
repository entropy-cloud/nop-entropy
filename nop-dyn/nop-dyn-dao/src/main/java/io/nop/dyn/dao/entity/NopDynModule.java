package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.core.resource.ResourceHelper;
import io.nop.dyn.dao.entity._gen._NopDynModule;


@BizObjName("NopDynModule")
public class NopDynModule extends _NopDynModule {

    public String getNopModuleId() {
        return ResourceHelper.getModuleIdFromModuleName(getModuleName());
    }

}
