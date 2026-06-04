//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysDictOptionOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _dictId;

    
        @PropMeta(propId=2)
    
        public String getDictId(){
            return _dictId;
        }

        public void setDictId(String value){
            this._dictId = value;
        }


        private String _label;

    
        @PropMeta(propId=3)
    
        public String getLabel(){
            return _label;
        }

        public void setLabel(String value){
            this._label = value;
        }


        private String _value;

    
        @PropMeta(propId=4)
    
        public String getValue(){
            return _value;
        }

        public void setValue(String value){
            this._value = value;
        }


        private String _codeValue;

    
        @PropMeta(propId=5)
    
        public String getCodeValue(){
            return _codeValue;
        }

        public void setCodeValue(String value){
            this._codeValue = value;
        }


        private String _groupName;

    
        @PropMeta(propId=6)
    
        public String getGroupName(){
            return _groupName;
        }

        public void setGroupName(String value){
            this._groupName = value;
        }


        private Byte _isInternal;

    
        @PropMeta(propId=7)
    
        public Byte getIsInternal(){
            return _isInternal;
        }

        public void setIsInternal(Byte value){
            this._isInternal = value;
        }


        private Byte _isDeprecated;

    
        @PropMeta(propId=8)
    
        public Byte getIsDeprecated(){
            return _isDeprecated;
        }

        public void setIsDeprecated(Byte value){
            this._isDeprecated = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=9)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private Long _version;

    
        @PropMeta(propId=10)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=11)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=13)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=15)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _dict;

        public Map<String,Object> getDict(){
            return _dict;
        }

        public void setDict(Map<String,Object> value){
            this._dict = value;
        }


    }
