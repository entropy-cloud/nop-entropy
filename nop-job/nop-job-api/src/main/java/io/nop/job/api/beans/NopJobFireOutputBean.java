//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobFireOutputBean {

    
        private String _jobFireId;

    
        @PropMeta(propId=1)
    
        public String getJobFireId(){
            return _jobFireId;
        }

        public void setJobFireId(String value){
            this._jobFireId = value;
        }


        private Integer _fireStatus;

    
        @PropMeta(propId=9)
    
        public Integer getFireStatus(){
            return _fireStatus;
        }

        public void setFireStatus(Integer value){
            this._fireStatus = value;
        }


        private String _fireStatus_label;

    
        public String getFireStatus_label(){
            return _fireStatus_label;
        }

        public void setFireStatus_label(String value){
            this._fireStatus_label = value;
        }


        private String _plannerInstanceId;

    
        @PropMeta(propId=10)
    
        public String getPlannerInstanceId(){
            return _plannerInstanceId;
        }

        public void setPlannerInstanceId(String value){
            this._plannerInstanceId = value;
        }


        private String _dispatchInstanceId;

    
        @PropMeta(propId=11)
    
        public String getDispatchInstanceId(){
            return _dispatchInstanceId;
        }

        public void setDispatchInstanceId(String value){
            this._dispatchInstanceId = value;
        }


        private java.sql.Timestamp _startTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getStartTime(){
            return _startTime;
        }

        public void setStartTime(java.sql.Timestamp value){
            this._startTime = value;
        }


        private java.sql.Timestamp _endTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.sql.Timestamp value){
            this._endTime = value;
        }


        private Long _durationMs;

    
        @PropMeta(propId=14)
    
        public Long getDurationMs(){
            return _durationMs;
        }

        public void setDurationMs(Long value){
            this._durationMs = value;
        }


        private String _errorCode;

    
        @PropMeta(propId=19)
    
        public String getErrorCode(){
            return _errorCode;
        }

        public void setErrorCode(String value){
            this._errorCode = value;
        }


        private String _errorMessage;

    
        @PropMeta(propId=20)
    
        public String getErrorMessage(){
            return _errorMessage;
        }

        public void setErrorMessage(String value){
            this._errorMessage = value;
        }


        private Integer _triggerSource;

    
        @PropMeta(propId=6)
    
        public Integer getTriggerSource(){
            return _triggerSource;
        }

        public void setTriggerSource(Integer value){
            this._triggerSource = value;
        }


        private String _triggerSource_label;

    
        public String getTriggerSource_label(){
            return _triggerSource_label;
        }

        public void setTriggerSource_label(String value){
            this._triggerSource_label = value;
        }


        private java.sql.Timestamp _scheduledFireTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getScheduledFireTime(){
            return _scheduledFireTime;
        }

        public void setScheduledFireTime(java.sql.Timestamp value){
            this._scheduledFireTime = value;
        }


        private String _triggeredBy;

    
        @PropMeta(propId=8)
    
        public String getTriggeredBy(){
            return _triggeredBy;
        }

        public void setTriggeredBy(String value){
            this._triggeredBy = value;
        }


        private String _retryPolicyId;

    
        @PropMeta(propId=17)
    
        public String getRetryPolicyId(){
            return _retryPolicyId;
        }

        public void setRetryPolicyId(String value){
            this._retryPolicyId = value;
        }


        private String _retryRecordId;

    
        @PropMeta(propId=18)
    
        public String getRetryRecordId(){
            return _retryRecordId;
        }

        public void setRetryRecordId(String value){
            this._retryRecordId = value;
        }


        private Short _partitionIndex;

    
        @PropMeta(propId=21)
    
        public Short getPartitionIndex(){
            return _partitionIndex;
        }

        public void setPartitionIndex(Short value){
            this._partitionIndex = value;
        }


        private Long _version;

    
        @PropMeta(propId=22)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=23)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=24)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=25)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=26)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=27)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private String _jobScheduleId;

    
        @PropMeta(propId=2)
    
        public String getJobScheduleId(){
            return _jobScheduleId;
        }

        public void setJobScheduleId(String value){
            this._jobScheduleId = value;
        }


        private String _namespaceId;

    
        @PropMeta(propId=3)
    
        public String getNamespaceId(){
            return _namespaceId;
        }

        public void setNamespaceId(String value){
            this._namespaceId = value;
        }


        private String _groupId;

    
        @PropMeta(propId=4)
    
        public String getGroupId(){
            return _groupId;
        }

        public void setGroupId(String value){
            this._groupId = value;
        }


        private String _jobName;

    
        @PropMeta(propId=5)
    
        public String getJobName(){
            return _jobName;
        }

        public void setJobName(String value){
            this._jobName = value;
        }


        private String _jobParamsSnapshot;

    
        @PropMeta(propId=15)
    
        public String getJobParamsSnapshot(){
            return _jobParamsSnapshot;
        }

        public void setJobParamsSnapshot(String value){
            this._jobParamsSnapshot = value;
        }


        private String _executorKind;

    
        @PropMeta(propId=16)
    
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


        private Map<String,Object> _jobSchedule;

        public Map<String,Object> getJobSchedule(){
            return _jobSchedule;
        }

        public void setJobSchedule(Map<String,Object> value){
            this._jobSchedule = value;
        }


    }
