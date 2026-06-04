//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfStepInstanceInputBean extends CrudInputBase {

    
        private String _stepId;

    
        @PropMeta(propId=1)
    
        public String getStepId(){
            return _stepId;
        }

        public void setStepId(String value){
            this._stepId = value;
        }


        private String _wfId;

    
        @PropMeta(propId=2)
    
        public String getWfId(){
            return _wfId;
        }

        public void setWfId(String value){
            this._wfId = value;
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


        private Integer _status;

    
        @PropMeta(propId=6)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _appState;

    
        @PropMeta(propId=7)
    
        public String getAppState(){
            return _appState;
        }

        public void setAppState(String value){
            this._appState = value;
        }


        private String _subWfId;

    
        @PropMeta(propId=8)
    
        public String getSubWfId(){
            return _subWfId;
        }

        public void setSubWfId(String value){
            this._subWfId = value;
        }


        private String _subWfName;

    
        @PropMeta(propId=9)
    
        public String getSubWfName(){
            return _subWfName;
        }

        public void setSubWfName(String value){
            this._subWfName = value;
        }


        private Long _subWfVersion;

    
        @PropMeta(propId=10)
    
        public Long getSubWfVersion(){
            return _subWfVersion;
        }

        public void setSubWfVersion(Long value){
            this._subWfVersion = value;
        }


        private Integer _subWfResultStatus;

    
        @PropMeta(propId=11)
    
        public Integer getSubWfResultStatus(){
            return _subWfResultStatus;
        }

        public void setSubWfResultStatus(Integer value){
            this._subWfResultStatus = value;
        }


        private Boolean _isRead;

    
        @PropMeta(propId=12)
    
        public Boolean getIsRead(){
            return _isRead;
        }

        public void setIsRead(Boolean value){
            this._isRead = value;
        }


        private String _actorModelId;

    
        @PropMeta(propId=13)
    
        public String getActorModelId(){
            return _actorModelId;
        }

        public void setActorModelId(String value){
            this._actorModelId = value;
        }


        private String _actorType;

    
        @PropMeta(propId=14)
    
        public String getActorType(){
            return _actorType;
        }

        public void setActorType(String value){
            this._actorType = value;
        }


        private String _actorId;

    
        @PropMeta(propId=15)
    
        public String getActorId(){
            return _actorId;
        }

        public void setActorId(String value){
            this._actorId = value;
        }


        private String _actorDeptId;

    
        @PropMeta(propId=16)
    
        public String getActorDeptId(){
            return _actorDeptId;
        }

        public void setActorDeptId(String value){
            this._actorDeptId = value;
        }


        private String _actorName;

    
        @PropMeta(propId=17)
    
        public String getActorName(){
            return _actorName;
        }

        public void setActorName(String value){
            this._actorName = value;
        }


        private String _ownerId;

    
        @PropMeta(propId=18)
    
        public String getOwnerId(){
            return _ownerId;
        }

        public void setOwnerId(String value){
            this._ownerId = value;
        }


        private String _ownerName;

    
        @PropMeta(propId=19)
    
        public String getOwnerName(){
            return _ownerName;
        }

        public void setOwnerName(String value){
            this._ownerName = value;
        }


        private String _ownerDeptId;

    
        @PropMeta(propId=20)
    
        public String getOwnerDeptId(){
            return _ownerDeptId;
        }

        public void setOwnerDeptId(String value){
            this._ownerDeptId = value;
        }


        private String _assignerId;

    
        @PropMeta(propId=21)
    
        public String getAssignerId(){
            return _assignerId;
        }

        public void setAssignerId(String value){
            this._assignerId = value;
        }


        private String _assignerName;

    
        @PropMeta(propId=22)
    
        public String getAssignerName(){
            return _assignerName;
        }

        public void setAssignerName(String value){
            this._assignerName = value;
        }


        private String _callerId;

    
        @PropMeta(propId=23)
    
        public String getCallerId(){
            return _callerId;
        }

        public void setCallerId(String value){
            this._callerId = value;
        }


        private String _callerName;

    
        @PropMeta(propId=24)
    
        public String getCallerName(){
            return _callerName;
        }

        public void setCallerName(String value){
            this._callerName = value;
        }


        private String _cancellerId;

    
        @PropMeta(propId=25)
    
        public String getCancellerId(){
            return _cancellerId;
        }

        public void setCancellerId(String value){
            this._cancellerId = value;
        }


        private String _cancellerName;

    
        @PropMeta(propId=26)
    
        public String getCancellerName(){
            return _cancellerName;
        }

        public void setCancellerName(String value){
            this._cancellerName = value;
        }


        private String _fromAction;

    
        @PropMeta(propId=27)
    
        public String getFromAction(){
            return _fromAction;
        }

        public void setFromAction(String value){
            this._fromAction = value;
        }


        private String _lastAction;

    
        @PropMeta(propId=28)
    
        public String getLastAction(){
            return _lastAction;
        }

        public void setLastAction(String value){
            this._lastAction = value;
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


        private java.sql.Timestamp _readTime;

    
        @PropMeta(propId=32)
    
        public java.sql.Timestamp getReadTime(){
            return _readTime;
        }

        public void setReadTime(java.sql.Timestamp value){
            this._readTime = value;
        }


        private java.sql.Timestamp _remindTime;

    
        @PropMeta(propId=33)
    
        public java.sql.Timestamp getRemindTime(){
            return _remindTime;
        }

        public void setRemindTime(java.sql.Timestamp value){
            this._remindTime = value;
        }


        private Integer _remindCount;

    
        @PropMeta(propId=34)
    
        public Integer getRemindCount(){
            return _remindCount;
        }

        public void setRemindCount(Integer value){
            this._remindCount = value;
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


        private String _errCode;

    
        @PropMeta(propId=37)
    
        public String getErrCode(){
            return _errCode;
        }

        public void setErrCode(String value){
            this._errCode = value;
        }


        private String _errMsg;

    
        @PropMeta(propId=38)
    
        public String getErrMsg(){
            return _errMsg;
        }

        public void setErrMsg(String value){
            this._errMsg = value;
        }


        private Integer _priority;

    
        @PropMeta(propId=39)
    
        public Integer getPriority(){
            return _priority;
        }

        public void setPriority(Integer value){
            this._priority = value;
        }


        private String _joinGroup;

    
        @PropMeta(propId=40)
    
        public String getJoinGroup(){
            return _joinGroup;
        }

        public void setJoinGroup(String value){
            this._joinGroup = value;
        }


        private String _tagText;

    
        @PropMeta(propId=41)
    
        public String getTagText(){
            return _tagText;
        }

        public void setTagText(String value){
            this._tagText = value;
        }


        private String _nextStepId;

    
        @PropMeta(propId=42)
    
        public String getNextStepId(){
            return _nextStepId;
        }

        public void setNextStepId(String value){
            this._nextStepId = value;
        }


        private String _execGroup;

    
        @PropMeta(propId=43)
    
        public String getExecGroup(){
            return _execGroup;
        }

        public void setExecGroup(String value){
            this._execGroup = value;
        }


        private Integer _execOrder;

    
        @PropMeta(propId=44)
    
        public Integer getExecOrder(){
            return _execOrder;
        }

        public void setExecOrder(Integer value){
            this._execOrder = value;
        }


        private Integer _execCount;

    
        @PropMeta(propId=45)
    
        public Integer getExecCount(){
            return _execCount;
        }

        public void setExecCount(Integer value){
            this._execCount = value;
        }


        private Integer _voteWeight;

    
        @PropMeta(propId=46)
    
        public Integer getVoteWeight(){
            return _voteWeight;
        }

        public void setVoteWeight(Integer value){
            this._voteWeight = value;
        }


        private String _remark;

    
        @PropMeta(propId=52)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
