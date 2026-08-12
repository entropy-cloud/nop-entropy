
package io.nop.auth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.auth.biz.INopAuthMfaChallengeBiz;
import io.nop.auth.dao.entity.NopAuthMfaChallenge;

@BizModel("NopAuthMfaChallenge")
public class NopAuthMfaChallengeBizModel extends CrudBizModel<NopAuthMfaChallenge> implements INopAuthMfaChallengeBiz{
    public NopAuthMfaChallengeBizModel(){
        setEntityName(NopAuthMfaChallenge.class.getName());
    }
}
