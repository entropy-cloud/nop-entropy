//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiModelOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _provider;

    
        @PropMeta(propId=2)
    
        public String getProvider(){
            return _provider;
        }

        public void setProvider(String value){
            this._provider = value;
        }


        private String _provider_label;

    
        public String getProvider_label(){
            return _provider_label;
        }

        public void setProvider_label(String value){
            this._provider_label = value;
        }


        private String _modelName;

    
        @PropMeta(propId=3)
    
        public String getModelName(){
            return _modelName;
        }

        public void setModelName(String value){
            this._modelName = value;
        }


        private String _baseUrl;

    
        @PropMeta(propId=4)
    
        public String getBaseUrl(){
            return _baseUrl;
        }

        public void setBaseUrl(String value){
            this._baseUrl = value;
        }


        private String _apiKey;

    
        @PropMeta(propId=5)
    
        public String getApiKey(){
            return _apiKey;
        }

        public void setApiKey(String value){
            this._apiKey = value;
        }


        private Integer _version;

    
        @PropMeta(propId=6)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=7)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=9)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private java.math.BigDecimal _inputPricePer1m;

    
        @PropMeta(propId=11)
    
        public java.math.BigDecimal getInputPricePer1m(){
            return _inputPricePer1m;
        }

        public void setInputPricePer1m(java.math.BigDecimal value){
            this._inputPricePer1m = value;
        }


        private java.math.BigDecimal _outputPricePer1m;

    
        @PropMeta(propId=12)
    
        public java.math.BigDecimal getOutputPricePer1m(){
            return _outputPricePer1m;
        }

        public void setOutputPricePer1m(java.math.BigDecimal value){
            this._outputPricePer1m = value;
        }


        private java.math.BigDecimal _reasoningPricePer1m;

    
        @PropMeta(propId=13)
    
        public java.math.BigDecimal getReasoningPricePer1m(){
            return _reasoningPricePer1m;
        }

        public void setReasoningPricePer1m(java.math.BigDecimal value){
            this._reasoningPricePer1m = value;
        }


        private java.math.BigDecimal _cacheReadPricePer1m;

    
        @PropMeta(propId=14)
    
        public java.math.BigDecimal getCacheReadPricePer1m(){
            return _cacheReadPricePer1m;
        }

        public void setCacheReadPricePer1m(java.math.BigDecimal value){
            this._cacheReadPricePer1m = value;
        }


        private java.math.BigDecimal _cacheWritePricePer1m;

    
        @PropMeta(propId=15)
    
        public java.math.BigDecimal getCacheWritePricePer1m(){
            return _cacheWritePricePer1m;
        }

        public void setCacheWritePricePer1m(java.math.BigDecimal value){
            this._cacheWritePricePer1m = value;
        }


        private String _currency;

    
        @PropMeta(propId=16)
    
        public String getCurrency(){
            return _currency;
        }

        public void setCurrency(String value){
            this._currency = value;
        }


    }
