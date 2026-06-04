//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiChatRequestOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _templateId;

    
        @PropMeta(propId=2)
    
        public String getTemplateId(){
            return _templateId;
        }

        public void setTemplateId(String value){
            this._templateId = value;
        }


        private String _sessionId;

    
        @PropMeta(propId=3)
    
        public String getSessionId(){
            return _sessionId;
        }

        public void setSessionId(String value){
            this._sessionId = value;
        }


        private String _systemPrompt;

    
        @PropMeta(propId=4)
    
        public String getSystemPrompt(){
            return _systemPrompt;
        }

        public void setSystemPrompt(String value){
            this._systemPrompt = value;
        }


        private String _userPrompt;

    
        @PropMeta(propId=5)
    
        public String getUserPrompt(){
            return _userPrompt;
        }

        public void setUserPrompt(String value){
            this._userPrompt = value;
        }


        private String _messageType;

    
        @PropMeta(propId=6)
    
        public String getMessageType(){
            return _messageType;
        }

        public void setMessageType(String value){
            this._messageType = value;
        }


        private String _messageType_label;

    
        public String getMessageType_label(){
            return _messageType_label;
        }

        public void setMessageType_label(String value){
            this._messageType_label = value;
        }


        private java.sql.Timestamp _requestTimestamp;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getRequestTimestamp(){
            return _requestTimestamp;
        }

        public void setRequestTimestamp(java.sql.Timestamp value){
            this._requestTimestamp = value;
        }


        private String _hash;

    
        @PropMeta(propId=8)
    
        public String getHash(){
            return _hash;
        }

        public void setHash(String value){
            this._hash = value;
        }


        private String _metadata;

    
        @PropMeta(propId=9)
    
        public String getMetadata(){
            return _metadata;
        }

        public void setMetadata(String value){
            this._metadata = value;
        }


    }
