//__XGEN_FORCE_OVERRIDE__
    package io.nop.task.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopTaskInstanceInputBean extends CrudInputBase {

    
        private String _taskInstanceId;

    
        @PropMeta(propId=1)
    
        public String getTaskInstanceId(){
            return _taskInstanceId;
        }

        public void setTaskInstanceId(String value){
            this._taskInstanceId = value;
        }


        private String _taskName;

    
        @PropMeta(propId=2)
    
        public String getTaskName(){
            return _taskName;
        }

        public void setTaskName(String value){
            this._taskName = value;
        }


        private Long _taskVersion;

    
        @PropMeta(propId=3)
    
        public Long getTaskVersion(){
            return _taskVersion;
        }

        public void setTaskVersion(Long value){
            this._taskVersion = value;
        }


        private String _taskInputs;

    
        @PropMeta(propId=4)
    
        public String getTaskInputs(){
            return _taskInputs;
        }

        public void setTaskInputs(String value){
            this._taskInputs = value;
        }


        private String _taskGroup;

    
        @PropMeta(propId=5)
    
        public String getTaskGroup(){
            return _taskGroup;
        }

        public void setTaskGroup(String value){
            this._taskGroup = value;
        }


        private Integer _status;

    
        @PropMeta(propId=6)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private java.sql.Timestamp _startTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getStartTime(){
            return _startTime;
        }

        public void setStartTime(java.sql.Timestamp value){
            this._startTime = value;
        }


        private java.sql.Timestamp _endTime;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.sql.Timestamp value){
            this._endTime = value;
        }


        private java.sql.Timestamp _dueTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getDueTime(){
            return _dueTime;
        }

        public void setDueTime(java.sql.Timestamp value){
            this._dueTime = value;
        }


        private String _bizKey;

    
        @PropMeta(propId=10)
    
        public String getBizKey(){
            return _bizKey;
        }

        public void setBizKey(String value){
            this._bizKey = value;
        }


        private String _bizObjName;

    
        @PropMeta(propId=11)
    
        public String getBizObjName(){
            return _bizObjName;
        }

        public void setBizObjName(String value){
            this._bizObjName = value;
        }


        private String _bizObjId;

    
        @PropMeta(propId=12)
    
        public String getBizObjId(){
            return _bizObjId;
        }

        public void setBizObjId(String value){
            this._bizObjId = value;
        }


        private String _parentTaskName;

    
        @PropMeta(propId=13)
    
        public String getParentTaskName(){
            return _parentTaskName;
        }

        public void setParentTaskName(String value){
            this._parentTaskName = value;
        }


        private Long _parentTaskVersion;

    
        @PropMeta(propId=14)
    
        public Long getParentTaskVersion(){
            return _parentTaskVersion;
        }

        public void setParentTaskVersion(Long value){
            this._parentTaskVersion = value;
        }


        private String _parentTaskId;

    
        @PropMeta(propId=15)
    
        public String getParentTaskId(){
            return _parentTaskId;
        }

        public void setParentTaskId(String value){
            this._parentTaskId = value;
        }


        private String _parentStepId;

    
        @PropMeta(propId=16)
    
        public String getParentStepId(){
            return _parentStepId;
        }

        public void setParentStepId(String value){
            this._parentStepId = value;
        }


        private String _starterId;

    
        @PropMeta(propId=17)
    
        public String getStarterId(){
            return _starterId;
        }

        public void setStarterId(String value){
            this._starterId = value;
        }


        private String _starterName;

    
        @PropMeta(propId=18)
    
        public String getStarterName(){
            return _starterName;
        }

        public void setStarterName(String value){
            this._starterName = value;
        }


        private String _starterDeptId;

    
        @PropMeta(propId=19)
    
        public String getStarterDeptId(){
            return _starterDeptId;
        }

        public void setStarterDeptId(String value){
            this._starterDeptId = value;
        }


        private String _managerType;

    
        @PropMeta(propId=20)
    
        public String getManagerType(){
            return _managerType;
        }

        public void setManagerType(String value){
            this._managerType = value;
        }


        private String _managerDeptId;

    
        @PropMeta(propId=21)
    
        public String getManagerDeptId(){
            return _managerDeptId;
        }

        public void setManagerDeptId(String value){
            this._managerDeptId = value;
        }


        private String _managerName;

    
        @PropMeta(propId=22)
    
        public String getManagerName(){
            return _managerName;
        }

        public void setManagerName(String value){
            this._managerName = value;
        }


        private String _managerId;

    
        @PropMeta(propId=23)
    
        public String getManagerId(){
            return _managerId;
        }

        public void setManagerId(String value){
            this._managerId = value;
        }


        private Integer _priority;

    
        @PropMeta(propId=24)
    
        public Integer getPriority(){
            return _priority;
        }

        public void setPriority(Integer value){
            this._priority = value;
        }


        private String _signalText;

    
        @PropMeta(propId=25)
    
        public String getSignalText(){
            return _signalText;
        }

        public void setSignalText(String value){
            this._signalText = value;
        }


        private String _tagText;

    
        @PropMeta(propId=26)
    
        public String getTagText(){
            return _tagText;
        }

        public void setTagText(String value){
            this._tagText = value;
        }


        private String _jobInstanceId;

    
        @PropMeta(propId=27)
    
        public String getJobInstanceId(){
            return _jobInstanceId;
        }

        public void setJobInstanceId(String value){
            this._jobInstanceId = value;
        }


        private String _errCode;

    
        @PropMeta(propId=28)
    
        public String getErrCode(){
            return _errCode;
        }

        public void setErrCode(String value){
            this._errCode = value;
        }


        private String _errMsg;

    
        @PropMeta(propId=29)
    
        public String getErrMsg(){
            return _errMsg;
        }

        public void setErrMsg(String value){
            this._errMsg = value;
        }


        private String _workerId;

    
        @PropMeta(propId=30)
    
        public String getWorkerId(){
            return _workerId;
        }

        public void setWorkerId(String value){
            this._workerId = value;
        }


        private String _remark;

    
        @PropMeta(propId=37)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private String _errorBeanData;

    
        @PropMeta(propId=38)
    
        public String getErrorBeanData(){
            return _errorBeanData;
        }

        public void setErrorBeanData(String value){
            this._errorBeanData = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=39)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


    }
