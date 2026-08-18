//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthMfaTrustedDeviceInputBean extends CrudInputBase {

    
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


        private String _deviceHash;

    
        @PropMeta(propId=3)
    
        public String getDeviceHash(){
            return _deviceHash;
        }

        public void setDeviceHash(String value){
            this._deviceHash = value;
        }


        private String _deviceName;

    
        @PropMeta(propId=4)
    
        public String getDeviceName(){
            return _deviceName;
        }

        public void setDeviceName(String value){
            this._deviceName = value;
        }


        private java.sql.Timestamp _expireAt;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(java.sql.Timestamp value){
            this._expireAt = value;
        }


        private java.sql.Timestamp _lastUsedAt;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getLastUsedAt(){
            return _lastUsedAt;
        }

        public void setLastUsedAt(java.sql.Timestamp value){
            this._lastUsedAt = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=7)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


    }
