//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeAnnotationUsageInputBean extends CrudInputBase {

    
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


        private String _annotationTypeId;

    
        @PropMeta(propId=3)
    
        public String getAnnotationTypeId(){
            return _annotationTypeId;
        }

        public void setAnnotationTypeId(String value){
            this._annotationTypeId = value;
        }


        private String _annotatedSymbolId;

    
        @PropMeta(propId=4)
    
        public String getAnnotatedSymbolId(){
            return _annotatedSymbolId;
        }

        public void setAnnotatedSymbolId(String value){
            this._annotatedSymbolId = value;
        }


        private Integer _line;

    
        @PropMeta(propId=5)
    
        public Integer getLine(){
            return _line;
        }

        public void setLine(Integer value){
            this._line = value;
        }


        private Integer _column;

    
        @PropMeta(propId=6)
    
        public Integer getColumn(){
            return _column;
        }

        public void setColumn(Integer value){
            this._column = value;
        }


        private String _attributes;

    
        @PropMeta(propId=7)
    
        public String getAttributes(){
            return _attributes;
        }

        public void setAttributes(String value){
            this._attributes = value;
        }


    }
