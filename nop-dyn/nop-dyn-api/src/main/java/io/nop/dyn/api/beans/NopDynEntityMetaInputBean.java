//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynEntityMetaInputBean extends CrudInputBase {

    
        private String _entityMetaId;

    
        @PropMeta(propId=1)
    
        public String getEntityMetaId(){
            return _entityMetaId;
        }

        public void setEntityMetaId(String value){
            this._entityMetaId = value;
        }


        private String _moduleId;

    
        @PropMeta(propId=2)
    
        public String getModuleId(){
            return _moduleId;
        }

        public void setModuleId(String value){
            this._moduleId = value;
        }


        private String _entityName;

    
        @PropMeta(propId=3)
    
        public String getEntityName(){
            return _entityName;
        }

        public void setEntityName(String value){
            this._entityName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=4)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _tableName;

    
        @PropMeta(propId=5)
    
        public String getTableName(){
            return _tableName;
        }

        public void setTableName(String value){
            this._tableName = value;
        }


        private String _querySpace;

    
        @PropMeta(propId=6)
    
        public String getQuerySpace(){
            return _querySpace;
        }

        public void setQuerySpace(String value){
            this._querySpace = value;
        }


        private Integer _storeType;

    
        @PropMeta(propId=7)
    
        public Integer getStoreType(){
            return _storeType;
        }

        public void setStoreType(Integer value){
            this._storeType = value;
        }


        private String _tagsText;

    
        @PropMeta(propId=8)
    
        public String getTagsText(){
            return _tagsText;
        }

        public void setTagsText(String value){
            this._tagsText = value;
        }


        private Boolean _isExternal;

    
        @PropMeta(propId=9)
    
        public Boolean getIsExternal(){
            return _isExternal;
        }

        public void setIsExternal(Boolean value){
            this._isExternal = value;
        }


        private Integer _status;

    
        @PropMeta(propId=10)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _extConfig;

    
        @PropMeta(propId=11)
    
        public String getExtConfig(){
            return _extConfig;
        }

        public void setExtConfig(String value){
            this._extConfig = value;
        }


        private String _remark;

    
        @PropMeta(propId=17)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
