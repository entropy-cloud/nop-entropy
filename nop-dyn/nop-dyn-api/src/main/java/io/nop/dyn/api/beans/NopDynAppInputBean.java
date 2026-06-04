//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynAppInputBean extends CrudInputBase {

    
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


        private java.util.List<java.lang.String> _relatedModuleList_ids;

    
        public java.util.List<java.lang.String> getRelatedModuleList_ids(){
            return _relatedModuleList_ids;
        }

        public void setRelatedModuleList_ids(java.util.List<java.lang.String> value){
            this._relatedModuleList_ids = value;
        }


    }
