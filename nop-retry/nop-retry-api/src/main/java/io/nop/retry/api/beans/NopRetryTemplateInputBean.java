//__XGEN_FORCE_OVERRIDE__
    package io.nop.retry.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRetryTemplateInputBean extends CrudInputBase {

    
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


        private String _name;

    
        @PropMeta(propId=4)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _status;

    
        @PropMeta(propId=5)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private Integer _maxRetryCount;

    
        @PropMeta(propId=6)
    
        public Integer getMaxRetryCount(){
            return _maxRetryCount;
        }

        public void setMaxRetryCount(Integer value){
            this._maxRetryCount = value;
        }


        private Integer _backoffStrategy;

    
        @PropMeta(propId=7)
    
        public Integer getBackoffStrategy(){
            return _backoffStrategy;
        }

        public void setBackoffStrategy(Integer value){
            this._backoffStrategy = value;
        }


        private String _backoffConfig;

    
        @PropMeta(propId=8)
    
        public String getBackoffConfig(){
            return _backoffConfig;
        }

        public void setBackoffConfig(String value){
            this._backoffConfig = value;
        }


        private Integer _executionTimeoutSeconds;

    
        @PropMeta(propId=9)
    
        public Integer getExecutionTimeoutSeconds(){
            return _executionTimeoutSeconds;
        }

        public void setExecutionTimeoutSeconds(Integer value){
            this._executionTimeoutSeconds = value;
        }


        private Long _deadlineTimeoutMs;

    
        @PropMeta(propId=10)
    
        public Long getDeadlineTimeoutMs(){
            return _deadlineTimeoutMs;
        }

        public void setDeadlineTimeoutMs(Long value){
            this._deadlineTimeoutMs = value;
        }


        private Integer _blockStrategy;

    
        @PropMeta(propId=11)
    
        public Integer getBlockStrategy(){
            return _blockStrategy;
        }

        public void setBlockStrategy(Integer value){
            this._blockStrategy = value;
        }


        private String _callbackEnabled;

    
        @PropMeta(propId=12)
    
        public String getCallbackEnabled(){
            return _callbackEnabled;
        }

        public void setCallbackEnabled(String value){
            this._callbackEnabled = value;
        }


        private Integer _callbackTriggerType;

    
        @PropMeta(propId=13)
    
        public Integer getCallbackTriggerType(){
            return _callbackTriggerType;
        }

        public void setCallbackTriggerType(Integer value){
            this._callbackTriggerType = value;
        }


        private Integer _callbackMaxAttempts;

    
        @PropMeta(propId=14)
    
        public Integer getCallbackMaxAttempts(){
            return _callbackMaxAttempts;
        }

        public void setCallbackMaxAttempts(Integer value){
            this._callbackMaxAttempts = value;
        }


        private Integer _callbackIntervalSeconds;

    
        @PropMeta(propId=15)
    
        public Integer getCallbackIntervalSeconds(){
            return _callbackIntervalSeconds;
        }

        public void setCallbackIntervalSeconds(Integer value){
            this._callbackIntervalSeconds = value;
        }


        private String _ownerId;

    
        @PropMeta(propId=16)
    
        public String getOwnerId(){
            return _ownerId;
        }

        public void setOwnerId(String value){
            this._ownerId = value;
        }


        private String _description;

    
        @PropMeta(propId=17)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private List<NopRetryRecordInputBean> _records;

        public List<NopRetryRecordInputBean> getRecords(){
            return _records;
        }

        public void setRecords(List<NopRetryRecordInputBean> value){
            this._records = value;
        }


    }
