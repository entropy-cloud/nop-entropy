//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysVariableInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=10)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
