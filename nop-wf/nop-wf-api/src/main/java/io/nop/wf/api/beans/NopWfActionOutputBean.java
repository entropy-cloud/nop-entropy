//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfActionOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _wfId;

    
        @PropMeta(propId=2)
    
        public String getWfId(){
            return _wfId;
        }

        public void setWfId(String value){
            this._wfId = value;
        }


        private String _stepId;

    
        @PropMeta(propId=3)
    
        public String getStepId(){
            return _stepId;
        }

        public void setStepId(String value){
            this._stepId = value;
        }


        private String _actionName;

    
        @PropMeta(propId=4)
    
        public String getActionName(){
            return _actionName;
        }

        public void setActionName(String value){
            this._actionName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=5)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private java.sql.Timestamp _execTime;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getExecTime(){
            return _execTime;
        }

        public void setExecTime(java.sql.Timestamp value){
            this._execTime = value;
        }


        private String _callerId;

    
        @PropMeta(propId=7)
    
        public String getCallerId(){
            return _callerId;
        }

        public void setCallerId(String value){
            this._callerId = value;
        }


        private String _callerName;

    
        @PropMeta(propId=8)
    
        public String getCallerName(){
            return _callerName;
        }

        public void setCallerName(String value){
            this._callerName = value;
        }


        private String _opinion;

    
        @PropMeta(propId=9)
    
        public String getOpinion(){
            return _opinion;
        }

        public void setOpinion(String value){
            this._opinion = value;
        }


        private String _errCode;

    
        @PropMeta(propId=10)
    
        public String getErrCode(){
            return _errCode;
        }

        public void setErrCode(String value){
            this._errCode = value;
        }


        private String _errMsg;

    
        @PropMeta(propId=11)
    
        public String getErrMsg(){
            return _errMsg;
        }

        public void setErrMsg(String value){
            this._errMsg = value;
        }


        private Integer _version;

    
        @PropMeta(propId=12)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=13)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=15)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=16)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private Map<String,Object> _wfInstance;

        public Map<String,Object> getWfInstance(){
            return _wfInstance;
        }

        public void setWfInstance(Map<String,Object> value){
            this._wfInstance = value;
        }


        private Map<String,Object> _wfStepInstance;

        public Map<String,Object> getWfStepInstance(){
            return _wfStepInstance;
        }

        public void setWfStepInstance(Map<String,Object> value){
            this._wfStepInstance = value;
        }


    }
