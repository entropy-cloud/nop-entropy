//__XGEN_FORCE_OVERRIDE__
    package io.nop.task.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopTaskStepInstanceInputBean extends CrudInputBase {

    
        private String _stepInstanceId;

    
        @PropMeta(propId=1)
    
        public String getStepInstanceId(){
            return _stepInstanceId;
        }

        public void setStepInstanceId(String value){
            this._stepInstanceId = value;
        }


        private String _taskInstanceId;

    
        @PropMeta(propId=2)
    
        public String getTaskInstanceId(){
            return _taskInstanceId;
        }

        public void setTaskInstanceId(String value){
            this._taskInstanceId = value;
        }


        private String _stepType;

    
        @PropMeta(propId=3)
    
        public String getStepType(){
            return _stepType;
        }

        public void setStepType(String value){
            this._stepType = value;
        }


        private String _stepName;

    
        @PropMeta(propId=4)
    
        public String getStepName(){
            return _stepName;
        }

        public void setStepName(String value){
            this._stepName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=5)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private Integer _stepStatus;

    
        @PropMeta(propId=6)
    
        public Integer getStepStatus(){
            return _stepStatus;
        }

        public void setStepStatus(Integer value){
            this._stepStatus = value;
        }


        private String _subTaskId;

    
        @PropMeta(propId=8)
    
        public String getSubTaskId(){
            return _subTaskId;
        }

        public void setSubTaskId(String value){
            this._subTaskId = value;
        }


        private String _subTaskName;

    
        @PropMeta(propId=9)
    
        public String getSubTaskName(){
            return _subTaskName;
        }

        public void setSubTaskName(String value){
            this._subTaskName = value;
        }


        private Long _subTaskVersion;

    
        @PropMeta(propId=10)
    
        public Long getSubTaskVersion(){
            return _subTaskVersion;
        }

        public void setSubTaskVersion(Long value){
            this._subTaskVersion = value;
        }


        private java.sql.Timestamp _startTime;

    
        @PropMeta(propId=29)
    
        public java.sql.Timestamp getStartTime(){
            return _startTime;
        }

        public void setStartTime(java.sql.Timestamp value){
            this._startTime = value;
        }


        private java.sql.Timestamp _finishTime;

    
        @PropMeta(propId=30)
    
        public java.sql.Timestamp getFinishTime(){
            return _finishTime;
        }

        public void setFinishTime(java.sql.Timestamp value){
            this._finishTime = value;
        }


        private java.sql.Timestamp _dueTime;

    
        @PropMeta(propId=31)
    
        public java.sql.Timestamp getDueTime(){
            return _dueTime;
        }

        public void setDueTime(java.sql.Timestamp value){
            this._dueTime = value;
        }


        private java.sql.Timestamp _nextRetryTime;

    
        @PropMeta(propId=35)
    
        public java.sql.Timestamp getNextRetryTime(){
            return _nextRetryTime;
        }

        public void setNextRetryTime(java.sql.Timestamp value){
            this._nextRetryTime = value;
        }


        private Integer _retryCount;

    
        @PropMeta(propId=36)
    
        public Integer getRetryCount(){
            return _retryCount;
        }

        public void setRetryCount(Integer value){
            this._retryCount = value;
        }


        private Boolean _internal;

    
        @PropMeta(propId=37)
    
        public Boolean getInternal(){
            return _internal;
        }

        public void setInternal(Boolean value){
            this._internal = value;
        }


        private String _errCode;

    
        @PropMeta(propId=38)
    
        public String getErrCode(){
            return _errCode;
        }

        public void setErrCode(String value){
            this._errCode = value;
        }


        private String _errMsg;

    
        @PropMeta(propId=39)
    
        public String getErrMsg(){
            return _errMsg;
        }

        public void setErrMsg(String value){
            this._errMsg = value;
        }


        private Integer _priority;

    
        @PropMeta(propId=40)
    
        public Integer getPriority(){
            return _priority;
        }

        public void setPriority(Integer value){
            this._priority = value;
        }


        private String _tagText;

    
        @PropMeta(propId=41)
    
        public String getTagText(){
            return _tagText;
        }

        public void setTagText(String value){
            this._tagText = value;
        }


        private String _parentStepId;

    
        @PropMeta(propId=42)
    
        public String getParentStepId(){
            return _parentStepId;
        }

        public void setParentStepId(String value){
            this._parentStepId = value;
        }


        private String _workerId;

    
        @PropMeta(propId=43)
    
        public String getWorkerId(){
            return _workerId;
        }

        public void setWorkerId(String value){
            this._workerId = value;
        }


        private String _stepPath;

    
        @PropMeta(propId=44)
    
        public String getStepPath(){
            return _stepPath;
        }

        public void setStepPath(String value){
            this._stepPath = value;
        }


        private Integer _runId;

    
        @PropMeta(propId=45)
    
        public Integer getRunId(){
            return _runId;
        }

        public void setRunId(Integer value){
            this._runId = value;
        }


        private Integer _bodyStepIndex;

    
        @PropMeta(propId=46)
    
        public Integer getBodyStepIndex(){
            return _bodyStepIndex;
        }

        public void setBodyStepIndex(Integer value){
            this._bodyStepIndex = value;
        }


        private String _stateBeanData;

    
        @PropMeta(propId=47)
    
        public String getStateBeanData(){
            return _stateBeanData;
        }

        public void setStateBeanData(String value){
            this._stateBeanData = value;
        }


        private String _remark;

    
        @PropMeta(propId=53)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private String _errorBeanData;

    
        @PropMeta(propId=54)
    
        public String getErrorBeanData(){
            return _errorBeanData;
        }

        public void setErrorBeanData(String value){
            this._errorBeanData = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=55)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


    }
