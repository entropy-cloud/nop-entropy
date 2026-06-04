//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysSequenceOutputBean {

    
        private String _seqName;

    
        @PropMeta(propId=1)
    
        public String getSeqName(){
            return _seqName;
        }

        public void setSeqName(String value){
            this._seqName = value;
        }


        private String _seqType;

    
        @PropMeta(propId=2)
    
        public String getSeqType(){
            return _seqType;
        }

        public void setSeqType(String value){
            this._seqType = value;
        }


        private Byte _isUuid;

    
        @PropMeta(propId=3)
    
        public Byte getIsUuid(){
            return _isUuid;
        }

        public void setIsUuid(Byte value){
            this._isUuid = value;
        }


        private Long _nextValue;

    
        @PropMeta(propId=4)
    
        public Long getNextValue(){
            return _nextValue;
        }

        public void setNextValue(Long value){
            this._nextValue = value;
        }


        private Integer _stepSize;

    
        @PropMeta(propId=5)
    
        public Integer getStepSize(){
            return _stepSize;
        }

        public void setStepSize(Integer value){
            this._stepSize = value;
        }


        private Integer _cacheSize;

    
        @PropMeta(propId=6)
    
        public Integer getCacheSize(){
            return _cacheSize;
        }

        public void setCacheSize(Integer value){
            this._cacheSize = value;
        }


        private Long _maxValue;

    
        @PropMeta(propId=7)
    
        public Long getMaxValue(){
            return _maxValue;
        }

        public void setMaxValue(Long value){
            this._maxValue = value;
        }


        private Integer _resetType;

    
        @PropMeta(propId=8)
    
        public Integer getResetType(){
            return _resetType;
        }

        public void setResetType(Integer value){
            this._resetType = value;
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


    }
