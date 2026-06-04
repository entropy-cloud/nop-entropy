//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiProjectInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _language;

    
        @PropMeta(propId=2)
    
        public String getLanguage(){
            return _language;
        }

        public void setLanguage(String value){
            this._language = value;
        }


        private String _name;

    
        @PropMeta(propId=3)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _prototypeId;

    
        @PropMeta(propId=4)
    
        public String getPrototypeId(){
            return _prototypeId;
        }

        public void setPrototypeId(String value){
            this._prototypeId = value;
        }


        private String _projectDir;

    
        @PropMeta(propId=5)
    
        public String getProjectDir(){
            return _projectDir;
        }

        public void setProjectDir(String value){
            this._projectDir = value;
        }


    }
