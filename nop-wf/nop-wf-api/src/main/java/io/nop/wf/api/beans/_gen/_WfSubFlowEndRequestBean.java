//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _WfSubFlowEndRequestBean{

    
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
    
        private Integer _status;

        /**
         * 状态 
         */
        @PropMeta(propId=4,displayName="状态")
        public Integer getStatus(){
            return _status;
        }

        /**
         * 状态 
         */
        public void setStatus(Integer value){
            this._status = value;
        }
    
        private java.util.Map<java.lang.String,java.lang.Object> _results;

        /**
         * 结果数据 
         */
        @PropMeta(propId=5,displayName="结果数据")
        public java.util.Map<java.lang.String,java.lang.Object> getResults(){
            return _results;
        }

        /**
         * 结果数据 
         */
        public void setResults(java.util.Map<java.lang.String,java.lang.Object> value){
            this._results = value;
        }
    
        private String _parentWfName;

        /**
         * 父工作流名称 
         */
        @PropMeta(propId=6,displayName="父工作流名称")
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
        @PropMeta(propId=7,displayName="父工作流版本")
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
        @PropMeta(propId=8,displayName="父工作流ID")
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
        @PropMeta(propId=9,displayName="父工作流步骤ID")
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
