//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynModuleInputBean extends CrudInputBase {

    
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


        private Object _importFile;

    
        public Object getImportFile(){
            return _importFile;
        }

        public void setImportFile(Object value){
            this._importFile = value;
        }


    }
