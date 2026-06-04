//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfStepInstanceLinkOutputBean {

    
        private String _wfId;

    
        @PropMeta(propId=1)
    
        public String getWfId(){
            return _wfId;
        }

        public void setWfId(String value){
            this._wfId = value;
        }


        private String _stepId;

    
        @PropMeta(propId=2)
    
        public String getStepId(){
            return _stepId;
        }

        public void setStepId(String value){
            this._stepId = value;
        }


        private String _nextStepId;

    
        @PropMeta(propId=3)
    
        public String getNextStepId(){
            return _nextStepId;
        }

        public void setNextStepId(String value){
            this._nextStepId = value;
        }


        private String _execAction;

    
        @PropMeta(propId=4)
    
        public String getExecAction(){
            return _execAction;
        }

        public void setExecAction(String value){
            this._execAction = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=5)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=6)
    
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


        private Map<String,Object> _wfStep;

        public Map<String,Object> getWfStep(){
            return _wfStep;
        }

        public void setWfStep(Map<String,Object> value){
            this._wfStep = value;
        }


        private Map<String,Object> _nextWfStep;

        public Map<String,Object> getNextWfStep(){
            return _nextWfStep;
        }

        public void setNextWfStep(Map<String,Object> value){
            this._nextWfStep = value;
        }


    }
