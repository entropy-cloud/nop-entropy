//__XGEN_FORCE_OVERRIDE__
    package io.nop.batch.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopBatchTaskVarOutputBean {

    
        private String _batchTaskId;

    
        @PropMeta(propId=1)
    
        public String getBatchTaskId(){
            return _batchTaskId;
        }

        public void setBatchTaskId(String value){
            this._batchTaskId = value;
        }


        private String _fieldName;

    
        @PropMeta(propId=2)
    
        public String getFieldName(){
            return _fieldName;
        }

        public void setFieldName(String value){
            this._fieldName = value;
        }


        private Integer _fieldType;

    
        @PropMeta(propId=3)
    
        public Integer getFieldType(){
            return _fieldType;
        }

        public void setFieldType(Integer value){
            this._fieldType = value;
        }


        private String _stringValue;

    
        @PropMeta(propId=4)
    
        public String getStringValue(){
            return _stringValue;
        }

        public void setStringValue(String value){
            this._stringValue = value;
        }


        private java.math.BigDecimal _decimalValue;

    
        @PropMeta(propId=5)
    
        public java.math.BigDecimal getDecimalValue(){
            return _decimalValue;
        }

        public void setDecimalValue(java.math.BigDecimal value){
            this._decimalValue = value;
        }


        private Long _longValue;

    
        @PropMeta(propId=6)
    
        public Long getLongValue(){
            return _longValue;
        }

        public void setLongValue(Long value){
            this._longValue = value;
        }


        private java.time.LocalDate _dateValue;

    
        @PropMeta(propId=7)
    
        public java.time.LocalDate getDateValue(){
            return _dateValue;
        }

        public void setDateValue(java.time.LocalDate value){
            this._dateValue = value;
        }


        private java.sql.Timestamp _timestampValue;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getTimestampValue(){
            return _timestampValue;
        }

        public void setTimestampValue(java.sql.Timestamp value){
            this._timestampValue = value;
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


        private Map<String,Object> _task;

        public Map<String,Object> getTask(){
            return _task;
        }

        public void setTask(Map<String,Object> value){
            this._task = value;
        }


    }
