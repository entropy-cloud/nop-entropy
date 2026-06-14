//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeInheritanceOutputBean {

    
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


        private String _relationType_label;

    
        public String getRelationType_label(){
            return _relationType_label;
        }

        public void setRelationType_label(String value){
            this._relationType_label = value;
        }


        private String _provenance;

    
        @PropMeta(propId=6)
    
        public String getProvenance(){
            return _provenance;
        }

        public void setProvenance(String value){
            this._provenance = value;
        }


        private Map<String,Object> _index;

        public Map<String,Object> getIndex(){
            return _index;
        }

        public void setIndex(Map<String,Object> value){
            this._index = value;
        }


        private Map<String,Object> _subType;

        public Map<String,Object> getSubType(){
            return _subType;
        }

        public void setSubType(Map<String,Object> value){
            this._subType = value;
        }


        private Map<String,Object> _superType;

        public Map<String,Object> getSuperType(){
            return _superType;
        }

        public void setSuperType(Map<String,Object> value){
            this._superType = value;
        }


    }
