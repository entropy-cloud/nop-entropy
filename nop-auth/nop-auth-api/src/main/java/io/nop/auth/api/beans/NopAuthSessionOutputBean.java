//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthSessionOutputBean {

    
        private String _sessionId;

    
        @PropMeta(propId=1)
    
        public String getSessionId(){
            return _sessionId;
        }

        public void setSessionId(String value){
            this._sessionId = value;
        }


        private String _userId;

    
        @PropMeta(propId=2)
    
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _userName;

    
        @PropMeta(propId=3)
    
        public String getUserName(){
            return _userName;
        }

        public void setUserName(String value){
            this._userName = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=4)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _loginAddr;

    
        @PropMeta(propId=5)
    
        public String getLoginAddr(){
            return _loginAddr;
        }

        public void setLoginAddr(String value){
            this._loginAddr = value;
        }


        private String _loginDevice;

    
        @PropMeta(propId=6)
    
        public String getLoginDevice(){
            return _loginDevice;
        }

        public void setLoginDevice(String value){
            this._loginDevice = value;
        }


        private String _loginApp;

    
        @PropMeta(propId=7)
    
        public String getLoginApp(){
            return _loginApp;
        }

        public void setLoginApp(String value){
            this._loginApp = value;
        }


        private String _loginOs;

    
        @PropMeta(propId=8)
    
        public String getLoginOs(){
            return _loginOs;
        }

        public void setLoginOs(String value){
            this._loginOs = value;
        }


        private java.sql.Timestamp _loginTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getLoginTime(){
            return _loginTime;
        }

        public void setLoginTime(java.sql.Timestamp value){
            this._loginTime = value;
        }


        private Integer _loginType;

    
        @PropMeta(propId=10)
    
        public Integer getLoginType(){
            return _loginType;
        }

        public void setLoginType(Integer value){
            this._loginType = value;
        }


        private String _loginType_label;

    
        public String getLoginType_label(){
            return _loginType_label;
        }

        public void setLoginType_label(String value){
            this._loginType_label = value;
        }


        private java.sql.Timestamp _logoutTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getLogoutTime(){
            return _logoutTime;
        }

        public void setLogoutTime(java.sql.Timestamp value){
            this._logoutTime = value;
        }


        private Integer _logoutType;

    
        @PropMeta(propId=12)
    
        public Integer getLogoutType(){
            return _logoutType;
        }

        public void setLogoutType(Integer value){
            this._logoutType = value;
        }


        private String _logoutType_label;

    
        public String getLogoutType_label(){
            return _logoutType_label;
        }

        public void setLogoutType_label(String value){
            this._logoutType_label = value;
        }


        private String _logoutBy;

    
        @PropMeta(propId=13)
    
        public String getLogoutBy(){
            return _logoutBy;
        }

        public void setLogoutBy(String value){
            this._logoutBy = value;
        }


        private java.time.LocalDateTime _lastAccessTime;

    
        @PropMeta(propId=14)
    
        public java.time.LocalDateTime getLastAccessTime(){
            return _lastAccessTime;
        }

        public void setLastAccessTime(java.time.LocalDateTime value){
            this._lastAccessTime = value;
        }


        private String _accessToken;

    
        @PropMeta(propId=15)
    
        public String getAccessToken(){
            return _accessToken;
        }

        public void setAccessToken(String value){
            this._accessToken = value;
        }


        private String _refreshToken;

    
        @PropMeta(propId=16)
    
        public String getRefreshToken(){
            return _refreshToken;
        }

        public void setRefreshToken(String value){
            this._refreshToken = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=18)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=19)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=20)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _user;

        public Map<String,Object> getUser(){
            return _user;
        }

        public void setUser(Map<String,Object> value){
            this._user = value;
        }


    }
