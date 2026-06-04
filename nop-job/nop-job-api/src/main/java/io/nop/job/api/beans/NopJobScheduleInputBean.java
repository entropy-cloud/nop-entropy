//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobScheduleInputBean extends CrudInputBase {

    
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


        private String _executorKind;

    
        @PropMeta(propId=8)
    
        public String getExecutorKind(){
            return _executorKind;
        }

        public void setExecutorKind(String value){
            this._executorKind = value;
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


        private String _remark;

    
        @PropMeta(propId=34)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
