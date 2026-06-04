//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynAppModuleInputBean extends CrudInputBase {

    
        private String _appId;

    
        @PropMeta(propId=1)
    
        public String getAppId(){
            return _appId;
        }

        public void setAppId(String value){
            this._appId = value;
        }


        private String _moduleId;

    
        @PropMeta(propId=2)
    
        public String getModuleId(){
            return _moduleId;
        }

        public void setModuleId(String value){
            this._moduleId = value;
        }


    }
