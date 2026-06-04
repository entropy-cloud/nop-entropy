//__XGEN_FORCE_OVERRIDE__
    package io.nop.tcc.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopTccBranchRecordInputBean extends CrudInputBase {

    
        private String _branchId;

    
        @PropMeta(propId=1)
    
        public String getBranchId(){
            return _branchId;
        }

        public void setBranchId(String value){
            this._branchId = value;
        }


        private String _txnId;

    
        @PropMeta(propId=2)
    
        public String getTxnId(){
            return _txnId;
        }

        public void setTxnId(String value){
            this._txnId = value;
        }


        private Integer _branchNo;

    
        @PropMeta(propId=3)
    
        public Integer getBranchNo(){
            return _branchNo;
        }

        public void setBranchNo(Integer value){
            this._branchNo = value;
        }


        private String _parentBranchId;

    
        @PropMeta(propId=4)
    
        public String getParentBranchId(){
            return _parentBranchId;
        }

        public void setParentBranchId(String value){
            this._parentBranchId = value;
        }


        private Integer _status;

    
        @PropMeta(propId=5)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private java.sql.Timestamp _expireTime;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getExpireTime(){
            return _expireTime;
        }

        public void setExpireTime(java.sql.Timestamp value){
            this._expireTime = value;
        }


        private String _serviceName;

    
        @PropMeta(propId=7)
    
        public String getServiceName(){
            return _serviceName;
        }

        public void setServiceName(String value){
            this._serviceName = value;
        }


        private String _serviceMethod;

    
        @PropMeta(propId=8)
    
        public String getServiceMethod(){
            return _serviceMethod;
        }

        public void setServiceMethod(String value){
            this._serviceMethod = value;
        }


        private String _confirmMethod;

    
        @PropMeta(propId=9)
    
        public String getConfirmMethod(){
            return _confirmMethod;
        }

        public void setConfirmMethod(String value){
            this._confirmMethod = value;
        }


        private String _cancelMethod;

    
        @PropMeta(propId=10)
    
        public String getCancelMethod(){
            return _cancelMethod;
        }

        public void setCancelMethod(String value){
            this._cancelMethod = value;
        }


        private String _requestData;

    
        @PropMeta(propId=11)
    
        public String getRequestData(){
            return _requestData;
        }

        public void setRequestData(String value){
            this._requestData = value;
        }


        private String _errorCode;

    
        @PropMeta(propId=12)
    
        public String getErrorCode(){
            return _errorCode;
        }

        public void setErrorCode(String value){
            this._errorCode = value;
        }


        private String _errorMessage;

    
        @PropMeta(propId=13)
    
        public String getErrorMessage(){
            return _errorMessage;
        }

        public void setErrorMessage(String value){
            this._errorMessage = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=14)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


        private java.sql.Timestamp _beginTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getBeginTime(){
            return _beginTime;
        }

        public void setBeginTime(java.sql.Timestamp value){
            this._beginTime = value;
        }


        private java.sql.Timestamp _endTime;

    
        @PropMeta(propId=16)
    
        public java.sql.Timestamp getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.sql.Timestamp value){
            this._endTime = value;
        }


        private String _commitErrorCode;

    
        @PropMeta(propId=17)
    
        public String getCommitErrorCode(){
            return _commitErrorCode;
        }

        public void setCommitErrorCode(String value){
            this._commitErrorCode = value;
        }


        private String _commitErrorMessage;

    
        @PropMeta(propId=18)
    
        public String getCommitErrorMessage(){
            return _commitErrorMessage;
        }

        public void setCommitErrorMessage(String value){
            this._commitErrorMessage = value;
        }


        private String _commitErrorStack;

    
        @PropMeta(propId=19)
    
        public String getCommitErrorStack(){
            return _commitErrorStack;
        }

        public void setCommitErrorStack(String value){
            this._commitErrorStack = value;
        }


        private String _cancelErrorCode;

    
        @PropMeta(propId=20)
    
        public String getCancelErrorCode(){
            return _cancelErrorCode;
        }

        public void setCancelErrorCode(String value){
            this._cancelErrorCode = value;
        }


        private String _cancelErrorMessage;

    
        @PropMeta(propId=21)
    
        public String getCancelErrorMessage(){
            return _cancelErrorMessage;
        }

        public void setCancelErrorMessage(String value){
            this._cancelErrorMessage = value;
        }


        private String _cancelErrorStack;

    
        @PropMeta(propId=22)
    
        public String getCancelErrorStack(){
            return _cancelErrorStack;
        }

        public void setCancelErrorStack(String value){
            this._cancelErrorStack = value;
        }


        private Integer _retryTimes;

    
        @PropMeta(propId=23)
    
        public Integer getRetryTimes(){
            return _retryTimes;
        }

        public void setRetryTimes(Integer value){
            this._retryTimes = value;
        }


        private Integer _maxRetryTimes;

    
        @PropMeta(propId=24)
    
        public Integer getMaxRetryTimes(){
            return _maxRetryTimes;
        }

        public void setMaxRetryTimes(Integer value){
            this._maxRetryTimes = value;
        }


        private java.sql.Timestamp _nextRetryTime;

    
        @PropMeta(propId=25)
    
        public java.sql.Timestamp getNextRetryTime(){
            return _nextRetryTime;
        }

        public void setNextRetryTime(java.sql.Timestamp value){
            this._nextRetryTime = value;
        }


    }
