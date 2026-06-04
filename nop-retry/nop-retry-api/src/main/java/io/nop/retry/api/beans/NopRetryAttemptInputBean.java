//__XGEN_FORCE_OVERRIDE__
    package io.nop.retry.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRetryAttemptInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _recordId;

    
        @PropMeta(propId=2)
    
        public String getRecordId(){
            return _recordId;
        }

        public void setRecordId(String value){
            this._recordId = value;
        }


        private Integer _attemptNo;

    
        @PropMeta(propId=3)
    
        public Integer getAttemptNo(){
            return _attemptNo;
        }

        public void setAttemptNo(Integer value){
            this._attemptNo = value;
        }


        private Integer _status;

    
        @PropMeta(propId=4)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private java.sql.Timestamp _startTime;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getStartTime(){
            return _startTime;
        }

        public void setStartTime(java.sql.Timestamp value){
            this._startTime = value;
        }


        private java.sql.Timestamp _endTime;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.sql.Timestamp value){
            this._endTime = value;
        }


        private Long _durationMs;

    
        @PropMeta(propId=7)
    
        public Long getDurationMs(){
            return _durationMs;
        }

        public void setDurationMs(Long value){
            this._durationMs = value;
        }


        private String _errorCode;

    
        @PropMeta(propId=8)
    
        public String getErrorCode(){
            return _errorCode;
        }

        public void setErrorCode(String value){
            this._errorCode = value;
        }


        private String _errorMessage;

    
        @PropMeta(propId=9)
    
        public String getErrorMessage(){
            return _errorMessage;
        }

        public void setErrorMessage(String value){
            this._errorMessage = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=10)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


        private String _clientAddress;

    
        @PropMeta(propId=11)
    
        public String getClientAddress(){
            return _clientAddress;
        }

        public void setClientAddress(String value){
            this._clientAddress = value;
        }


        private String _reason;

    
        @PropMeta(propId=12)
    
        public String getReason(){
            return _reason;
        }

        public void setReason(String value){
            this._reason = value;
        }


        private String _requestPayloadSnapshot;

    
        @PropMeta(propId=13)
    
        public String getRequestPayloadSnapshot(){
            return _requestPayloadSnapshot;
        }

        public void setRequestPayloadSnapshot(String value){
            this._requestPayloadSnapshot = value;
        }


    }
