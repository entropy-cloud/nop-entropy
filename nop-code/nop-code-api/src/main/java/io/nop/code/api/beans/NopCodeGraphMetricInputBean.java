//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeGraphMetricInputBean extends CrudInputBase {

    
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


        private String _metricType;

    
        @PropMeta(propId=3)
    
        public String getMetricType(){
            return _metricType;
        }

        public void setMetricType(String value){
            this._metricType = value;
        }


        private String _symbolId;

    
        @PropMeta(propId=4)
    
        public String getSymbolId(){
            return _symbolId;
        }

        public void setSymbolId(String value){
            this._symbolId = value;
        }


        private Integer _communityId;

    
        @PropMeta(propId=5)
    
        public Integer getCommunityId(){
            return _communityId;
        }

        public void setCommunityId(Integer value){
            this._communityId = value;
        }


        private Double _score;

    
        @PropMeta(propId=6)
    
        public Double getScore(){
            return _score;
        }

        public void setScore(Double value){
            this._score = value;
        }


        private Integer _rankNo;

    
        @PropMeta(propId=7)
    
        public Integer getRankNo(){
            return _rankNo;
        }

        public void setRankNo(Integer value){
            this._rankNo = value;
        }


        private String _entryPointType;

    
        @PropMeta(propId=8)
    
        public String getEntryPointType(){
            return _entryPointType;
        }

        public void setEntryPointType(String value){
            this._entryPointType = value;
        }


        private java.sql.Timestamp _computedAt;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getComputedAt(){
            return _computedAt;
        }

        public void setComputedAt(java.sql.Timestamp value){
            this._computedAt = value;
        }


        private String _extData;

    
        @PropMeta(propId=10)
    
        public String getExtData(){
            return _extData;
        }

        public void setExtData(String value){
            this._extData = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


    }
