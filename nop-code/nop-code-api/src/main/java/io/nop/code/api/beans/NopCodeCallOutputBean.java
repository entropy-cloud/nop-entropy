//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeCallOutputBean {

    
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


        private String _callerId;

    
        @PropMeta(propId=3)
    
        public String getCallerId(){
            return _callerId;
        }

        public void setCallerId(String value){
            this._callerId = value;
        }


        private String _calleeId;

    
        @PropMeta(propId=4)
    
        public String getCalleeId(){
            return _calleeId;
        }

        public void setCalleeId(String value){
            this._calleeId = value;
        }


        private String _fileId;

    
        @PropMeta(propId=5)
    
        public String getFileId(){
            return _fileId;
        }

        public void setFileId(String value){
            this._fileId = value;
        }


        private Integer _line;

    
        @PropMeta(propId=6)
    
        public Integer getLine(){
            return _line;
        }

        public void setLine(Integer value){
            this._line = value;
        }


        private Integer _column;

    
        @PropMeta(propId=7)
    
        public Integer getColumn(){
            return _column;
        }

        public void setColumn(Integer value){
            this._column = value;
        }


        private String _callType;

    
        @PropMeta(propId=8)
    
        public String getCallType(){
            return _callType;
        }

        public void setCallType(String value){
            this._callType = value;
        }


        private String _callType_label;

    
        public String getCallType_label(){
            return _callType_label;
        }

        public void setCallType_label(String value){
            this._callType_label = value;
        }


        private String _context;

    
        @PropMeta(propId=9)
    
        public String getContext(){
            return _context;
        }

        public void setContext(String value){
            this._context = value;
        }


        private String _provenance;

    
        @PropMeta(propId=10)
    
        public String getProvenance(){
            return _provenance;
        }

        public void setProvenance(String value){
            this._provenance = value;
        }


        private String _metadata;

    
        @PropMeta(propId=11)
    
        public String getMetadata(){
            return _metadata;
        }

        public void setMetadata(String value){
            this._metadata = value;
        }


        private Map<String,Object> _index;

        public Map<String,Object> getIndex(){
            return _index;
        }

        public void setIndex(Map<String,Object> value){
            this._index = value;
        }


        private Map<String,Object> _caller;

        public Map<String,Object> getCaller(){
            return _caller;
        }

        public void setCaller(Map<String,Object> value){
            this._caller = value;
        }


        private Map<String,Object> _callee;

        public Map<String,Object> getCallee(){
            return _callee;
        }

        public void setCallee(Map<String,Object> value){
            this._callee = value;
        }


        private Map<String,Object> _file;

        public Map<String,Object> getFile(){
            return _file;
        }

        public void setFile(Map<String,Object> value){
            this._file = value;
        }


    }
