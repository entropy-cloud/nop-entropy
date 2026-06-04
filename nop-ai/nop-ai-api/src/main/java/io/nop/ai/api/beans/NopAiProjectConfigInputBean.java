//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiProjectConfigInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _projectId;

    
        @PropMeta(propId=2)
    
        public String getProjectId(){
            return _projectId;
        }

        public void setProjectId(String value){
            this._projectId = value;
        }


        private String _configName;

    
        @PropMeta(propId=3)
    
        public String getConfigName(){
            return _configName;
        }

        public void setConfigName(String value){
            this._configName = value;
        }


        private String _configValue;

    
        @PropMeta(propId=4)
    
        public String getConfigValue(){
            return _configValue;
        }

        public void setConfigValue(String value){
            this._configValue = value;
        }


        private String _configType;

    
        @PropMeta(propId=5)
    
        public String getConfigType(){
            return _configType;
        }

        public void setConfigType(String value){
            this._configType = value;
        }


    }
