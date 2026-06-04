//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthSiteOutputBean {

    
        private String _siteId;

    
        @PropMeta(propId=1)
    
        public String getSiteId(){
            return _siteId;
        }

        public void setSiteId(String value){
            this._siteId = value;
        }


        private String _displayName;

    
        @PropMeta(propId=2)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private Integer _orderNo;

    
        @PropMeta(propId=3)
    
        public Integer getOrderNo(){
            return _orderNo;
        }

        public void setOrderNo(Integer value){
            this._orderNo = value;
        }


        private String _url;

    
        @PropMeta(propId=4)
    
        public String getUrl(){
            return _url;
        }

        public void setUrl(String value){
            this._url = value;
        }


        private Integer _status;

    
        @PropMeta(propId=5)
    
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


        private String _extConfig;

    
        @PropMeta(propId=6)
    
        public String getExtConfig(){
            return _extConfig;
        }

        public void setExtConfig(String value){
            this._extConfig = value;
        }


        private String _configVersion;

    
        @PropMeta(propId=7)
    
        public String getConfigVersion(){
            return _configVersion;
        }

        public void setConfigVersion(String value){
            this._configVersion = value;
        }


        private Integer _version;

    
        @PropMeta(propId=8)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=9)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=11)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=13)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<Map<String,Object>> _resources;

        public List<Map<String,Object>> getResources(){
            return _resources;
        }

        public void setResources(List<Map<String,Object>> value){
            this._resources = value;
        }


        private Map<String,Object> _resourcesConnection;

        public Map<String,Object> getResourcesConnection(){
            return _resourcesConnection;
        }

        public void setResourcesConnection(Map<String,Object> value){
            this._resourcesConnection = value;
        }


        private Map<String,Object> _resourcesList;

        public Map<String,Object> getResourcesList(){
            return _resourcesList;
        }

        public void setResourcesList(Map<String,Object> value){
            this._resourcesList = value;
        }


    }
