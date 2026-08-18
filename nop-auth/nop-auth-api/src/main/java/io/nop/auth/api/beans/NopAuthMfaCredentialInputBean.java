//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthMfaCredentialInputBean extends CrudInputBase {

    
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


        private String _credentialId;

    
        @PropMeta(propId=3)
    
        public String getCredentialId(){
            return _credentialId;
        }

        public void setCredentialId(String value){
            this._credentialId = value;
        }


        private String _publicKey;

    
        @PropMeta(propId=4)
    
        public String getPublicKey(){
            return _publicKey;
        }

        public void setPublicKey(String value){
            this._publicKey = value;
        }


        private Long _signCount;

    
        @PropMeta(propId=5)
    
        public Long getSignCount(){
            return _signCount;
        }

        public void setSignCount(Long value){
            this._signCount = value;
        }


        private String _transports;

    
        @PropMeta(propId=6)
    
        public String getTransports(){
            return _transports;
        }

        public void setTransports(String value){
            this._transports = value;
        }


        private String _name;

    
        @PropMeta(propId=7)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _status;

    
        @PropMeta(propId=8)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private java.sql.Timestamp _lastUsedAt;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getLastUsedAt(){
            return _lastUsedAt;
        }

        public void setLastUsedAt(java.sql.Timestamp value){
            this._lastUsedAt = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=10)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=12)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _remark;

    
        @PropMeta(propId=17)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
