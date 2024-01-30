//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _WfStartRequestBean{

    
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
    
        private long _wfVersion;

        /**
         * 工作流版本 
         */
        @PropMeta(propId=2,displayName="工作流版本")
        public long getWfVersion(){
            return _wfVersion;
        }

        /**
         * 工作流版本 
         */
        public void setWfVersion(long value){
            this._wfVersion = value;
        }
    
        private java.util.Map<java.lang.String,java.lang.Object> _wfParams;

        /**
         * 启动参数 
         */
        @PropMeta(propId=3,displayName="启动参数")
        public java.util.Map<java.lang.String,java.lang.Object> getWfParams(){
            return _wfParams;
        }

        /**
         * 启动参数 
         */
        public void setWfParams(java.util.Map<java.lang.String,java.lang.Object> value){
            this._wfParams = value;
        }
    
        private String _parentWfName;

        /**
         * 父工作流名称 
         */
        @PropMeta(propId=4,displayName="父工作流名称")
        public String getParentWfName(){
            return _parentWfName;
        }

        /**
         * 父工作流名称 
         */
        public void setParentWfName(String value){
            this._parentWfName = value;
        }
    
        private Long _parentWfVersion;

        /**
         * 父工作流版本 
         */
        @PropMeta(propId=5,displayName="父工作流版本")
        public Long getParentWfVersion(){
            return _parentWfVersion;
        }

        /**
         * 父工作流版本 
         */
        public void setParentWfVersion(Long value){
            this._parentWfVersion = value;
        }
    
        private String _parentWfId;

        /**
         * 父工作流ID 
         */
        @PropMeta(propId=6,displayName="父工作流ID")
        public String getParentWfId(){
            return _parentWfId;
        }

        /**
         * 父工作流ID 
         */
        public void setParentWfId(String value){
            this._parentWfId = value;
        }
    
        private String _parentWfStepId;

        /**
         * 父工作流步骤ID 
         */
        @PropMeta(propId=7,displayName="父工作流步骤ID")
        public String getParentWfStepId(){
            return _parentWfStepId;
        }

        /**
         * 父工作流步骤ID 
         */
        public void setParentWfStepId(String value){
            this._parentWfStepId = value;
        }
    
    }
