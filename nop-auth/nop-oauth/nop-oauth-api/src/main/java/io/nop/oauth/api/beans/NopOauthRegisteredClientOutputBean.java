//__XGEN_FORCE_OVERRIDE__
    package io.nop.oauth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopOauthRegisteredClientOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _clientId;

    
        @PropMeta(propId=2)
    
        public String getClientId(){
            return _clientId;
        }

        public void setClientId(String value){
            this._clientId = value;
        }


        private java.sql.Timestamp _clientIdIssuedAt;

    
        @PropMeta(propId=3)
    
        public java.sql.Timestamp getClientIdIssuedAt(){
            return _clientIdIssuedAt;
        }

        public void setClientIdIssuedAt(java.sql.Timestamp value){
            this._clientIdIssuedAt = value;
        }


        private String _clientSecret;

    
        @PropMeta(propId=4)
    
        public String getClientSecret(){
            return _clientSecret;
        }

        public void setClientSecret(String value){
            this._clientSecret = value;
        }


        private java.sql.Timestamp _clientSecretExpiresAt;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getClientSecretExpiresAt(){
            return _clientSecretExpiresAt;
        }

        public void setClientSecretExpiresAt(java.sql.Timestamp value){
            this._clientSecretExpiresAt = value;
        }


        private String _clientName;

    
        @PropMeta(propId=6)
    
        public String getClientName(){
            return _clientName;
        }

        public void setClientName(String value){
            this._clientName = value;
        }


        private String _clientAuthenticationMethods;

    
        @PropMeta(propId=7)
    
        public String getClientAuthenticationMethods(){
            return _clientAuthenticationMethods;
        }

        public void setClientAuthenticationMethods(String value){
            this._clientAuthenticationMethods = value;
        }


        private String _authorizationGrantTypes;

    
        @PropMeta(propId=8)
    
        public String getAuthorizationGrantTypes(){
            return _authorizationGrantTypes;
        }

        public void setAuthorizationGrantTypes(String value){
            this._authorizationGrantTypes = value;
        }


        private String _redirectUris;

    
        @PropMeta(propId=9)
    
        public String getRedirectUris(){
            return _redirectUris;
        }

        public void setRedirectUris(String value){
            this._redirectUris = value;
        }


        private String _postLogoutRedirectUris;

    
        @PropMeta(propId=10)
    
        public String getPostLogoutRedirectUris(){
            return _postLogoutRedirectUris;
        }

        public void setPostLogoutRedirectUris(String value){
            this._postLogoutRedirectUris = value;
        }


        private String _scopes;

    
        @PropMeta(propId=11)
    
        public String getScopes(){
            return _scopes;
        }

        public void setScopes(String value){
            this._scopes = value;
        }


        private String _clientSettings;

    
        @PropMeta(propId=12)
    
        public String getClientSettings(){
            return _clientSettings;
        }

        public void setClientSettings(String value){
            this._clientSettings = value;
        }


        private String _tokenSettings;

    
        @PropMeta(propId=13)
    
        public String getTokenSettings(){
            return _tokenSettings;
        }

        public void setTokenSettings(String value){
            this._tokenSettings = value;
        }


    }
