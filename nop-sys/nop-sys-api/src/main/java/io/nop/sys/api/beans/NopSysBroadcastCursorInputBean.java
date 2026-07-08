//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysBroadcastCursorInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _subscriberId;

    
        @PropMeta(propId=2)
    
        public String getSubscriberId(){
            return _subscriberId;
        }

        public void setSubscriberId(String value){
            this._subscriberId = value;
        }


        private String _eventTopic;

    
        @PropMeta(propId=3)
    
        public String getEventTopic(){
            return _eventTopic;
        }

        public void setEventTopic(String value){
            this._eventTopic = value;
        }


        private Long _lastConsumedEventId;

    
        @PropMeta(propId=4)
    
        public Long getLastConsumedEventId(){
            return _lastConsumedEventId;
        }

        public void setLastConsumedEventId(Long value){
            this._lastConsumedEventId = value;
        }


        private String _leaseOwner;

    
        @PropMeta(propId=5)
    
        public String getLeaseOwner(){
            return _leaseOwner;
        }

        public void setLeaseOwner(String value){
            this._leaseOwner = value;
        }


        private java.sql.Timestamp _leaseExpireTime;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getLeaseExpireTime(){
            return _leaseExpireTime;
        }

        public void setLeaseExpireTime(java.sql.Timestamp value){
            this._leaseExpireTime = value;
        }


        private java.sql.Timestamp _lastConsumeTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getLastConsumeTime(){
            return _lastConsumeTime;
        }

        public void setLastConsumeTime(java.sql.Timestamp value){
            this._lastConsumeTime = value;
        }


        private String _lastError;

    
        @PropMeta(propId=8)
    
        public String getLastError(){
            return _lastError;
        }

        public void setLastError(String value){
            this._lastError = value;
        }


    }
