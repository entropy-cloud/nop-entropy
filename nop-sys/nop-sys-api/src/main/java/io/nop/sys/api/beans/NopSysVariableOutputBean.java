//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysVariableOutputBean {

    
        private String _varName;

    
        @PropMeta(propId=1)
    
        public String getVarName(){
            return _varName;
        }

        public void setVarName(String value){
            this._varName = value;
        }


        private String _varValue;

    
        @PropMeta(propId=2)
    
        public String getVarValue(){
            return _varValue;
        }

        public void setVarValue(String value){
            this._varValue = value;
        }


        private String _stdDomain;

    
        @PropMeta(propId=3)
    
        public String getStdDomain(){
            return _stdDomain;
        }

        public void setStdDomain(String value){
            this._stdDomain = value;
        }


        private String _varType;

    
        @PropMeta(propId=4)
    
        public String getVarType(){
            return _varType;
        }

        public void setVarType(String value){
            this._varType = value;
        }


        private Long _version;

    
        @PropMeta(propId=5)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=6)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=8)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=10)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
