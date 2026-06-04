//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiModelInputBean extends CrudInputBase {

    
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


    }
