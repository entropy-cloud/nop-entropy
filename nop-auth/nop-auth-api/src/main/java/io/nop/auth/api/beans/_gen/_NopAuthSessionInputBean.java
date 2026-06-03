//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthSessionInputBean extends CrudInputBase {

    
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


        private String _cacheData;

        @PropMeta(propId=17)
        public String getCacheData(){
            return _cacheData;
        }

        public void setCacheData(String value){
            this._cacheData = value;
        }


        private String _remark;

        @PropMeta(propId=20)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
