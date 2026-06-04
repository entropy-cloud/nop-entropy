//__XGEN_FORCE_OVERRIDE__
    package io.nop.task.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopTaskDefinitionAuthInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _taskDefId;

    
        @PropMeta(propId=2)
    
        public String getTaskDefId(){
            return _taskDefId;
        }

        public void setTaskDefId(String value){
            this._taskDefId = value;
        }


        private String _actorType;

    
        @PropMeta(propId=3)
    
        public String getActorType(){
            return _actorType;
        }

        public void setActorType(String value){
            this._actorType = value;
        }


        private String _actorId;

    
        @PropMeta(propId=4)
    
        public String getActorId(){
            return _actorId;
        }

        public void setActorId(String value){
            this._actorId = value;
        }


        private String _actorDeptId;

    
        @PropMeta(propId=5)
    
        public String getActorDeptId(){
            return _actorDeptId;
        }

        public void setActorDeptId(String value){
            this._actorDeptId = value;
        }


        private String _actorName;

    
        @PropMeta(propId=6)
    
        public String getActorName(){
            return _actorName;
        }

        public void setActorName(String value){
            this._actorName = value;
        }


        private Boolean _allowEdit;

    
        @PropMeta(propId=7)
    
        public Boolean getAllowEdit(){
            return _allowEdit;
        }

        public void setAllowEdit(Boolean value){
            this._allowEdit = value;
        }


        private Boolean _allowManage;

    
        @PropMeta(propId=8)
    
        public Boolean getAllowManage(){
            return _allowManage;
        }

        public void setAllowManage(Boolean value){
            this._allowManage = value;
        }


        private Boolean _allowStart;

    
        @PropMeta(propId=9)
    
        public Boolean getAllowStart(){
            return _allowStart;
        }

        public void setAllowStart(Boolean value){
            this._allowStart = value;
        }


        private String _remark;

    
        @PropMeta(propId=15)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
