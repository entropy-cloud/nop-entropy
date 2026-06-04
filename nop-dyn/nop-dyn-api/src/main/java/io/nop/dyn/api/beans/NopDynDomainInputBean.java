//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynDomainInputBean extends CrudInputBase {

    
        private String _domainId;

    
        @PropMeta(propId=1)
    
        public String getDomainId(){
            return _domainId;
        }

        public void setDomainId(String value){
            this._domainId = value;
        }


        private String _moduleId;

    
        @PropMeta(propId=2)
    
        public String getModuleId(){
            return _moduleId;
        }

        public void setModuleId(String value){
            this._moduleId = value;
        }


        private String _domainName;

    
        @PropMeta(propId=3)
    
        public String getDomainName(){
            return _domainName;
        }

        public void setDomainName(String value){
            this._domainName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=4)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _stdDomainName;

    
        @PropMeta(propId=5)
    
        public String getStdDomainName(){
            return _stdDomainName;
        }

        public void setStdDomainName(String value){
            this._stdDomainName = value;
        }


        private String _stdSqlType;

    
        @PropMeta(propId=6)
    
        public String getStdSqlType(){
            return _stdSqlType;
        }

        public void setStdSqlType(String value){
            this._stdSqlType = value;
        }


        private Integer _precision;

    
        @PropMeta(propId=7)
    
        public Integer getPrecision(){
            return _precision;
        }

        public void setPrecision(Integer value){
            this._precision = value;
        }


        private Integer _scale;

    
        @PropMeta(propId=8)
    
        public Integer getScale(){
            return _scale;
        }

        public void setScale(Integer value){
            this._scale = value;
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
