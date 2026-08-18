//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.crud;

    import io.nop.api.core.annotations.biz.BizModel;
    import io.nop.auth.api.beans.NopAuthMfaCredentialInputBean;
    import io.nop.auth.api.beans.NopAuthMfaCredentialOutputBean;
    import io.nop.api.core.api.ICrudApi;
    

    @BizModel("NopAuthMfaCredential")
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public interface NopAuthMfaCredentialApi extends ICrudApi<NopAuthMfaCredentialInputBean, NopAuthMfaCredentialOutputBean> {
    }
