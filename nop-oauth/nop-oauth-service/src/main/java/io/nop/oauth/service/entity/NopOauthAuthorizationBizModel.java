
package io.nop.oauth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.oauth.dao.entity.NopOauthAuthorization;

@BizModel("NopOauthAuthorization")
public class NopOauthAuthorizationBizModel extends CrudBizModel<NopOauthAuthorization>{
    public NopOauthAuthorizationBizModel(){
        setEntityName(NopOauthAuthorization.class.getName());
    }
}
