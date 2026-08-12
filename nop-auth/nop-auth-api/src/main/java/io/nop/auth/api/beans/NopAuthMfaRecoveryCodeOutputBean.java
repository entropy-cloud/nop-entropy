//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthMfaRecoveryCodeOutputBean {

    
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


        private Byte _used;

    
        @PropMeta(propId=4)
    
        public Byte getUsed(){
            return _used;
        }

        public void setUsed(Byte value){
            this._used = value;
        }


        private java.sql.Timestamp _usedAt;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getUsedAt(){
            return _usedAt;
        }

        public void setUsedAt(java.sql.Timestamp value){
            this._usedAt = value;
        }


        private java.sql.Timestamp _expireAt;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(java.sql.Timestamp value){
            this._expireAt = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=7)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private Integer _version;

    
        @PropMeta(propId=8)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
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


        private Map<String,Object> _user;

        public Map<String,Object> getUser(){
            return _user;
        }

        public void setUser(Map<String,Object> value){
            this._user = value;
        }


        private Map<String,Object> _setting;

        public Map<String,Object> getSetting(){
            return _setting;
        }

        public void setSetting(Map<String,Object> value){
            this._setting = value;
        }


    }
