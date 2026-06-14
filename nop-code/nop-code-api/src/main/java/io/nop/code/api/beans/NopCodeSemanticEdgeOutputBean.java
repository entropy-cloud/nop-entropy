//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeSemanticEdgeOutputBean {

    
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


        private String _sourceSymbolId;

    
        @PropMeta(propId=3)
    
        public String getSourceSymbolId(){
            return _sourceSymbolId;
        }

        public void setSourceSymbolId(String value){
            this._sourceSymbolId = value;
        }


        private String _targetSymbolId;

    
        @PropMeta(propId=4)
    
        public String getTargetSymbolId(){
            return _targetSymbolId;
        }

        public void setTargetSymbolId(String value){
            this._targetSymbolId = value;
        }


        private Boolean _directed;

    
        @PropMeta(propId=5)
    
        public Boolean getDirected(){
            return _directed;
        }

        public void setDirected(Boolean value){
            this._directed = value;
        }


        private String _relationType;

    
        @PropMeta(propId=6)
    
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


        private Integer _confidence;

    
        @PropMeta(propId=7)
    
        public Integer getConfidence(){
            return _confidence;
        }

        public void setConfidence(Integer value){
            this._confidence = value;
        }


        private Double _confidenceScore;

    
        @PropMeta(propId=8)
    
        public Double getConfidenceScore(){
            return _confidenceScore;
        }

        public void setConfidenceScore(Double value){
            this._confidenceScore = value;
        }


        private String _rationale;

    
        @PropMeta(propId=9)
    
        public String getRationale(){
            return _rationale;
        }

        public void setRationale(String value){
            this._rationale = value;
        }


        private String _extractorId;

    
        @PropMeta(propId=10)
    
        public String getExtractorId(){
            return _extractorId;
        }

        public void setExtractorId(String value){
            this._extractorId = value;
        }


        private String _extData;

    
        @PropMeta(propId=11)
    
        public String getExtData(){
            return _extData;
        }

        public void setExtData(String value){
            this._extData = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=12)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _provenance;

    
        @PropMeta(propId=15)
    
        public String getProvenance(){
            return _provenance;
        }

        public void setProvenance(String value){
            this._provenance = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=16)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=17)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private Map<String,Object> _index;

        public Map<String,Object> getIndex(){
            return _index;
        }

        public void setIndex(Map<String,Object> value){
            this._index = value;
        }


        private Map<String,Object> _sourceSymbol;

        public Map<String,Object> getSourceSymbol(){
            return _sourceSymbol;
        }

        public void setSourceSymbol(Map<String,Object> value){
            this._sourceSymbol = value;
        }


        private Map<String,Object> _targetSymbol;

        public Map<String,Object> getTargetSymbol(){
            return _targetSymbol;
        }

        public void setTargetSymbol(Map<String,Object> value){
            this._targetSymbol = value;
        }


    }
