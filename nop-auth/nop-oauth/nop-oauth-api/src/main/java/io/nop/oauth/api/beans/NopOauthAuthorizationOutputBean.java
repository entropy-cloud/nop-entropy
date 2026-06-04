//__XGEN_FORCE_OVERRIDE__
    package io.nop.oauth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopOauthAuthorizationOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _registeredClientId;

    
        @PropMeta(propId=2)
    
        public String getRegisteredClientId(){
            return _registeredClientId;
        }

        public void setRegisteredClientId(String value){
            this._registeredClientId = value;
        }


        private String _principalName;

    
        @PropMeta(propId=3)
    
        public String getPrincipalName(){
            return _principalName;
        }

        public void setPrincipalName(String value){
            this._principalName = value;
        }


        private String _authorizationGrantType;

    
        @PropMeta(propId=4)
    
        public String getAuthorizationGrantType(){
            return _authorizationGrantType;
        }

        public void setAuthorizationGrantType(String value){
            this._authorizationGrantType = value;
        }


        private String _authorizedScopes;

    
        @PropMeta(propId=5)
    
        public String getAuthorizedScopes(){
            return _authorizedScopes;
        }

        public void setAuthorizedScopes(String value){
            this._authorizedScopes = value;
        }


        private String _attributes;

    
        @PropMeta(propId=6)
    
        public String getAttributes(){
            return _attributes;
        }

        public void setAttributes(String value){
            this._attributes = value;
        }


        private String _state;

    
        @PropMeta(propId=7)
    
        public String getState(){
            return _state;
        }

        public void setState(String value){
            this._state = value;
        }


        private String _authorizationCodeValue;

    
        @PropMeta(propId=8)
    
        public String getAuthorizationCodeValue(){
            return _authorizationCodeValue;
        }

        public void setAuthorizationCodeValue(String value){
            this._authorizationCodeValue = value;
        }


        private java.sql.Timestamp _authorizationCodeIssuedAt;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getAuthorizationCodeIssuedAt(){
            return _authorizationCodeIssuedAt;
        }

        public void setAuthorizationCodeIssuedAt(java.sql.Timestamp value){
            this._authorizationCodeIssuedAt = value;
        }


        private java.sql.Timestamp _authorizationCodeExpiresAt;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getAuthorizationCodeExpiresAt(){
            return _authorizationCodeExpiresAt;
        }

        public void setAuthorizationCodeExpiresAt(java.sql.Timestamp value){
            this._authorizationCodeExpiresAt = value;
        }


        private String _authorizationCodeMetadata;

    
        @PropMeta(propId=11)
    
        public String getAuthorizationCodeMetadata(){
            return _authorizationCodeMetadata;
        }

        public void setAuthorizationCodeMetadata(String value){
            this._authorizationCodeMetadata = value;
        }


        private String _accessTokenValue;

    
        @PropMeta(propId=12)
    
        public String getAccessTokenValue(){
            return _accessTokenValue;
        }

        public void setAccessTokenValue(String value){
            this._accessTokenValue = value;
        }


        private java.sql.Timestamp _accessTokenIssuedAt;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getAccessTokenIssuedAt(){
            return _accessTokenIssuedAt;
        }

        public void setAccessTokenIssuedAt(java.sql.Timestamp value){
            this._accessTokenIssuedAt = value;
        }


        private java.sql.Timestamp _accessTokenExpiresAt;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getAccessTokenExpiresAt(){
            return _accessTokenExpiresAt;
        }

        public void setAccessTokenExpiresAt(java.sql.Timestamp value){
            this._accessTokenExpiresAt = value;
        }


        private String _accessTokenMetadata;

    
        @PropMeta(propId=15)
    
        public String getAccessTokenMetadata(){
            return _accessTokenMetadata;
        }

        public void setAccessTokenMetadata(String value){
            this._accessTokenMetadata = value;
        }


        private String _accessTokenType;

    
        @PropMeta(propId=16)
    
        public String getAccessTokenType(){
            return _accessTokenType;
        }

        public void setAccessTokenType(String value){
            this._accessTokenType = value;
        }


        private String _accessTokenScopes;

    
        @PropMeta(propId=17)
    
        public String getAccessTokenScopes(){
            return _accessTokenScopes;
        }

        public void setAccessTokenScopes(String value){
            this._accessTokenScopes = value;
        }


        private String _oidcIdTokenValue;

    
        @PropMeta(propId=18)
    
        public String getOidcIdTokenValue(){
            return _oidcIdTokenValue;
        }

        public void setOidcIdTokenValue(String value){
            this._oidcIdTokenValue = value;
        }


        private java.sql.Timestamp _oidcIdTokenIssuedAt;

    
        @PropMeta(propId=19)
    
        public java.sql.Timestamp getOidcIdTokenIssuedAt(){
            return _oidcIdTokenIssuedAt;
        }

        public void setOidcIdTokenIssuedAt(java.sql.Timestamp value){
            this._oidcIdTokenIssuedAt = value;
        }


        private java.sql.Timestamp _oidcIdTokenExpiresAt;

    
        @PropMeta(propId=20)
    
        public java.sql.Timestamp getOidcIdTokenExpiresAt(){
            return _oidcIdTokenExpiresAt;
        }

        public void setOidcIdTokenExpiresAt(java.sql.Timestamp value){
            this._oidcIdTokenExpiresAt = value;
        }


        private String _oidcIdTokenMetadata;

    
        @PropMeta(propId=21)
    
        public String getOidcIdTokenMetadata(){
            return _oidcIdTokenMetadata;
        }

        public void setOidcIdTokenMetadata(String value){
            this._oidcIdTokenMetadata = value;
        }


        private String _refreshTokenValue;

    
        @PropMeta(propId=22)
    
        public String getRefreshTokenValue(){
            return _refreshTokenValue;
        }

        public void setRefreshTokenValue(String value){
            this._refreshTokenValue = value;
        }


        private java.sql.Timestamp _refreshTokenIssuedAt;

    
        @PropMeta(propId=23)
    
        public java.sql.Timestamp getRefreshTokenIssuedAt(){
            return _refreshTokenIssuedAt;
        }

        public void setRefreshTokenIssuedAt(java.sql.Timestamp value){
            this._refreshTokenIssuedAt = value;
        }


        private java.sql.Timestamp _refreshTokenExpiresAt;

    
        @PropMeta(propId=24)
    
        public java.sql.Timestamp getRefreshTokenExpiresAt(){
            return _refreshTokenExpiresAt;
        }

        public void setRefreshTokenExpiresAt(java.sql.Timestamp value){
            this._refreshTokenExpiresAt = value;
        }


        private String _refreshTokenMetadata;

    
        @PropMeta(propId=25)
    
        public String getRefreshTokenMetadata(){
            return _refreshTokenMetadata;
        }

        public void setRefreshTokenMetadata(String value){
            this._refreshTokenMetadata = value;
        }


        private String _userCodeValue;

    
        @PropMeta(propId=26)
    
        public String getUserCodeValue(){
            return _userCodeValue;
        }

        public void setUserCodeValue(String value){
            this._userCodeValue = value;
        }


        private java.sql.Timestamp _userCodeIssuedAt;

    
        @PropMeta(propId=27)
    
        public java.sql.Timestamp getUserCodeIssuedAt(){
            return _userCodeIssuedAt;
        }

        public void setUserCodeIssuedAt(java.sql.Timestamp value){
            this._userCodeIssuedAt = value;
        }


        private java.sql.Timestamp _userCodeExpiresAt;

    
        @PropMeta(propId=28)
    
        public java.sql.Timestamp getUserCodeExpiresAt(){
            return _userCodeExpiresAt;
        }

        public void setUserCodeExpiresAt(java.sql.Timestamp value){
            this._userCodeExpiresAt = value;
        }


        private String _userCodeMetadata;

    
        @PropMeta(propId=29)
    
        public String getUserCodeMetadata(){
            return _userCodeMetadata;
        }

        public void setUserCodeMetadata(String value){
            this._userCodeMetadata = value;
        }


        private String _deviceCodeValue;

    
        @PropMeta(propId=30)
    
        public String getDeviceCodeValue(){
            return _deviceCodeValue;
        }

        public void setDeviceCodeValue(String value){
            this._deviceCodeValue = value;
        }


        private java.sql.Timestamp _deviceCodeIssuedAt;

    
        @PropMeta(propId=31)
    
        public java.sql.Timestamp getDeviceCodeIssuedAt(){
            return _deviceCodeIssuedAt;
        }

        public void setDeviceCodeIssuedAt(java.sql.Timestamp value){
            this._deviceCodeIssuedAt = value;
        }


        private java.sql.Timestamp _deviceCodeExpiresAt;

    
        @PropMeta(propId=32)
    
        public java.sql.Timestamp getDeviceCodeExpiresAt(){
            return _deviceCodeExpiresAt;
        }

        public void setDeviceCodeExpiresAt(java.sql.Timestamp value){
            this._deviceCodeExpiresAt = value;
        }


        private String _deviceCodeMetadata;

    
        @PropMeta(propId=33)
    
        public String getDeviceCodeMetadata(){
            return _deviceCodeMetadata;
        }

        public void setDeviceCodeMetadata(String value){
            this._deviceCodeMetadata = value;
        }


    }
