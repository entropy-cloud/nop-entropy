package io.nop.biz.crud;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.graphql.core.IBizModelImpl;
import io.nop.orm.support.DynamicOrmEntity;

@BizModel("")
public class DynamicCrudBizModel extends CrudBizModel<DynamicOrmEntity> implements IBizModelImpl {
    private String bizObjName;

    @Override
    public String getBizObjName() {
        return bizObjName;
    }

    public void setBizObjName(String bizObjName) {
        this.bizObjName = bizObjName;
    }
}