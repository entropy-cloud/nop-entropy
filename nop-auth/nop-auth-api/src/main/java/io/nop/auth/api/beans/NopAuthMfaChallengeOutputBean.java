//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthMfaChallengeOutputBean {

    
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


        private String _createdBy;

    
        @PropMeta(propId=9)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=11)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _scene;

    
        @PropMeta(propId=13)
    
        public String getScene(){
            return _scene;
        }

        public void setScene(String value){
            this._scene = value;
        }


        private Long _verifiedAt;

    
        @PropMeta(propId=15)
    
        public Long getVerifiedAt(){
            return _verifiedAt;
        }

        public void setVerifiedAt(Long value){
            this._verifiedAt = value;
        }


    }
