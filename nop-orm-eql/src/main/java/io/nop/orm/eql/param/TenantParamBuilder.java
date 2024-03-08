package io.nop.orm.eql.param;

import io.nop.api.core.context.ContextProvider;

import java.util.List;

public class TenantParamBuilder implements ISqlParamBuilder {
    public static final TenantParamBuilder INSTANCE = new TenantParamBuilder();

    @Override
    public void buildParams(List<Object> input, List<Object> params) {
        params.add(ContextProvider.currentTenantId());
    }
}