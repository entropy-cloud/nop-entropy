//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysObjTagInputBean extends CrudInputBase {

    
        private String _bizObjId;

    
        @PropMeta(propId=1)
    
        public String getBizObjId(){
            return _bizObjId;
        }

        public void setBizObjId(String value){
            this._bizObjId = value;
        }


        private String _bizObjName;

    
        @PropMeta(propId=2)
    
        public String getBizObjName(){
            return _bizObjName;
        }

        public void setBizObjName(String value){
            this._bizObjName = value;
        }


        private Long _tagId;

    
        @PropMeta(propId=3)
    
        public Long getTagId(){
            return _tagId;
        }

        public void setTagId(Long value){
            this._tagId = value;
        }


    }
