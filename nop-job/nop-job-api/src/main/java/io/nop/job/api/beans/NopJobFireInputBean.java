//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobFireInputBean extends CrudInputBase {

    
        private String _jobFireId;

    
        @PropMeta(propId=1)
    
        public String getJobFireId(){
            return _jobFireId;
        }

        public void setJobFireId(String value){
            this._jobFireId = value;
        }


        private java.sql.Timestamp _scheduledFireTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getScheduledFireTime(){
            return _scheduledFireTime;
        }

        public void setScheduledFireTime(java.sql.Timestamp value){
            this._scheduledFireTime = value;
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


    }
