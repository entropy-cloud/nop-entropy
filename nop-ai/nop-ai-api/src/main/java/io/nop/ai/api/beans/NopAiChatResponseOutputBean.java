//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiChatResponseOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _requestId;

    
        @PropMeta(propId=2)
    
        public String getRequestId(){
            return _requestId;
        }

        public void setRequestId(String value){
            this._requestId = value;
        }


        private String _sessionId;

    
        @PropMeta(propId=3)
    
        public String getSessionId(){
            return _sessionId;
        }

        public void setSessionId(String value){
            this._sessionId = value;
        }


        private String _modelId;

    
        @PropMeta(propId=4)
    
        public String getModelId(){
            return _modelId;
        }

        public void setModelId(String value){
            this._modelId = value;
        }


        private String _aiProvider;

    
        @PropMeta(propId=5)
    
        public String getAiProvider(){
            return _aiProvider;
        }

        public void setAiProvider(String value){
            this._aiProvider = value;
        }


        private String _aiProvider_label;

    
        public String getAiProvider_label(){
            return _aiProvider_label;
        }

        public void setAiProvider_label(String value){
            this._aiProvider_label = value;
        }


        private String _aiModel;

    
        @PropMeta(propId=6)
    
        public String getAiModel(){
            return _aiModel;
        }

        public void setAiModel(String value){
            this._aiModel = value;
        }


        private String _responseContent;

    
        @PropMeta(propId=7)
    
        public String getResponseContent(){
            return _responseContent;
        }

        public void setResponseContent(String value){
            this._responseContent = value;
        }


        private java.sql.Timestamp _responseTimestamp;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getResponseTimestamp(){
            return _responseTimestamp;
        }

        public void setResponseTimestamp(java.sql.Timestamp value){
            this._responseTimestamp = value;
        }


        private Integer _promptTokens;

    
        @PropMeta(propId=9)
    
        public Integer getPromptTokens(){
            return _promptTokens;
        }

        public void setPromptTokens(Integer value){
            this._promptTokens = value;
        }


        private Integer _completionTokens;

    
        @PropMeta(propId=10)
    
        public Integer getCompletionTokens(){
            return _completionTokens;
        }

        public void setCompletionTokens(Integer value){
            this._completionTokens = value;
        }


        private Integer _responseDurationMs;

    
        @PropMeta(propId=11)
    
        public Integer getResponseDurationMs(){
            return _responseDurationMs;
        }

        public void setResponseDurationMs(Integer value){
            this._responseDurationMs = value;
        }


        private java.math.BigDecimal _correctnessScore;

    
        @PropMeta(propId=12)
    
        public java.math.BigDecimal getCorrectnessScore(){
            return _correctnessScore;
        }

        public void setCorrectnessScore(java.math.BigDecimal value){
            this._correctnessScore = value;
        }


        private java.math.BigDecimal _performanceScore;

    
        @PropMeta(propId=13)
    
        public java.math.BigDecimal getPerformanceScore(){
            return _performanceScore;
        }

        public void setPerformanceScore(java.math.BigDecimal value){
            this._performanceScore = value;
        }


        private java.math.BigDecimal _readabilityScore;

    
        @PropMeta(propId=14)
    
        public java.math.BigDecimal getReadabilityScore(){
            return _readabilityScore;
        }

        public void setReadabilityScore(java.math.BigDecimal value){
            this._readabilityScore = value;
        }


        private java.math.BigDecimal _complianceScore;

    
        @PropMeta(propId=15)
    
        public java.math.BigDecimal getComplianceScore(){
            return _complianceScore;
        }

        public void setComplianceScore(java.math.BigDecimal value){
            this._complianceScore = value;
        }


    }
