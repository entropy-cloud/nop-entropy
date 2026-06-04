//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthSiteInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=13)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
