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
    public class NopDynAppOutputBean {

    
        private String _appId;

    
        @PropMeta(propId=1)
    
        public String getAppId(){
            return _appId;
        }

        public void setAppId(String value){
            this._appId = value;
        }


        private String _appName;

    
        @PropMeta(propId=2)
    
        public String getAppName(){
            return _appName;
        }

        public void setAppName(String value){
            this._appName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=3)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private Integer _appVersion;

    
        @PropMeta(propId=4)
    
        public Integer getAppVersion(){
            return _appVersion;
        }

        public void setAppVersion(Integer value){
            this._appVersion = value;
        }


        private Integer _sortOrder;

    
        @PropMeta(propId=5)
    
        public Integer getSortOrder(){
            return _sortOrder;
        }

        public void setSortOrder(Integer value){
            this._sortOrder = value;
        }


        private Integer _status;

    
        @PropMeta(propId=6)
    
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

    
        @PropMeta(propId=7)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=8)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=10)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private java.util.List<java.lang.String> _relatedModuleList_ids;

    
        public java.util.List<java.lang.String> getRelatedModuleList_ids(){
            return _relatedModuleList_ids;
        }

        public void setRelatedModuleList_ids(java.util.List<java.lang.String> value){
            this._relatedModuleList_ids = value;
        }


        private String _relatedModuleList_label;

    
        public String getRelatedModuleList_label(){
            return _relatedModuleList_label;
        }

        public void setRelatedModuleList_label(String value){
            this._relatedModuleList_label = value;
        }


        private List<Map<String,Object>> _patchFiles;

        public List<Map<String,Object>> getPatchFiles(){
            return _patchFiles;
        }

        public void setPatchFiles(List<Map<String,Object>> value){
            this._patchFiles = value;
        }


        private List<Map<String,Object>> _moduleMappings;

        public List<Map<String,Object>> getModuleMappings(){
            return _moduleMappings;
        }

        public void setModuleMappings(List<Map<String,Object>> value){
            this._moduleMappings = value;
        }


        private List<Map<String,Object>> _relatedModuleList;

        public List<Map<String,Object>> getRelatedModuleList(){
            return _relatedModuleList;
        }

        public void setRelatedModuleList(List<Map<String,Object>> value){
            this._relatedModuleList = value;
        }


    }
