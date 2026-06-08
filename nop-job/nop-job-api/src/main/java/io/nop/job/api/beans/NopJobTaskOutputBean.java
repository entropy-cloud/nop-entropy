//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobTaskOutputBean {

    
        private String _jobTaskId;

    
        @PropMeta(propId=1)
    
        public String getJobTaskId(){
            return _jobTaskId;
        }

        public void setJobTaskId(String value){
            this._jobTaskId = value;
        }


        private String _jobFireId;

    
        @PropMeta(propId=2)
    
        public String getJobFireId(){
            return _jobFireId;
        }

        public void setJobFireId(String value){
            this._jobFireId = value;
        }


        private Integer _shardingIndex;

    
        @PropMeta(propId=24)
    
        public Integer getShardingIndex(){
            return _shardingIndex;
        }

        public void setShardingIndex(Integer value){
            this._shardingIndex = value;
        }


        private Integer _shardingTotal;

    
        @PropMeta(propId=25)
    
        public Integer getShardingTotal(){
            return _shardingTotal;
        }

        public void setShardingTotal(Integer value){
            this._shardingTotal = value;
        }


        private Integer _taskStatus;

    
        @PropMeta(propId=4)
    
        public Integer getTaskStatus(){
            return _taskStatus;
        }

        public void setTaskStatus(Integer value){
            this._taskStatus = value;
        }


        private String _taskStatus_label;

    
        public String getTaskStatus_label(){
            return _taskStatus_label;
        }

        public void setTaskStatus_label(String value){
            this._taskStatus_label = value;
        }


        private String _workerInstanceId;

    
        @PropMeta(propId=5)
    
        public String getWorkerInstanceId(){
            return _workerInstanceId;
        }

        public void setWorkerInstanceId(String value){
            this._workerInstanceId = value;
        }


        private String _workerAddress;

    
        @PropMeta(propId=6)
    
        public String getWorkerAddress(){
            return _workerAddress;
        }

        public void setWorkerAddress(String value){
            this._workerAddress = value;
        }


        private java.sql.Timestamp _startTime;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getStartTime(){
            return _startTime;
        }

        public void setStartTime(java.sql.Timestamp value){
            this._startTime = value;
        }


        private java.sql.Timestamp _endTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.sql.Timestamp value){
            this._endTime = value;
        }


        private Long _durationMs;

    
        @PropMeta(propId=10)
    
        public Long getDurationMs(){
            return _durationMs;
        }

        public void setDurationMs(Long value){
            this._durationMs = value;
        }


        private String _resultPayload;

    
        @PropMeta(propId=11)
    
        public String getResultPayload(){
            return _resultPayload;
        }

        public void setResultPayload(String value){
            this._resultPayload = value;
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


        private String _taskPayload;

    
        @PropMeta(propId=7)
    
        public String getTaskPayload(){
            return _taskPayload;
        }

        public void setTaskPayload(String value){
            this._taskPayload = value;
        }


        private Integer _taskNo;

    
        @PropMeta(propId=3)
    
        public Integer getTaskNo(){
            return _taskNo;
        }

        public void setTaskNo(Integer value){
            this._taskNo = value;
        }


        private Short _partitionIndex;

    
        @PropMeta(propId=14)
    
        public Short getPartitionIndex(){
            return _partitionIndex;
        }

        public void setPartitionIndex(Short value){
            this._partitionIndex = value;
        }


        private Long _version;

    
        @PropMeta(propId=15)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=16)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=17)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=18)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=19)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=20)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Integer _progress;

    
        @PropMeta(propId=21)
    
        public Integer getProgress(){
            return _progress;
        }

        public void setProgress(Integer value){
            this._progress = value;
        }


        private String _progressMessage;

    
        @PropMeta(propId=22)
    
        public String getProgressMessage(){
            return _progressMessage;
        }

        public void setProgressMessage(String value){
            this._progressMessage = value;
        }


        private String _targetHost;

    
        @PropMeta(propId=23)
    
        public String getTargetHost(){
            return _targetHost;
        }

        public void setTargetHost(String value){
            this._targetHost = value;
        }


        private Map<String,Object> _jobFire;

        public Map<String,Object> getJobFire(){
            return _jobFire;
        }

        public void setJobFire(Map<String,Object> value){
            this._jobFire = value;
        }


    }
