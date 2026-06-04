//__XGEN_FORCE_OVERRIDE__
    package io.nop.oauth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopOauthAuthorizationConsentInputBean extends CrudInputBase {

    
        private String _registeredClientId;

    
        @PropMeta(propId=1)
    
        public String getRegisteredClientId(){
            return _registeredClientId;
        }

        public void setRegisteredClientId(String value){
            this._registeredClientId = value;
        }


        private String _principalName;

    
        @PropMeta(propId=2)
    
        public String getPrincipalName(){
            return _principalName;
        }

        public void setPrincipalName(String value){
            this._principalName = value;
        }


        private String _authorities;

    
        @PropMeta(propId=3)
    
        public String getAuthorities(){
            return _authorities;
        }

        public void setAuthorities(String value){
            this._authorities = value;
        }


    }
