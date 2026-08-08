//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiChannelSessionInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _channelType;

    
        @PropMeta(propId=2)
    
        public String getChannelType(){
            return _channelType;
        }

        public void setChannelType(String value){
            this._channelType = value;
        }


        private String _channelId;

    
        @PropMeta(propId=3)
    
        public String getChannelId(){
            return _channelId;
        }

        public void setChannelId(String value){
            this._channelId = value;
        }


        private String _sessionId;

    
        @PropMeta(propId=4)
    
        public String getSessionId(){
            return _sessionId;
        }

        public void setSessionId(String value){
            this._sessionId = value;
        }


        private String _agentName;

    
        @PropMeta(propId=5)
    
        public String getAgentName(){
            return _agentName;
        }

        public void setAgentName(String value){
            this._agentName = value;
        }


        private java.sql.Timestamp _lastActiveAt;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getLastActiveAt(){
            return _lastActiveAt;
        }

        public void setLastActiveAt(java.sql.Timestamp value){
            this._lastActiveAt = value;
        }


    }
