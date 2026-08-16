package io.nop.credential.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.credential.biz.INopCredentialOauthStateBiz;
import io.nop.credential.dao.entity.NopCredentialOauthState;

@BizModel("NopCredentialOauthState")
public class NopCredentialOauthStateBizModel extends CrudBizModel<NopCredentialOauthState> implements INopCredentialOauthStateBiz {
    public NopCredentialOauthStateBizModel(){
        setEntityName(NopCredentialOauthState.class.getName());
    }
}
