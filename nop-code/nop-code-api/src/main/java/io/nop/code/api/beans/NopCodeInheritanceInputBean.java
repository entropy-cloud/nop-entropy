//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeInheritanceInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _indexId;

    
        @PropMeta(propId=2)
    
        public String getIndexId(){
            return _indexId;
        }

        public void setIndexId(String value){
            this._indexId = value;
        }


        private String _subTypeId;

    
        @PropMeta(propId=3)
    
        public String getSubTypeId(){
            return _subTypeId;
        }

        public void setSubTypeId(String value){
            this._subTypeId = value;
        }


        private String _superTypeId;

    
        @PropMeta(propId=4)
    
        public String getSuperTypeId(){
            return _superTypeId;
        }

        public void setSuperTypeId(String value){
            this._superTypeId = value;
        }


        private String _relationType;

    
        @PropMeta(propId=5)
    
        public String getRelationType(){
            return _relationType;
        }

        public void setRelationType(String value){
            this._relationType = value;
        }


    }
