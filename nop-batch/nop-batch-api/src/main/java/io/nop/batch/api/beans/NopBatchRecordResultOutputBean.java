//__XGEN_FORCE_OVERRIDE__
    package io.nop.batch.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopBatchRecordResultOutputBean {

    
        private String _batchTaskId;

    
        @PropMeta(propId=1)
    
        public String getBatchTaskId(){
            return _batchTaskId;
        }

        public void setBatchTaskId(String value){
            this._batchTaskId = value;
        }


        private String _recordKey;

    
        @PropMeta(propId=2)
    
        public String getRecordKey(){
            return _recordKey;
        }

        public void setRecordKey(String value){
            this._recordKey = value;
        }


        private Integer _resultStatus;

    
        @PropMeta(propId=3)
    
        public Integer getResultStatus(){
            return _resultStatus;
        }

        public void setResultStatus(Integer value){
            this._resultStatus = value;
        }


        private String _resultCode;

    
        @PropMeta(propId=4)
    
        public String getResultCode(){
            return _resultCode;
        }

        public void setResultCode(String value){
            this._resultCode = value;
        }


        private String _resultMsg;

    
        @PropMeta(propId=5)
    
        public String getResultMsg(){
            return _resultMsg;
        }

        public void setResultMsg(String value){
            this._resultMsg = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=6)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


        private String _recordInfo;

    
        @PropMeta(propId=7)
    
        public String getRecordInfo(){
            return _recordInfo;
        }

        public void setRecordInfo(String value){
            this._recordInfo = value;
        }


        private Integer _retryCount;

    
        @PropMeta(propId=8)
    
        public Integer getRetryCount(){
            return _retryCount;
        }

        public void setRetryCount(Integer value){
            this._retryCount = value;
        }


        private Integer _batchSize;

    
        @PropMeta(propId=9)
    
        public Integer getBatchSize(){
            return _batchSize;
        }

        public void setBatchSize(Integer value){
            this._batchSize = value;
        }


        private Integer _handleStatus;

    
        @PropMeta(propId=10)
    
        public Integer getHandleStatus(){
            return _handleStatus;
        }

        public void setHandleStatus(Integer value){
            this._handleStatus = value;
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


        private Map<String,Object> _task;

        public Map<String,Object> getTask(){
            return _task;
        }

        public void setTask(Map<String,Object> value){
            this._task = value;
        }


    }
