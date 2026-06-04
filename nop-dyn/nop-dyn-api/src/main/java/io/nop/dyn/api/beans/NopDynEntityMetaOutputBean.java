//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynEntityMetaOutputBean {

    
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


        private String _storeType_label;

    
        public String getStoreType_label(){
            return _storeType_label;
        }

        public void setStoreType_label(String value){
            this._storeType_label = value;
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


        private String _status_label;

    
        public String getStatus_label(){
            return _status_label;
        }

        public void setStatus_label(String value){
            this._status_label = value;
        }


        private Object _mainPagePath;

    
        public Object getMainPagePath(){
            return _mainPagePath;
        }

        public void setMainPagePath(Object value){
            this._mainPagePath = value;
        }


        private String _extConfig;

    
        @PropMeta(propId=11)
    
        public String getExtConfig(){
            return _extConfig;
        }

        public void setExtConfig(String value){
            this._extConfig = value;
        }


        private Integer _version;

    
        @PropMeta(propId=12)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=13)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=15)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=16)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=17)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _module;

        public Map<String,Object> getModule(){
            return _module;
        }

        public void setModule(Map<String,Object> value){
            this._module = value;
        }


        private List<Map<String,Object>> _propMetas;

        public List<Map<String,Object>> getPropMetas(){
            return _propMetas;
        }

        public void setPropMetas(List<Map<String,Object>> value){
            this._propMetas = value;
        }


        private List<Map<String,Object>> _relationMetasForEntity;

        public List<Map<String,Object>> getRelationMetasForEntity(){
            return _relationMetasForEntity;
        }

        public void setRelationMetasForEntity(List<Map<String,Object>> value){
            this._relationMetasForEntity = value;
        }


        private List<Map<String,Object>> _relationMetasForRefEntity;

        public List<Map<String,Object>> getRelationMetasForRefEntity(){
            return _relationMetasForRefEntity;
        }

        public void setRelationMetasForRefEntity(List<Map<String,Object>> value){
            this._relationMetasForRefEntity = value;
        }


        private List<Map<String,Object>> _functionMetas;

        public List<Map<String,Object>> getFunctionMetas(){
            return _functionMetas;
        }

        public void setFunctionMetas(List<Map<String,Object>> value){
            this._functionMetas = value;
        }


    }
