//__XGEN_FORCE_OVERRIDE__
    package io.nop.job.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopJobTaskLogInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=16)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
