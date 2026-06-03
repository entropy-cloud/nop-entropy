//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthOpLogOutputBean {

    
        private String _logId;

        @PropMeta(propId=1)
        public String getLogId(){
            return _logId;
        }

        public void setLogId(String value){
            this._logId = value;
        }


        private String _userName;

        @PropMeta(propId=2)
        public String getUserName(){
            return _userName;
        }

        public void setUserName(String value){
            this._userName = value;
        }


        private String _userId;

        @PropMeta(propId=3)
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _sessionId;

        @PropMeta(propId=4)
        public String getSessionId(){
            return _sessionId;
        }

        public void setSessionId(String value){
            this._sessionId = value;
        }


        private String _operation;

        @PropMeta(propId=5)
        public String getOperation(){
            return _operation;
        }

        public void setOperation(String value){
            this._operation = value;
        }


        private String _description;

        @PropMeta(propId=6)
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private java.sql.Timestamp _actionTime;

        @PropMeta(propId=7)
        public java.sql.Timestamp getActionTime(){
            return _actionTime;
        }

        public void setActionTime(java.sql.Timestamp value){
            this._actionTime = value;
        }


        private Long _usedTime;

        @PropMeta(propId=8)
        public Long getUsedTime(){
            return _usedTime;
        }

        public void setUsedTime(Long value){
            this._usedTime = value;
        }


        private Integer _resultStatus;

        @PropMeta(propId=9)
        public Integer getResultStatus(){
            return _resultStatus;
        }

        public void setResultStatus(Integer value){
            this._resultStatus = value;
        }


        private String _errorCode;

        @PropMeta(propId=10)
        public String getErrorCode(){
            return _errorCode;
        }

        public void setErrorCode(String value){
            this._errorCode = value;
        }


        private String _retMessage;

        @PropMeta(propId=11)
        public String getRetMessage(){
            return _retMessage;
        }

        public void setRetMessage(String value){
            this._retMessage = value;
        }


        private String _opRequest;

        @PropMeta(propId=12)
        public String getOpRequest(){
            return _opRequest;
        }

        public void setOpRequest(String value){
            this._opRequest = value;
        }


        private String _opResponse;

        @PropMeta(propId=13)
        public String getOpResponse(){
            return _opResponse;
        }

        public void setOpResponse(String value){
            this._opResponse = value;
        }


        private Map<String,Object> _session;

        public Map<String,Object> getSession(){
            return _session;
        }

        public void setSession(Map<String,Object> value){
            this._session = value;
        }


        private Map<String,Object> _user;

        public Map<String,Object> getUser(){
            return _user;
        }

        public void setUser(Map<String,Object> value){
            this._user = value;
        }


    }
