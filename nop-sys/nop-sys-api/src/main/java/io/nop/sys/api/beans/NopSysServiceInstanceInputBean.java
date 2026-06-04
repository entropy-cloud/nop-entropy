//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysServiceInstanceInputBean extends CrudInputBase {

    
        private String _instanceId;

    
        @PropMeta(propId=1)
    
        public String getInstanceId(){
            return _instanceId;
        }

        public void setInstanceId(String value){
            this._instanceId = value;
        }


        private String _serviceName;

    
        @PropMeta(propId=2)
    
        public String getServiceName(){
            return _serviceName;
        }

        public void setServiceName(String value){
            this._serviceName = value;
        }


        private String _clusterName;

    
        @PropMeta(propId=3)
    
        public String getClusterName(){
            return _clusterName;
        }

        public void setClusterName(String value){
            this._clusterName = value;
        }


        private String _groupName;

    
        @PropMeta(propId=4)
    
        public String getGroupName(){
            return _groupName;
        }

        public void setGroupName(String value){
            this._groupName = value;
        }


        private String _tagsText;

    
        @PropMeta(propId=5)
    
        public String getTagsText(){
            return _tagsText;
        }

        public void setTagsText(String value){
            this._tagsText = value;
        }


        private String _serverAddr;

    
        @PropMeta(propId=6)
    
        public String getServerAddr(){
            return _serverAddr;
        }

        public void setServerAddr(String value){
            this._serverAddr = value;
        }


        private Integer _serverPort;

    
        @PropMeta(propId=7)
    
        public Integer getServerPort(){
            return _serverPort;
        }

        public void setServerPort(Integer value){
            this._serverPort = value;
        }


        private Integer _weight;

    
        @PropMeta(propId=8)
    
        public Integer getWeight(){
            return _weight;
        }

        public void setWeight(Integer value){
            this._weight = value;
        }


        private String _metaData;

    
        @PropMeta(propId=9)
    
        public String getMetaData(){
            return _metaData;
        }

        public void setMetaData(String value){
            this._metaData = value;
        }


        private Boolean _isHealthy;

    
        @PropMeta(propId=10)
    
        public Boolean getIsHealthy(){
            return _isHealthy;
        }

        public void setIsHealthy(Boolean value){
            this._isHealthy = value;
        }


        private Boolean _isEnabled;

    
        @PropMeta(propId=11)
    
        public Boolean getIsEnabled(){
            return _isEnabled;
        }

        public void setIsEnabled(Boolean value){
            this._isEnabled = value;
        }


        private Boolean _isEphemeral;

    
        @PropMeta(propId=12)
    
        public Boolean getIsEphemeral(){
            return _isEphemeral;
        }

        public void setIsEphemeral(Boolean value){
            this._isEphemeral = value;
        }


    }
