//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysEventInputBean extends CrudInputBase {

    
        private Long _eventId;

    
        @PropMeta(propId=1)
    
        public Long getEventId(){
            return _eventId;
        }

        public void setEventId(Long value){
            this._eventId = value;
        }


        private String _eventTopic;

    
        @PropMeta(propId=2)
    
        public String getEventTopic(){
            return _eventTopic;
        }

        public void setEventTopic(String value){
            this._eventTopic = value;
        }


        private String _eventName;

    
        @PropMeta(propId=3)
    
        public String getEventName(){
            return _eventName;
        }

        public void setEventName(String value){
            this._eventName = value;
        }


        private String _eventHeaders;

    
        @PropMeta(propId=4)
    
        public String getEventHeaders(){
            return _eventHeaders;
        }

        public void setEventHeaders(String value){
            this._eventHeaders = value;
        }


        private String _eventData;

    
        @PropMeta(propId=5)
    
        public String getEventData(){
            return _eventData;
        }

        public void setEventData(String value){
            this._eventData = value;
        }


        private String _selection;

    
        @PropMeta(propId=6)
    
        public String getSelection(){
            return _selection;
        }

        public void setSelection(String value){
            this._selection = value;
        }


        private java.sql.Timestamp _eventTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getEventTime(){
            return _eventTime;
        }

        public void setEventTime(java.sql.Timestamp value){
            this._eventTime = value;
        }


        private Integer _eventStatus;

    
        @PropMeta(propId=8)
    
        public Integer getEventStatus(){
            return _eventStatus;
        }

        public void setEventStatus(Integer value){
            this._eventStatus = value;
        }


        private java.sql.Timestamp _processTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getProcessTime(){
            return _processTime;
        }

        public void setProcessTime(java.sql.Timestamp value){
            this._processTime = value;
        }


        private java.sql.Timestamp _scheduleTime;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getScheduleTime(){
            return _scheduleTime;
        }

        public void setScheduleTime(java.sql.Timestamp value){
            this._scheduleTime = value;
        }


        private Boolean _isBroadcast;

    
        @PropMeta(propId=11)
    
        public Boolean getIsBroadcast(){
            return _isBroadcast;
        }

        public void setIsBroadcast(Boolean value){
            this._isBroadcast = value;
        }


        private String _bizObjName;

    
        @PropMeta(propId=12)
    
        public String getBizObjName(){
            return _bizObjName;
        }

        public void setBizObjName(String value){
            this._bizObjName = value;
        }


        private String _bizKey;

    
        @PropMeta(propId=13)
    
        public String getBizKey(){
            return _bizKey;
        }

        public void setBizKey(String value){
            this._bizKey = value;
        }


        private java.time.LocalDate _bizDate;

    
        @PropMeta(propId=14)
    
        public java.time.LocalDate getBizDate(){
            return _bizDate;
        }

        public void setBizDate(java.time.LocalDate value){
            this._bizDate = value;
        }


        private Integer _partitionIndex;

    
        @PropMeta(propId=15)
    
        public Integer getPartitionIndex(){
            return _partitionIndex;
        }

        public void setPartitionIndex(Integer value){
            this._partitionIndex = value;
        }


        private Integer _retryTimes;

    
        @PropMeta(propId=16)
    
        public Integer getRetryTimes(){
            return _retryTimes;
        }

        public void setRetryTimes(Integer value){
            this._retryTimes = value;
        }


    }
