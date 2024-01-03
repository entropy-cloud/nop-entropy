
package io.nop.oauth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.oauth.dao.entity.NopOauthRegisteredClient;

@BizModel("NopOauthRegisteredClient")
public class NopOauthRegisteredClientBizModel extends CrudBizModel<NopOauthRegisteredClient>{
    public NopOauthRegisteredClientBizModel(){
        setEntityName(NopOauthRegisteredClient.class.getName());
    }
}
