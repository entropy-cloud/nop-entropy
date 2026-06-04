//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysLockOutputBean {

    
        private String _lockName;

    
        @PropMeta(propId=1)
    
        public String getLockName(){
            return _lockName;
        }

        public void setLockName(String value){
            this._lockName = value;
        }


        private String _lockGroup;

    
        @PropMeta(propId=2)
    
        public String getLockGroup(){
            return _lockGroup;
        }

        public void setLockGroup(String value){
            this._lockGroup = value;
        }


        private java.sql.Timestamp _lockTime;

    
        @PropMeta(propId=3)
    
        public java.sql.Timestamp getLockTime(){
            return _lockTime;
        }

        public void setLockTime(java.sql.Timestamp value){
            this._lockTime = value;
        }


        private java.sql.Timestamp _expireAt;

    
        @PropMeta(propId=4)
    
        public java.sql.Timestamp getExpireAt(){
            return _expireAt;
        }

        public void setExpireAt(java.sql.Timestamp value){
            this._expireAt = value;
        }


        private String _lockReason;

    
        @PropMeta(propId=5)
    
        public String getLockReason(){
            return _lockReason;
        }

        public void setLockReason(String value){
            this._lockReason = value;
        }


        private String _holderId;

    
        @PropMeta(propId=6)
    
        public String getHolderId(){
            return _holderId;
        }

        public void setHolderId(String value){
            this._holderId = value;
        }


        private String _holderAdder;

    
        @PropMeta(propId=7)
    
        public String getHolderAdder(){
            return _holderAdder;
        }

        public void setHolderAdder(String value){
            this._holderAdder = value;
        }


        private String _appId;

    
        @PropMeta(propId=8)
    
        public String getAppId(){
            return _appId;
        }

        public void setAppId(String value){
            this._appId = value;
        }


        private Long _version;

    
        @PropMeta(propId=9)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=10)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=12)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


    }
