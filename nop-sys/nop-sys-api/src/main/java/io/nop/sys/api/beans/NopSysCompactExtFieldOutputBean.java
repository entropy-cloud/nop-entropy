//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysCompactExtFieldOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _entityName;

    
        @PropMeta(propId=2)
    
        public String getEntityName(){
            return _entityName;
        }

        public void setEntityName(String value){
            this._entityName = value;
        }


        private String _propName;

    
        @PropMeta(propId=3)
    
        public String getPropName(){
            return _propName;
        }

        public void setPropName(String value){
            this._propName = value;
        }


        private Integer _position;

    
        @PropMeta(propId=4)
    
        public Integer getPosition(){
            return _position;
        }

        public void setPosition(Integer value){
            this._position = value;
        }


        private String _displayName;

    
        @PropMeta(propId=5)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _dictName;

    
        @PropMeta(propId=6)
    
        public String getDictName(){
            return _dictName;
        }

        public void setDictName(String value){
            this._dictName = value;
        }


        private String _defaultValue;

    
        @PropMeta(propId=7)
    
        public String getDefaultValue(){
            return _defaultValue;
        }

        public void setDefaultValue(String value){
            this._defaultValue = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=8)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
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


        private String _remark;

    
        @PropMeta(propId=14)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
