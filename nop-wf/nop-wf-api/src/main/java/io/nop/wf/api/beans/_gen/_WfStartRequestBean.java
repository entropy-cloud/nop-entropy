//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans._gen;

    @SuppressWarnings({"PMD"})
    public class _WfStartRequestBean{

    
        private String _wfName;

        /**
         * 工作流名称 
         */
        public String getWfName(){
            return _wfName;
        }

        /**
         * 工作流名称 
         */
        public void setWfName(String value){
            this._wfName = value;
        }
    
        private String _wfVersion;

        /**
         * 工作流版本 
         */
        public String getWfVersion(){
            return _wfVersion;
        }

        /**
         * 工作流版本 
         */
        public void setWfVersion(String value){
            this._wfVersion = value;
        }
    
        private java.util.Map<java.lang.String,java.lang.Object> _args;

        /**
         * 启动参数 
         */
        public java.util.Map<java.lang.String,java.lang.Object> getArgs(){
            return _args;
        }

        /**
         * 启动参数 
         */
        public void setArgs(java.util.Map<java.lang.String,java.lang.Object> value){
            this._args = value;
        }
    
        private String _parentWfName;

        /**
         * 父工作流名称 
         */
        public String getParentWfName(){
            return _parentWfName;
        }

        /**
         * 父工作流名称 
         */
        public void setParentWfName(String value){
            this._parentWfName = value;
        }
    
        private String _parentWfVersion;

        /**
         * 父工作流版本 
         */
        public String getParentWfVersion(){
            return _parentWfVersion;
        }

        /**
         * 父工作流版本 
         */
        public void setParentWfVersion(String value){
            this._parentWfVersion = value;
        }
    
        private String _parentWfId;

        /**
         * 父工作流ID 
         */
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
