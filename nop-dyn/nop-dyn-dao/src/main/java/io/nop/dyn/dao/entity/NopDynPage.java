package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.dyn.dao.entity._gen._NopDynPage;


@BizObjName("NopDynPage")
public class NopDynPage extends _NopDynPage {
    public String getPagePath() {
        String nopModuleId = getModule().getNopModuleId();
        return "/" + nopModuleId + "/" + getPageGroup() + "/" + getPageName() + ".page.yaml";
    }
}