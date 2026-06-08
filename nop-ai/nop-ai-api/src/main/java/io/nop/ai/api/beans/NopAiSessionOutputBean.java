//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiSessionOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _projectId;

    
        @PropMeta(propId=2)
    
        public String getProjectId(){
            return _projectId;
        }

        public void setProjectId(String value){
            this._projectId = value;
        }


        private String _parentSessionId;

    
        @PropMeta(propId=3)
    
        public String getParentSessionId(){
            return _parentSessionId;
        }

        public void setParentSessionId(String value){
            this._parentSessionId = value;
        }


        private String _agentName;

    
        @PropMeta(propId=4)
    
        public String getAgentName(){
            return _agentName;
        }

        public void setAgentName(String value){
            this._agentName = value;
        }


        private String _modelProvider;

    
        @PropMeta(propId=5)
    
        public String getModelProvider(){
            return _modelProvider;
        }

        public void setModelProvider(String value){
            this._modelProvider = value;
        }


        private String _modelProvider_label;

    
        public String getModelProvider_label(){
            return _modelProvider_label;
        }

        public void setModelProvider_label(String value){
            this._modelProvider_label = value;
        }


        private String _modelName;

    
        @PropMeta(propId=6)
    
        public String getModelName(){
            return _modelName;
        }

        public void setModelName(String value){
            this._modelName = value;
        }


        private String _slug;

    
        @PropMeta(propId=7)
    
        public String getSlug(){
            return _slug;
        }

        public void setSlug(String value){
            this._slug = value;
        }


        private String _title;

    
        @PropMeta(propId=8)
    
        public String getTitle(){
            return _title;
        }

        public void setTitle(String value){
            this._title = value;
        }


        private String _directory;

    
        @PropMeta(propId=9)
    
        public String getDirectory(){
            return _directory;
        }

        public void setDirectory(String value){
            this._directory = value;
        }


        private Integer _status;

    
        @PropMeta(propId=10)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _status_label;

    
        public String getStatus_label(){
            return _status_label;
        }

        public void setStatus_label(String value){
            this._status_label = value;
        }


        private Integer _version;

    
        @PropMeta(propId=11)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private java.math.BigDecimal _cost;

    
        @PropMeta(propId=12)
    
        public java.math.BigDecimal getCost(){
            return _cost;
        }

        public void setCost(java.math.BigDecimal value){
            this._cost = value;
        }


        private Integer _tokensInput;

    
        @PropMeta(propId=13)
    
        public Integer getTokensInput(){
            return _tokensInput;
        }

        public void setTokensInput(Integer value){
            this._tokensInput = value;
        }


        private Integer _tokensOutput;

    
        @PropMeta(propId=14)
    
        public Integer getTokensOutput(){
            return _tokensOutput;
        }

        public void setTokensOutput(Integer value){
            this._tokensOutput = value;
        }


        private Integer _tokensReasoning;

    
        @PropMeta(propId=15)
    
        public Integer getTokensReasoning(){
            return _tokensReasoning;
        }

        public void setTokensReasoning(Integer value){
            this._tokensReasoning = value;
        }


        private Integer _tokensCacheRead;

    
        @PropMeta(propId=16)
    
        public Integer getTokensCacheRead(){
            return _tokensCacheRead;
        }

        public void setTokensCacheRead(Integer value){
            this._tokensCacheRead = value;
        }


        private Integer _tokensCacheWrite;

    
        @PropMeta(propId=17)
    
        public Integer getTokensCacheWrite(){
            return _tokensCacheWrite;
        }

        public void setTokensCacheWrite(Integer value){
            this._tokensCacheWrite = value;
        }


        private Long _totalBytes;

    
        @PropMeta(propId=18)
    
        public Long getTotalBytes(){
            return _totalBytes;
        }

        public void setTotalBytes(Long value){
            this._totalBytes = value;
        }


        private String _contextMetadata;

    
        @PropMeta(propId=19)
    
        public String getContextMetadata(){
            return _contextMetadata;
        }

        public void setContextMetadata(String value){
            this._contextMetadata = value;
        }


        private String _metadata;

    
        @PropMeta(propId=20)
    
        public String getMetadata(){
            return _metadata;
        }

        public void setMetadata(String value){
            this._metadata = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=21)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=22)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=23)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=24)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private java.sql.Timestamp _compactedAt;

    
        @PropMeta(propId=25)
    
        public java.sql.Timestamp getCompactedAt(){
            return _compactedAt;
        }

        public void setCompactedAt(java.sql.Timestamp value){
            this._compactedAt = value;
        }


        private java.sql.Timestamp _archivedAt;

    
        @PropMeta(propId=26)
    
        public java.sql.Timestamp getArchivedAt(){
            return _archivedAt;
        }

        public void setArchivedAt(java.sql.Timestamp value){
            this._archivedAt = value;
        }


    }
