//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeDependencyOutputBean {

    
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


        private String _sourceFilePath;

    
        @PropMeta(propId=3)
    
        public String getSourceFilePath(){
            return _sourceFilePath;
        }

        public void setSourceFilePath(String value){
            this._sourceFilePath = value;
        }


        private String _targetFilePath;

    
        @PropMeta(propId=4)
    
        public String getTargetFilePath(){
            return _targetFilePath;
        }

        public void setTargetFilePath(String value){
            this._targetFilePath = value;
        }


        private String _importStatement;

    
        @PropMeta(propId=5)
    
        public String getImportStatement(){
            return _importStatement;
        }

        public void setImportStatement(String value){
            this._importStatement = value;
        }


        private Boolean _resolved;

    
        @PropMeta(propId=6)
    
        public Boolean getResolved(){
            return _resolved;
        }

        public void setResolved(Boolean value){
            this._resolved = value;
        }


        private String _dependencyKeyHash;

    
        @PropMeta(propId=7)
    
        public String getDependencyKeyHash(){
            return _dependencyKeyHash;
        }

        public void setDependencyKeyHash(String value){
            this._dependencyKeyHash = value;
        }


        private Map<String,Object> _index;

        public Map<String,Object> getIndex(){
            return _index;
        }

        public void setIndex(Map<String,Object> value){
            this._index = value;
        }


    }
