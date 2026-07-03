//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    import java.util.Map;
    import java.util.Set;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _WfTransferFailedItemBean{

    
        private String _wfName;

        /**
         * 工作流名称 
         */
        @PropMeta(propId=1,displayName="工作流名称")
        public String getWfName(){
            return _wfName;
        }

        /**
         * 工作流名称 
         */
        public void setWfName(String value){
            this._wfName = value;
        }
    
        private Long _wfVersion;

        /**
         * 工作流版本 
         */
        @PropMeta(propId=2,displayName="工作流版本")
        public Long getWfVersion(){
            return _wfVersion;
        }

        /**
         * 工作流版本 
         */
        public void setWfVersion(Long value){
            this._wfVersion = value;
        }
    
        private String _wfId;

        /**
         * 工作流ID 
         */
        @PropMeta(propId=3,displayName="工作流ID")
        public String getWfId(){
            return _wfId;
        }

        /**
         * 工作流ID 
         */
        public void setWfId(String value){
            this._wfId = value;
        }
    
        private String _stepId;

        /**
         * 步骤ID 
         */
        @PropMeta(propId=4,displayName="步骤ID")
        public String getStepId(){
            return _stepId;
        }

        /**
         * 步骤ID 
         */
        public void setStepId(String value){
            this._stepId = value;
        }
    
        private String _reason;

        /**
         * 失败原因 
         */
        @PropMeta(propId=5,displayName="失败原因")
        public String getReason(){
            return _reason;
        }

        /**
         * 失败原因 
         */
        public void setReason(String value){
            this._reason = value;
        }
    
    }
