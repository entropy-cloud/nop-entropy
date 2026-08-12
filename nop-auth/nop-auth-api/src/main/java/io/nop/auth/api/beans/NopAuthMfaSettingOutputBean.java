//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthMfaSettingOutputBean {

    
        private String _userId;

    
        @PropMeta(propId=1)
    
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _mfaType;

    
        @PropMeta(propId=2)
    
        public String getMfaType(){
            return _mfaType;
        }

        public void setMfaType(String value){
            this._mfaType = value;
        }


        private String _status;

    
        @PropMeta(propId=4)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private String _phone;

    
        @PropMeta(propId=6)
    
        public String getPhone(){
            return _phone;
        }

        public void setPhone(String value){
            this._phone = value;
        }


        private Long _lastVerifiedWindow;

    
        @PropMeta(propId=7)
    
        public Long getLastVerifiedWindow(){
            return _lastVerifiedWindow;
        }

        public void setLastVerifiedWindow(Long value){
            this._lastVerifiedWindow = value;
        }


        private java.sql.Timestamp _lastVerifiedAt;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getLastVerifiedAt(){
            return _lastVerifiedAt;
        }

        public void setLastVerifiedAt(java.sql.Timestamp value){
            this._lastVerifiedAt = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=9)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private Integer _version;

    
        @PropMeta(propId=10)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=11)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=12)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=14)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=16)
    
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


        private List<Map<String,Object>> _recoveryCodes;

        public List<Map<String,Object>> getRecoveryCodes(){
            return _recoveryCodes;
        }

        public void setRecoveryCodes(List<Map<String,Object>> value){
            this._recoveryCodes = value;
        }


    }
