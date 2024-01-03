package io.nop.oauth.dao.entity._gen;

import io.nop.orm.support.OrmCompositePk;
import io.nop.oauth.dao.entity.NopOauthAuthorizationConsent;

/**
 * 用于生成复合主键的帮助类
 */
@SuppressWarnings({"PMD.UnnecessaryFullyQualifiedName"})
public class NopOauthAuthorizationConsentPkBuilder{
    private Object[] values = new Object[2];

   
    public NopOauthAuthorizationConsentPkBuilder setRegisteredClientId(java.lang.String value){
        this.values[0] = value;
        return this;
    }
   
    public NopOauthAuthorizationConsentPkBuilder setPrincipalName(java.lang.String value){
        this.values[1] = value;
        return this;
    }
   

    public OrmCompositePk build(){
        return OrmCompositePk.buildNotNull(NopOauthAuthorizationConsent.PK_PROP_NAMES,values);
    }
}
