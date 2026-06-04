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
    public class NopDynModuleOutputBean {

    
        private String _moduleId;

    
        @PropMeta(propId=1)
    
        public String getModuleId(){
            return _moduleId;
        }

        public void setModuleId(String value){
            this._moduleId = value;
        }


        private String _moduleName;

    
        @PropMeta(propId=2)
    
        public String getModuleName(){
            return _moduleName;
        }

        public void setModuleName(String value){
            this._moduleName = value;
        }


        private Integer _moduleVersion;

    
        @PropMeta(propId=3)
    
        public Integer getModuleVersion(){
            return _moduleVersion;
        }

        public void setModuleVersion(Integer value){
            this._moduleVersion = value;
        }


        private String _displayName;

    
        @PropMeta(propId=4)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _baseModuleId;

    
        @PropMeta(propId=5)
    
        public String getBaseModuleId(){
            return _baseModuleId;
        }

        public void setBaseModuleId(String value){
            this._baseModuleId = value;
        }


        private String _basePackageName;

    
        @PropMeta(propId=6)
    
        public String getBasePackageName(){
            return _basePackageName;
        }

        public void setBasePackageName(String value){
            this._basePackageName = value;
        }


        private String _entityPackageName;

    
        @PropMeta(propId=7)
    
        public String getEntityPackageName(){
            return _entityPackageName;
        }

        public void setEntityPackageName(String value){
            this._entityPackageName = value;
        }


        private String _mavenGroupId;

    
        @PropMeta(propId=8)
    
        public String getMavenGroupId(){
            return _mavenGroupId;
        }

        public void setMavenGroupId(String value){
            this._mavenGroupId = value;
        }


        private Integer _status;

    
        @PropMeta(propId=9)
    
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


        private Integer _version;

    
        @PropMeta(propId=10)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
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


        private java.util.List<java.lang.String> _relatedAppList_ids;

    
        public java.util.List<java.lang.String> getRelatedAppList_ids(){
            return _relatedAppList_ids;
        }

        public void setRelatedAppList_ids(java.util.List<java.lang.String> value){
            this._relatedAppList_ids = value;
        }


        private String _relatedAppList_label;

    
        public String getRelatedAppList_label(){
            return _relatedAppList_label;
        }

        public void setRelatedAppList_label(String value){
            this._relatedAppList_label = value;
        }


        private Map<String,Object> _baseModule;

        public Map<String,Object> getBaseModule(){
            return _baseModule;
        }

        public void setBaseModule(Map<String,Object> value){
            this._baseModule = value;
        }


        private List<Map<String,Object>> _derivedModules;

        public List<Map<String,Object>> getDerivedModules(){
            return _derivedModules;
        }

        public void setDerivedModules(List<Map<String,Object>> value){
            this._derivedModules = value;
        }


        private List<Map<String,Object>> _appMappings;

        public List<Map<String,Object>> getAppMappings(){
            return _appMappings;
        }

        public void setAppMappings(List<Map<String,Object>> value){
            this._appMappings = value;
        }


        private List<Map<String,Object>> _sqls;

        public List<Map<String,Object>> getSqls(){
            return _sqls;
        }

        public void setSqls(List<Map<String,Object>> value){
            this._sqls = value;
        }


        private List<Map<String,Object>> _files;

        public List<Map<String,Object>> getFiles(){
            return _files;
        }

        public void setFiles(List<Map<String,Object>> value){
            this._files = value;
        }


        private List<Map<String,Object>> _pages;

        public List<Map<String,Object>> getPages(){
            return _pages;
        }

        public void setPages(List<Map<String,Object>> value){
            this._pages = value;
        }


        private List<Map<String,Object>> _entityMetas;

        public List<Map<String,Object>> getEntityMetas(){
            return _entityMetas;
        }

        public void setEntityMetas(List<Map<String,Object>> value){
            this._entityMetas = value;
        }


        private List<Map<String,Object>> _domains;

        public List<Map<String,Object>> getDomains(){
            return _domains;
        }

        public void setDomains(List<Map<String,Object>> value){
            this._domains = value;
        }


        private List<Map<String,Object>> _relatedAppList;

        public List<Map<String,Object>> getRelatedAppList(){
            return _relatedAppList;
        }

        public void setRelatedAppList(List<Map<String,Object>> value){
            this._relatedAppList = value;
        }


    }
