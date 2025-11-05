/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.oauth.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.oauth.dao.entity._gen._NopOauthAuthorizationConsent;

import io.nop.oauth.dao.entity._gen.NopOauthAuthorizationConsentPkBuilder;


@BizObjName("NopOauthAuthorizationConsent")
public class NopOauthAuthorizationConsent extends _NopOauthAuthorizationConsent{


    public static NopOauthAuthorizationConsentPkBuilder newPk(){
        return new NopOauthAuthorizationConsentPkBuilder();
    }

}
