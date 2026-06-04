//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynPropMetaInputBean extends CrudInputBase {

    
        private String _propMetaId;

    
        @PropMeta(propId=1)
    
        public String getPropMetaId(){
            return _propMetaId;
        }

        public void setPropMetaId(String value){
            this._propMetaId = value;
        }


        private String _entityMetaId;

    
        @PropMeta(propId=2)
    
        public String getEntityMetaId(){
            return _entityMetaId;
        }

        public void setEntityMetaId(String value){
            this._entityMetaId = value;
        }


        private Boolean _isMandatory;

    
        @PropMeta(propId=3)
    
        public Boolean getIsMandatory(){
            return _isMandatory;
        }

        public void setIsMandatory(Boolean value){
            this._isMandatory = value;
        }


        private String _propName;

    
        @PropMeta(propId=4)
    
        public String getPropName(){
            return _propName;
        }

        public void setPropName(String value){
            this._propName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=5)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
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


        private Integer _propId;

    
        @PropMeta(propId=9)
    
        public Integer getPropId(){
            return _propId;
        }

        public void setPropId(Integer value){
            this._propId = value;
        }


        private String _uiShow;

    
        @PropMeta(propId=10)
    
        public String getUiShow(){
            return _uiShow;
        }

        public void setUiShow(String value){
            this._uiShow = value;
        }


        private String _uiControl;

    
        @PropMeta(propId=11)
    
        public String getUiControl(){
            return _uiControl;
        }

        public void setUiControl(String value){
            this._uiControl = value;
        }


        private String _domainId;

    
        @PropMeta(propId=12)
    
        public String getDomainId(){
            return _domainId;
        }

        public void setDomainId(String value){
            this._domainId = value;
        }


        private String _stdDomainName;

    
        @PropMeta(propId=13)
    
        public String getStdDomainName(){
            return _stdDomainName;
        }

        public void setStdDomainName(String value){
            this._stdDomainName = value;
        }


        private String _dictName;

    
        @PropMeta(propId=14)
    
        public String getDictName(){
            return _dictName;
        }

        public void setDictName(String value){
            this._dictName = value;
        }


        private String _dynPropMapping;

    
        @PropMeta(propId=15)
    
        public String getDynPropMapping(){
            return _dynPropMapping;
        }

        public void setDynPropMapping(String value){
            this._dynPropMapping = value;
        }


        private String _tagsText;

    
        @PropMeta(propId=16)
    
        public String getTagsText(){
            return _tagsText;
        }

        public void setTagsText(String value){
            this._tagsText = value;
        }


        private String _defaultValue;

    
        @PropMeta(propId=17)
    
        public String getDefaultValue(){
            return _defaultValue;
        }

        public void setDefaultValue(String value){
            this._defaultValue = value;
        }


        private String _extConfig;

    
        @PropMeta(propId=18)
    
        public String getExtConfig(){
            return _extConfig;
        }

        public void setExtConfig(String value){
            this._extConfig = value;
        }


        private Integer _status;

    
        @PropMeta(propId=19)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _remark;

    
        @PropMeta(propId=25)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
