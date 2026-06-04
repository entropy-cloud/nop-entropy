//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfActionInputBean extends CrudInputBase {

    
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


    }
