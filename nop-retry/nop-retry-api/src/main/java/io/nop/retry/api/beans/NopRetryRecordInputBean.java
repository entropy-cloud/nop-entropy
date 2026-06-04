//__XGEN_FORCE_OVERRIDE__
    package io.nop.retry.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRetryRecordInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
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


        private String _policyId;

    
        @PropMeta(propId=4)
    
        public String getPolicyId(){
            return _policyId;
        }

        public void setPolicyId(String value){
            this._policyId = value;
        }


        private String _idempotentId;

    
        @PropMeta(propId=5)
    
        public String getIdempotentId(){
            return _idempotentId;
        }

        public void setIdempotentId(String value){
            this._idempotentId = value;
        }


        private String _bizNo;

    
        @PropMeta(propId=6)
    
        public String getBizNo(){
            return _bizNo;
        }

        public void setBizNo(String value){
            this._bizNo = value;
        }


        private Integer _taskType;

    
        @PropMeta(propId=7)
    
        public Integer getTaskType(){
            return _taskType;
        }

        public void setTaskType(Integer value){
            this._taskType = value;
        }


        private Integer _status;

    
        @PropMeta(propId=8)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private Integer _retryCount;

    
        @PropMeta(propId=9)
    
        public Integer getRetryCount(){
            return _retryCount;
        }

        public void setRetryCount(Integer value){
            this._retryCount = value;
        }


        private Integer _maxRetryCount;

    
        @PropMeta(propId=10)
    
        public Integer getMaxRetryCount(){
            return _maxRetryCount;
        }

        public void setMaxRetryCount(Integer value){
            this._maxRetryCount = value;
        }


        private java.sql.Timestamp _nextTriggerTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getNextTriggerTime(){
            return _nextTriggerTime;
        }

        public void setNextTriggerTime(java.sql.Timestamp value){
            this._nextTriggerTime = value;
        }


        private Integer _partitionIndex;

    
        @PropMeta(propId=12)
    
        public Integer getPartitionIndex(){
            return _partitionIndex;
        }

        public void setPartitionIndex(Integer value){
            this._partitionIndex = value;
        }


        private String _executorName;

    
        @PropMeta(propId=13)
    
        public String getExecutorName(){
            return _executorName;
        }

        public void setExecutorName(String value){
            this._executorName = value;
        }


        private String _requestPayload;

    
        @PropMeta(propId=14)
    
        public String getRequestPayload(){
            return _requestPayload;
        }

        public void setRequestPayload(String value){
            this._requestPayload = value;
        }


        private String _contextPayload;

    
        @PropMeta(propId=15)
    
        public String getContextPayload(){
            return _contextPayload;
        }

        public void setContextPayload(String value){
            this._contextPayload = value;
        }


        private String _serviceName;

    
        @PropMeta(propId=21)
    
        public String getServiceName(){
            return _serviceName;
        }

        public void setServiceName(String value){
            this._serviceName = value;
        }


        private String _serviceMethod;

    
        @PropMeta(propId=22)
    
        public String getServiceMethod(){
            return _serviceMethod;
        }

        public void setServiceMethod(String value){
            this._serviceMethod = value;
        }


    }
