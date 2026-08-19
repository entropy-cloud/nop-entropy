//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobTaskLogOutputBean {

    
        private String _jobTaskLogId;

    
        @PropMeta(propId=1)
    
        public String getJobTaskLogId(){
            return _jobTaskLogId;
        }

        public void setJobTaskLogId(String value){
            this._jobTaskLogId = value;
        }


        private String _jobTaskId;

    
        @PropMeta(propId=2)
    
        public String getJobTaskId(){
            return _jobTaskId;
        }

        public void setJobTaskId(String value){
            this._jobTaskId = value;
        }


        private String _jobFireId;

    
        @PropMeta(propId=3)
    
        public String getJobFireId(){
            return _jobFireId;
        }

        public void setJobFireId(String value){
            this._jobFireId = value;
        }


        private String _jobScheduleId;

    
        @PropMeta(propId=4)
    
        public String getJobScheduleId(){
            return _jobScheduleId;
        }

        public void setJobScheduleId(String value){
            this._jobScheduleId = value;
        }


        private String _jobName;

    
        @PropMeta(propId=5)
    
        public String getJobName(){
            return _jobName;
        }

        public void setJobName(String value){
            this._jobName = value;
        }


        private String _groupId;

    
        @PropMeta(propId=6)
    
        public String getGroupId(){
            return _groupId;
        }

        public void setGroupId(String value){
            this._groupId = value;
        }


        private java.sql.Timestamp _logTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getLogTime(){
            return _logTime;
        }

        public void setLogTime(java.sql.Timestamp value){
            this._logTime = value;
        }


        private Integer _logLevel;

    
        @PropMeta(propId=8)
    
        public Integer getLogLevel(){
            return _logLevel;
        }

        public void setLogLevel(Integer value){
            this._logLevel = value;
        }


        private String _logLevel_label;

    
        public String getLogLevel_label(){
            return _logLevel_label;
        }

        public void setLogLevel_label(String value){
            this._logLevel_label = value;
        }


        private String _logMessage;

    
        @PropMeta(propId=9)
    
        public String getLogMessage(){
            return _logMessage;
        }

        public void setLogMessage(String value){
            this._logMessage = value;
        }


        private String _logPayload;

    
        @PropMeta(propId=10)
    
        public String getLogPayload(){
            return _logPayload;
        }

        public void setLogPayload(String value){
            this._logPayload = value;
        }


        private Long _version;

    
        @PropMeta(propId=11)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=12)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=14)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=16)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
