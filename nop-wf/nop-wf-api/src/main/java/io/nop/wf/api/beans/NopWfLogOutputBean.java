//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfLogOutputBean {

    
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


        private String _actionId;

    
        @PropMeta(propId=4)
    
        public String getActionId(){
            return _actionId;
        }

        public void setActionId(String value){
            this._actionId = value;
        }


        private Integer _logLevel;

    
        @PropMeta(propId=5)
    
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


        private String _logMsg;

    
        @PropMeta(propId=6)
    
        public String getLogMsg(){
            return _logMsg;
        }

        public void setLogMsg(String value){
            this._logMsg = value;
        }


        private String _errCode;

    
        @PropMeta(propId=7)
    
        public String getErrCode(){
            return _errCode;
        }

        public void setErrCode(String value){
            this._errCode = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=8)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
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


        private Map<String,Object> _wfAction;

        public Map<String,Object> getWfAction(){
            return _wfAction;
        }

        public void setWfAction(Map<String,Object> value){
            this._wfAction = value;
        }


    }
