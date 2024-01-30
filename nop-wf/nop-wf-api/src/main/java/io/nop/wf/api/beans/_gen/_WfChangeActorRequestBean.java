//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _WfChangeActorRequestBean{

    
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
         * 工作流步骤ID 
         */
        @PropMeta(propId=4,displayName="工作流步骤ID")
        public String getStepId(){
            return _stepId;
        }

        /**
         * 工作流步骤ID 
         */
        public void setStepId(String value){
            this._stepId = value;
        }
    
        private String _actorType;

        /**
         * 参与者类型 
         */
        @PropMeta(propId=5,displayName="参与者类型")
        public String getActorType(){
            return _actorType;
        }

        /**
         * 参与者类型 
         */
        public void setActorType(String value){
            this._actorType = value;
        }
    
        private String _actorId;

        /**
         * 参与者ID 
         */
        @PropMeta(propId=6,displayName="参与者ID")
        public String getActorId(){
            return _actorId;
        }

        /**
         * 参与者ID 
         */
        public void setActorId(String value){
            this._actorId = value;
        }
    
        private String _actorDeptId;

        /**
         * 参与者部门ID 
         */
        @PropMeta(propId=6,displayName="参与者部门ID")
        public String getActorDeptId(){
            return _actorDeptId;
        }

        /**
         * 参与者部门ID 
         */
        public void setActorDeptId(String value){
            this._actorDeptId = value;
        }
    
        private String _ownerId;

        /**
         * 拥有者ID 
         */
        @PropMeta(propId=7,displayName="拥有者ID")
        public String getOwnerId(){
            return _ownerId;
        }

        /**
         * 拥有者ID 
         */
        public void setOwnerId(String value){
            this._ownerId = value;
        }
    
        private java.util.Map<java.lang.String,java.lang.Object> _attrs;

        /**
         * 扩展属性 
         */
        @PropMeta(propId=9,displayName="扩展属性")
        public java.util.Map<java.lang.String,java.lang.Object> getAttrs(){
            return _attrs;
        }

        /**
         * 扩展属性 
         */
        public void setAttrs(java.util.Map<java.lang.String,java.lang.Object> value){
            this._attrs = value;
        }
    
    }
