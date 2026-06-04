//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfStepInstanceLinkInputBean extends CrudInputBase {

    
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


    }
