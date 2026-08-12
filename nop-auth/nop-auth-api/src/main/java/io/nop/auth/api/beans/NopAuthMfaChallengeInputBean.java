//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthMfaChallengeInputBean extends CrudInputBase {

    
        private String _challengeToken;

    
        @PropMeta(propId=1)
    
        public String getChallengeToken(){
            return _challengeToken;
        }

        public void setChallengeToken(String value){
            this._challengeToken = value;
        }


        private String _userId;

    
        @PropMeta(propId=2)
    
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _mfaType;

    
        @PropMeta(propId=3)
    
        public String getMfaType(){
            return _mfaType;
        }

        public void setMfaType(String value){
            this._mfaType = value;
        }


        private Integer _loginType;

    
        @PropMeta(propId=4)
    
        public Integer getLoginType(){
            return _loginType;
        }

        public void setLoginType(Integer value){
            this._loginType = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=5)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _phone;

    
        @PropMeta(propId=6)
    
        public String getPhone(){
            return _phone;
        }

        public void setPhone(String value){
            this._phone = value;
        }


        private Long _expireAt;

    
        @PropMeta(propId=7)
    
        public Long getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(Long value){
            this._expireAt = value;
        }


        private Integer _failCount;

    
        @PropMeta(propId=8)
    
        public Integer getFailCount(){
            return _failCount;
        }

        public void setFailCount(Integer value){
            this._failCount = value;
        }


    }
