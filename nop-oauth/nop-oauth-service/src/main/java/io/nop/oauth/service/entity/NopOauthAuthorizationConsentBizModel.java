
package io.nop.oauth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.oauth.dao.entity.NopOauthAuthorizationConsent;

@BizModel("NopOauthAuthorizationConsent")
public class NopOauthAuthorizationConsentBizModel extends CrudBizModel<NopOauthAuthorizationConsent>{
    public NopOauthAuthorizationConsentBizModel(){
        setEntityName(NopOauthAuthorizationConsent.class.getName());
    }
}
