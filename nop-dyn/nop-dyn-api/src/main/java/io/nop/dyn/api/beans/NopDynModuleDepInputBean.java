//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynModuleDepInputBean extends CrudInputBase {

    
        private String _moduleId;

    
        @PropMeta(propId=1)
    
        public String getModuleId(){
            return _moduleId;
        }

        public void setModuleId(String value){
            this._moduleId = value;
        }


        private String _depModuleId;

    
        @PropMeta(propId=2)
    
        public String getDepModuleId(){
            return _depModuleId;
        }

        public void setDepModuleId(String value){
            this._depModuleId = value;
        }


    }
