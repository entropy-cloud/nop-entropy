package io.nop.dyn.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.dyn.dao.entity._gen._NopDynDomain;
import org.jetbrains.annotations.NotNull;


@BizObjName("NopDynDomain")
public class NopDynDomain extends _NopDynDomain implements Comparable<NopDynDomain> {

    @Override
    public int compareTo(@NotNull NopDynDomain o) {
        return this.getDomainName().compareTo(o.getDomainName());
    }
}
