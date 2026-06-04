//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeFlowInputBean extends CrudInputBase {

    
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


        private String _name;

    
        @PropMeta(propId=3)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _entryPointId;

    
        @PropMeta(propId=4)
    
        public String getEntryPointId(){
            return _entryPointId;
        }

        public void setEntryPointId(String value){
            this._entryPointId = value;
        }


        private String _entryPointQualifiedName;

    
        @PropMeta(propId=5)
    
        public String getEntryPointQualifiedName(){
            return _entryPointQualifiedName;
        }

        public void setEntryPointQualifiedName(String value){
            this._entryPointQualifiedName = value;
        }


        private Integer _depth;

    
        @PropMeta(propId=6)
    
        public Integer getDepth(){
            return _depth;
        }

        public void setDepth(Integer value){
            this._depth = value;
        }


        private Integer _symbolCount;

    
        @PropMeta(propId=7)
    
        public Integer getSymbolCount(){
            return _symbolCount;
        }

        public void setSymbolCount(Integer value){
            this._symbolCount = value;
        }


        private Double _fileSpread;

    
        @PropMeta(propId=8)
    
        public Double getFileSpread(){
            return _fileSpread;
        }

        public void setFileSpread(Double value){
            this._fileSpread = value;
        }


        private Double _externalScore;

    
        @PropMeta(propId=9)
    
        public Double getExternalScore(){
            return _externalScore;
        }

        public void setExternalScore(Double value){
            this._externalScore = value;
        }


        private Double _securityScore;

    
        @PropMeta(propId=10)
    
        public Double getSecurityScore(){
            return _securityScore;
        }

        public void setSecurityScore(Double value){
            this._securityScore = value;
        }


        private Double _testGap;

    
        @PropMeta(propId=11)
    
        public Double getTestGap(){
            return _testGap;
        }

        public void setTestGap(Double value){
            this._testGap = value;
        }


        private Double _depthScore;

    
        @PropMeta(propId=12)
    
        public Double getDepthScore(){
            return _depthScore;
        }

        public void setDepthScore(Double value){
            this._depthScore = value;
        }


        private Double _overallScore;

    
        @PropMeta(propId=13)
    
        public Double getOverallScore(){
            return _overallScore;
        }

        public void setOverallScore(Double value){
            this._overallScore = value;
        }


        private String _status;

    
        @PropMeta(propId=14)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=16)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=17)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=18)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


    }
