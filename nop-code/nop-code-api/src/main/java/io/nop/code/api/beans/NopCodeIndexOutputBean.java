//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeIndexOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _name;

    
        @PropMeta(propId=2)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _rootPath;

    
        @PropMeta(propId=3)
    
        public String getRootPath(){
            return _rootPath;
        }

        public void setRootPath(String value){
            this._rootPath = value;
        }


        private String _language;

    
        @PropMeta(propId=4)
    
        public String getLanguage(){
            return _language;
        }

        public void setLanguage(String value){
            this._language = value;
        }


        private Integer _symbolCount;

    
        @PropMeta(propId=5)
    
        public Integer getSymbolCount(){
            return _symbolCount;
        }

        public void setSymbolCount(Integer value){
            this._symbolCount = value;
        }


        private Integer _fileCount;

    
        @PropMeta(propId=6)
    
        public Integer getFileCount(){
            return _fileCount;
        }

        public void setFileCount(Integer value){
            this._fileCount = value;
        }


        private String _status;

    
        @PropMeta(propId=7)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private String _status_label;

    
        public String getStatus_label(){
            return _status_label;
        }

        public void setStatus_label(String value){
            this._status_label = value;
        }


        private Long _lastIndexed;

    
        @PropMeta(propId=8)
    
        public Long getLastIndexed(){
            return _lastIndexed;
        }

        public void setLastIndexed(Long value){
            this._lastIndexed = value;
        }


        private Integer _indexVersion;

    
        @PropMeta(propId=9)
    
        public Integer getIndexVersion(){
            return _indexVersion;
        }

        public void setIndexVersion(Integer value){
            this._indexVersion = value;
        }


    }
