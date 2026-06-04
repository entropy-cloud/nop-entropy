//__XGEN_FORCE_OVERRIDE__
    package io.nop.retry.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRetryDeadLetterOutputBean {

    
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


        private String _recordId;

    
        @PropMeta(propId=5)
    
        public String getRecordId(){
            return _recordId;
        }

        public void setRecordId(String value){
            this._recordId = value;
        }


        private String _idempotentId;

    
        @PropMeta(propId=6)
    
        public String getIdempotentId(){
            return _idempotentId;
        }

        public void setIdempotentId(String value){
            this._idempotentId = value;
        }


        private String _bizNo;

    
        @PropMeta(propId=7)
    
        public String getBizNo(){
            return _bizNo;
        }

        public void setBizNo(String value){
            this._bizNo = value;
        }


        private String _executorName;

    
        @PropMeta(propId=8)
    
        public String getExecutorName(){
            return _executorName;
        }

        public void setExecutorName(String value){
            this._executorName = value;
        }


        private String _requestPayload;

    
        @PropMeta(propId=9)
    
        public String getRequestPayload(){
            return _requestPayload;
        }

        public void setRequestPayload(String value){
            this._requestPayload = value;
        }


        private String _failureCode;

    
        @PropMeta(propId=10)
    
        public String getFailureCode(){
            return _failureCode;
        }

        public void setFailureCode(String value){
            this._failureCode = value;
        }


        private String _failureMessage;

    
        @PropMeta(propId=11)
    
        public String getFailureMessage(){
            return _failureMessage;
        }

        public void setFailureMessage(String value){
            this._failureMessage = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=12)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


        private Integer _finalStatus;

    
        @PropMeta(propId=13)
    
        public Integer getFinalStatus(){
            return _finalStatus;
        }

        public void setFinalStatus(Integer value){
            this._finalStatus = value;
        }


        private String _finalStatus_label;

    
        public String getFinalStatus_label(){
            return _finalStatus_label;
        }

        public void setFinalStatus_label(String value){
            this._finalStatus_label = value;
        }


        private Integer _version;

    
        @PropMeta(propId=14)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=15)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=16)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=17)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=18)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _serviceName;

    
        @PropMeta(propId=19)
    
        public String getServiceName(){
            return _serviceName;
        }

        public void setServiceName(String value){
            this._serviceName = value;
        }


        private String _serviceMethod;

    
        @PropMeta(propId=20)
    
        public String getServiceMethod(){
            return _serviceMethod;
        }

        public void setServiceMethod(String value){
            this._serviceMethod = value;
        }


        private Map<String,Object> _record;

        public Map<String,Object> getRecord(){
            return _record;
        }

        public void setRecord(Map<String,Object> value){
            this._record = value;
        }


    }
