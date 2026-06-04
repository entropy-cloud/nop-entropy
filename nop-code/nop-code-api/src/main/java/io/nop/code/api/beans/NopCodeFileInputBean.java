//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeFileInputBean extends CrudInputBase {

    
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


        private String _filePath;

    
        @PropMeta(propId=3)
    
        public String getFilePath(){
            return _filePath;
        }

        public void setFilePath(String value){
            this._filePath = value;
        }


        private String _packageName;

    
        @PropMeta(propId=4)
    
        public String getPackageName(){
            return _packageName;
        }

        public void setPackageName(String value){
            this._packageName = value;
        }


        private String _language;

    
        @PropMeta(propId=5)
    
        public String getLanguage(){
            return _language;
        }

        public void setLanguage(String value){
            this._language = value;
        }


        private Integer _lineCount;

    
        @PropMeta(propId=6)
    
        public Integer getLineCount(){
            return _lineCount;
        }

        public void setLineCount(Integer value){
            this._lineCount = value;
        }


        private String _imports;

    
        @PropMeta(propId=7)
    
        public String getImports(){
            return _imports;
        }

        public void setImports(String value){
            this._imports = value;
        }


        private String _sourceCode;

    
        @PropMeta(propId=8)
    
        public String getSourceCode(){
            return _sourceCode;
        }

        public void setSourceCode(String value){
            this._sourceCode = value;
        }


        private String _fileHash;

    
        @PropMeta(propId=9)
    
        public String getFileHash(){
            return _fileHash;
        }

        public void setFileHash(String value){
            this._fileHash = value;
        }


        private Long _lastModified;

    
        @PropMeta(propId=10)
    
        public Long getLastModified(){
            return _lastModified;
        }

        public void setLastModified(Long value){
            this._lastModified = value;
        }


        private Long _fileSize;

    
        @PropMeta(propId=11)
    
        public Long getFileSize(){
            return _fileSize;
        }

        public void setFileSize(Long value){
            this._fileSize = value;
        }


    }
