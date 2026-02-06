/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.oauth.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;

import io.nop.oauth.dao.entity.NopOauthAuthorizationConsent;
import io.nop.oauth.biz.INopOauthAuthorizationConsentBiz;

@BizModel("NopOauthAuthorizationConsent")
public class NopOauthAuthorizationConsentBizModel extends CrudBizModel<NopOauthAuthorizationConsent> implements INopOauthAuthorizationConsentBiz {
    public NopOauthAuthorizationConsentBizModel(){
        setEntityName(NopOauthAuthorizationConsent.class.getName());
    }
}
