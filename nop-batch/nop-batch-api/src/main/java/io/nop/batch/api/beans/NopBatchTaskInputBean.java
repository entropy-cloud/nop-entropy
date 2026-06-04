//__XGEN_FORCE_OVERRIDE__
    package io.nop.batch.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopBatchTaskInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _taskName;

    
        @PropMeta(propId=2)
    
        public String getTaskName(){
            return _taskName;
        }

        public void setTaskName(String value){
            this._taskName = value;
        }


        private String _taskKey;

    
        @PropMeta(propId=3)
    
        public String getTaskKey(){
            return _taskKey;
        }

        public void setTaskKey(String value){
            this._taskKey = value;
        }


        private Integer _taskStatus;

    
        @PropMeta(propId=4)
    
        public Integer getTaskStatus(){
            return _taskStatus;
        }

        public void setTaskStatus(Integer value){
            this._taskStatus = value;
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


        private String _taskParams;

    
        @PropMeta(propId=7)
    
        public String getTaskParams(){
            return _taskParams;
        }

        public void setTaskParams(String value){
            this._taskParams = value;
        }


        private Integer _execCount;

    
        @PropMeta(propId=8)
    
        public Integer getExecCount(){
            return _execCount;
        }

        public void setExecCount(Integer value){
            this._execCount = value;
        }


        private String _workerId;

    
        @PropMeta(propId=9)
    
        public String getWorkerId(){
            return _workerId;
        }

        public void setWorkerId(String value){
            this._workerId = value;
        }


        private String _inputFileId;

    
        @PropMeta(propId=10)
    
        public String getInputFileId(){
            return _inputFileId;
        }

        public void setInputFileId(String value){
            this._inputFileId = value;
        }


        private String _flowStepId;

    
        @PropMeta(propId=11)
    
        public String getFlowStepId(){
            return _flowStepId;
        }

        public void setFlowStepId(String value){
            this._flowStepId = value;
        }


        private String _flowId;

    
        @PropMeta(propId=12)
    
        public String getFlowId(){
            return _flowId;
        }

        public void setFlowId(String value){
            this._flowId = value;
        }


        private java.sql.Timestamp _restartTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getRestartTime(){
            return _restartTime;
        }

        public void setRestartTime(java.sql.Timestamp value){
            this._restartTime = value;
        }


        private Integer _resultStatus;

    
        @PropMeta(propId=14)
    
        public Integer getResultStatus(){
            return _resultStatus;
        }

        public void setResultStatus(Integer value){
            this._resultStatus = value;
        }


        private String _resultCode;

    
        @PropMeta(propId=15)
    
        public String getResultCode(){
            return _resultCode;
        }

        public void setResultCode(String value){
            this._resultCode = value;
        }


        private String _resultMsg;

    
        @PropMeta(propId=16)
    
        public String getResultMsg(){
            return _resultMsg;
        }

        public void setResultMsg(String value){
            this._resultMsg = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=17)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


        private Long _completedIndex;

    
        @PropMeta(propId=18)
    
        public Long getCompletedIndex(){
            return _completedIndex;
        }

        public void setCompletedIndex(Long value){
            this._completedIndex = value;
        }


        private Long _completeItemCount;

    
        @PropMeta(propId=19)
    
        public Long getCompleteItemCount(){
            return _completeItemCount;
        }

        public void setCompleteItemCount(Long value){
            this._completeItemCount = value;
        }


        private Integer _loadRetryCount;

    
        @PropMeta(propId=20)
    
        public Integer getLoadRetryCount(){
            return _loadRetryCount;
        }

        public void setLoadRetryCount(Integer value){
            this._loadRetryCount = value;
        }


        private Long _loadSkipCount;

    
        @PropMeta(propId=21)
    
        public Long getLoadSkipCount(){
            return _loadSkipCount;
        }

        public void setLoadSkipCount(Long value){
            this._loadSkipCount = value;
        }


        private Integer _retryItemCount;

    
        @PropMeta(propId=22)
    
        public Integer getRetryItemCount(){
            return _retryItemCount;
        }

        public void setRetryItemCount(Integer value){
            this._retryItemCount = value;
        }


        private Long _processItemCount;

    
        @PropMeta(propId=23)
    
        public Long getProcessItemCount(){
            return _processItemCount;
        }

        public void setProcessItemCount(Long value){
            this._processItemCount = value;
        }


        private Long _skipItemCount;

    
        @PropMeta(propId=24)
    
        public Long getSkipItemCount(){
            return _skipItemCount;
        }

        public void setSkipItemCount(Long value){
            this._skipItemCount = value;
        }


        private Long _writeItemCount;

    
        @PropMeta(propId=25)
    
        public Long getWriteItemCount(){
            return _writeItemCount;
        }

        public void setWriteItemCount(Long value){
            this._writeItemCount = value;
        }


        private String _remark;

    
        @PropMeta(propId=31)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
