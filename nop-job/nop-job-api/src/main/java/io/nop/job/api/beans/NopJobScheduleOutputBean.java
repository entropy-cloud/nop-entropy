//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobScheduleOutputBean {

    
        private String _jobScheduleId;

    
        @PropMeta(propId=1)
    
        public String getJobScheduleId(){
            return _jobScheduleId;
        }

        public void setJobScheduleId(String value){
            this._jobScheduleId = value;
        }


        private String _namespaceId;

    
        @PropMeta(propId=2)
    
        public String getNamespaceId(){
            return _namespaceId;
        }

        public void setNamespaceId(String value){
            this._namespaceId = value;
        }


        private String _groupId;

    
        @PropMeta(propId=3)
    
        public String getGroupId(){
            return _groupId;
        }

        public void setGroupId(String value){
            this._groupId = value;
        }


        private String _jobName;

    
        @PropMeta(propId=4)
    
        public String getJobName(){
            return _jobName;
        }

        public void setJobName(String value){
            this._jobName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=5)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _description;

    
        @PropMeta(propId=6)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private Integer _scheduleStatus;

    
        @PropMeta(propId=7)
    
        public Integer getScheduleStatus(){
            return _scheduleStatus;
        }

        public void setScheduleStatus(Integer value){
            this._scheduleStatus = value;
        }


        private String _scheduleStatus_label;

    
        public String getScheduleStatus_label(){
            return _scheduleStatus_label;
        }

        public void setScheduleStatus_label(String value){
            this._scheduleStatus_label = value;
        }


        private String _executorKind;

    
        @PropMeta(propId=8)
    
        public String getExecutorKind(){
            return _executorKind;
        }

        public void setExecutorKind(String value){
            this._executorKind = value;
        }


        private String _executorKind_label;

    
        public String getExecutorKind_label(){
            return _executorKind_label;
        }

        public void setExecutorKind_label(String value){
            this._executorKind_label = value;
        }


        private String _jobParams;

    
        @PropMeta(propId=9)
    
        public String getJobParams(){
            return _jobParams;
        }

        public void setJobParams(String value){
            this._jobParams = value;
        }


        private Integer _triggerType;

    
        @PropMeta(propId=10)
    
        public Integer getTriggerType(){
            return _triggerType;
        }

        public void setTriggerType(Integer value){
            this._triggerType = value;
        }


        private String _triggerType_label;

    
        public String getTriggerType_label(){
            return _triggerType_label;
        }

        public void setTriggerType_label(String value){
            this._triggerType_label = value;
        }


        private String _cronExpr;

    
        @PropMeta(propId=11)
    
        public String getCronExpr(){
            return _cronExpr;
        }

        public void setCronExpr(String value){
            this._cronExpr = value;
        }


        private Long _repeatIntervalMs;

    
        @PropMeta(propId=12)
    
        public Long getRepeatIntervalMs(){
            return _repeatIntervalMs;
        }

        public void setRepeatIntervalMs(Long value){
            this._repeatIntervalMs = value;
        }


        private Integer _maxExecutionCount;

    
        @PropMeta(propId=13)
    
        public Integer getMaxExecutionCount(){
            return _maxExecutionCount;
        }

        public void setMaxExecutionCount(Integer value){
            this._maxExecutionCount = value;
        }


        private java.sql.Timestamp _minScheduleTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getMinScheduleTime(){
            return _minScheduleTime;
        }

        public void setMinScheduleTime(java.sql.Timestamp value){
            this._minScheduleTime = value;
        }


        private java.sql.Timestamp _maxScheduleTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getMaxScheduleTime(){
            return _maxScheduleTime;
        }

        public void setMaxScheduleTime(java.sql.Timestamp value){
            this._maxScheduleTime = value;
        }


        private Integer _misfireThresholdMs;

    
        @PropMeta(propId=16)
    
        public Integer getMisfireThresholdMs(){
            return _misfireThresholdMs;
        }

        public void setMisfireThresholdMs(Integer value){
            this._misfireThresholdMs = value;
        }


        private Byte _useDefaultCalendar;

    
        @PropMeta(propId=17)
    
        public Byte getUseDefaultCalendar(){
            return _useDefaultCalendar;
        }

        public void setUseDefaultCalendar(Byte value){
            this._useDefaultCalendar = value;
        }


        private String _pauseCalendarSpec;

    
        @PropMeta(propId=18)
    
        public String getPauseCalendarSpec(){
            return _pauseCalendarSpec;
        }

        public void setPauseCalendarSpec(String value){
            this._pauseCalendarSpec = value;
        }


        private Integer _blockStrategy;

    
        @PropMeta(propId=19)
    
        public Integer getBlockStrategy(){
            return _blockStrategy;
        }

        public void setBlockStrategy(Integer value){
            this._blockStrategy = value;
        }


        private String _blockStrategy_label;

    
        public String getBlockStrategy_label(){
            return _blockStrategy_label;
        }

        public void setBlockStrategy_label(String value){
            this._blockStrategy_label = value;
        }


        private Integer _timeoutSeconds;

    
        @PropMeta(propId=20)
    
        public Integer getTimeoutSeconds(){
            return _timeoutSeconds;
        }

        public void setTimeoutSeconds(Integer value){
            this._timeoutSeconds = value;
        }


        private String _retryPolicyId;

    
        @PropMeta(propId=21)
    
        public String getRetryPolicyId(){
            return _retryPolicyId;
        }

        public void setRetryPolicyId(String value){
            this._retryPolicyId = value;
        }


        private Short _partitionIndex;

    
        @PropMeta(propId=22)
    
        public Short getPartitionIndex(){
            return _partitionIndex;
        }

        public void setPartitionIndex(Short value){
            this._partitionIndex = value;
        }


        private Long _fireCount;

    
        @PropMeta(propId=23)
    
        public Long getFireCount(){
            return _fireCount;
        }

        public void setFireCount(Long value){
            this._fireCount = value;
        }


        private Integer _activeFireCount;

    
        @PropMeta(propId=24)
    
        public Integer getActiveFireCount(){
            return _activeFireCount;
        }

        public void setActiveFireCount(Integer value){
            this._activeFireCount = value;
        }


        private java.sql.Timestamp _lastFireTime;

    
        @PropMeta(propId=25)
    
        public java.sql.Timestamp getLastFireTime(){
            return _lastFireTime;
        }

        public void setLastFireTime(java.sql.Timestamp value){
            this._lastFireTime = value;
        }


        private java.sql.Timestamp _lastEndTime;

    
        @PropMeta(propId=26)
    
        public java.sql.Timestamp getLastEndTime(){
            return _lastEndTime;
        }

        public void setLastEndTime(java.sql.Timestamp value){
            this._lastEndTime = value;
        }


        private java.sql.Timestamp _nextFireTime;

    
        @PropMeta(propId=27)
    
        public java.sql.Timestamp getNextFireTime(){
            return _nextFireTime;
        }

        public void setNextFireTime(java.sql.Timestamp value){
            this._nextFireTime = value;
        }


        private Integer _lastFireStatus;

    
        @PropMeta(propId=28)
    
        public Integer getLastFireStatus(){
            return _lastFireStatus;
        }

        public void setLastFireStatus(Integer value){
            this._lastFireStatus = value;
        }


        private String _lastFireStatus_label;

    
        public String getLastFireStatus_label(){
            return _lastFireStatus_label;
        }

        public void setLastFireStatus_label(String value){
            this._lastFireStatus_label = value;
        }


        private Long _version;

    
        @PropMeta(propId=29)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=30)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=31)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=32)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=33)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=34)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Long _lastDurationMs;

    
        @PropMeta(propId=35)
    
        public Long getLastDurationMs(){
            return _lastDurationMs;
        }

        public void setLastDurationMs(Long value){
            this._lastDurationMs = value;
        }


        private Long _totalFireCount;

    
        @PropMeta(propId=36)
    
        public Long getTotalFireCount(){
            return _totalFireCount;
        }

        public void setTotalFireCount(Long value){
            this._totalFireCount = value;
        }


        private Long _successFireCount;

    
        @PropMeta(propId=37)
    
        public Long getSuccessFireCount(){
            return _successFireCount;
        }

        public void setSuccessFireCount(Long value){
            this._successFireCount = value;
        }


        private Long _failFireCount;

    
        @PropMeta(propId=38)
    
        public Long getFailFireCount(){
            return _failFireCount;
        }

        public void setFailFireCount(Long value){
            this._failFireCount = value;
        }


        private Integer _taskCostCpu;

    
        @PropMeta(propId=39)
    
        public Integer getTaskCostCpu(){
            return _taskCostCpu;
        }

        public void setTaskCostCpu(Integer value){
            this._taskCostCpu = value;
        }


        private Integer _taskCostMemory;

    
        @PropMeta(propId=40)
    
        public Integer getTaskCostMemory(){
            return _taskCostMemory;
        }

        public void setTaskCostMemory(Integer value){
            this._taskCostMemory = value;
        }


        private String _dispatchMode;

    
        @PropMeta(propId=41)
    
        public String getDispatchMode(){
            return _dispatchMode;
        }

        public void setDispatchMode(String value){
            this._dispatchMode = value;
        }


        private String _dispatchMode_label;

    
        public String getDispatchMode_label(){
            return _dispatchMode_label;
        }

        public void setDispatchMode_label(String value){
            this._dispatchMode_label = value;
        }


        private Integer _partitionCount;

    
        @PropMeta(propId=42)
    
        public Integer getPartitionCount(){
            return _partitionCount;
        }

        public void setPartitionCount(Integer value){
            this._partitionCount = value;
        }


        private Integer _priority;

    
        @PropMeta(propId=43)
    
        public Integer getPriority(){
            return _priority;
        }

        public void setPriority(Integer value){
            this._priority = value;
        }


    }
