//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysTagInputBean extends CrudInputBase {

    
        private Long _sid;

    
        @PropMeta(propId=1)
    
        public Long getSid(){
            return _sid;
        }

        public void setSid(Long value){
            this._sid = value;
        }


        private String _name;

    
        @PropMeta(propId=2)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _description;

    
        @PropMeta(propId=3)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


    }
