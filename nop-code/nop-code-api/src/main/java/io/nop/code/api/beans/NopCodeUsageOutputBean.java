//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeUsageOutputBean {

    
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


        private String _symbolId;

    
        @PropMeta(propId=3)
    
        public String getSymbolId(){
            return _symbolId;
        }

        public void setSymbolId(String value){
            this._symbolId = value;
        }


        private String _fileId;

    
        @PropMeta(propId=4)
    
        public String getFileId(){
            return _fileId;
        }

        public void setFileId(String value){
            this._fileId = value;
        }


        private String _kind;

    
        @PropMeta(propId=5)
    
        public String getKind(){
            return _kind;
        }

        public void setKind(String value){
            this._kind = value;
        }


        private String _kind_label;

    
        public String getKind_label(){
            return _kind_label;
        }

        public void setKind_label(String value){
            this._kind_label = value;
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


        private String _enclosingSymbolId;

    
        @PropMeta(propId=8)
    
        public String getEnclosingSymbolId(){
            return _enclosingSymbolId;
        }

        public void setEnclosingSymbolId(String value){
            this._enclosingSymbolId = value;
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


        private Map<String,Object> _symbol;

        public Map<String,Object> getSymbol(){
            return _symbol;
        }

        public void setSymbol(Map<String,Object> value){
            this._symbol = value;
        }


        private Map<String,Object> _file;

        public Map<String,Object> getFile(){
            return _file;
        }

        public void setFile(Map<String,Object> value){
            this._file = value;
        }


        private Map<String,Object> _enclosingSymbol;

        public Map<String,Object> getEnclosingSymbol(){
            return _enclosingSymbol;
        }

        public void setEnclosingSymbol(Map<String,Object> value){
            this._enclosingSymbol = value;
        }


    }
