//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysExtFieldInputBean extends CrudInputBase {

    
        private String _entityName;

    
        @PropMeta(propId=1)
    
        public String getEntityName(){
            return _entityName;
        }

        public void setEntityName(String value){
            this._entityName = value;
        }


        private String _entityId;

    
        @PropMeta(propId=2)
    
        public String getEntityId(){
            return _entityId;
        }

        public void setEntityId(String value){
            this._entityId = value;
        }


        private String _fieldName;

    
        @PropMeta(propId=3)
    
        public String getFieldName(){
            return _fieldName;
        }

        public void setFieldName(String value){
            this._fieldName = value;
        }


        private Integer _fieldType;

    
        @PropMeta(propId=4)
    
        public Integer getFieldType(){
            return _fieldType;
        }

        public void setFieldType(Integer value){
            this._fieldType = value;
        }


        private Byte _decimalScale;

    
        @PropMeta(propId=5)
    
        public Byte getDecimalScale(){
            return _decimalScale;
        }

        public void setDecimalScale(Byte value){
            this._decimalScale = value;
        }


        private java.math.BigDecimal _decimalValue;

    
        @PropMeta(propId=6)
    
        public java.math.BigDecimal getDecimalValue(){
            return _decimalValue;
        }

        public void setDecimalValue(java.math.BigDecimal value){
            this._decimalValue = value;
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


        private String _stringValue;

    
        @PropMeta(propId=9)
    
        public String getStringValue(){
            return _stringValue;
        }

        public void setStringValue(String value){
            this._stringValue = value;
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
