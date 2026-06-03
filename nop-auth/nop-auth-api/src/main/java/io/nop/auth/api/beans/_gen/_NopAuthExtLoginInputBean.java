//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthExtLoginInputBean extends CrudInputBase {

    
        private String _sid;

        @PropMeta(propId=1)
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _userId;

        @PropMeta(propId=2)
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private Integer _loginType;

        @PropMeta(propId=3)
        public Integer getLoginType(){
            return _loginType;
        }

        public void setLoginType(Integer value){
            this._loginType = value;
        }


        private String _extId;

        @PropMeta(propId=4)
        public String getExtId(){
            return _extId;
        }

        public void setExtId(String value){
            this._extId = value;
        }


        private String _credential;

        @PropMeta(propId=5)
        public String getCredential(){
            return _credential;
        }

        public void setCredential(String value){
            this._credential = value;
        }


        private Boolean _verified;

        @PropMeta(propId=6)
        public Boolean getVerified(){
            return _verified;
        }

        public void setVerified(Boolean value){
            this._verified = value;
        }


        private java.sql.Timestamp _lastLoginTime;

        @PropMeta(propId=7)
        public java.sql.Timestamp getLastLoginTime(){
            return _lastLoginTime;
        }

        public void setLastLoginTime(java.sql.Timestamp value){
            this._lastLoginTime = value;
        }


        private String _lastLoginIp;

        @PropMeta(propId=8)
        public String getLastLoginIp(){
            return _lastLoginIp;
        }

        public void setLastLoginIp(String value){
            this._lastLoginIp = value;
        }


        private Byte _delFlag;

        @PropMeta(propId=9)
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private String _remark;

        @PropMeta(propId=15)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
