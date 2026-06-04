//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfDefinitionAuthOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _wfDefId;

    
        @PropMeta(propId=2)
    
        public String getWfDefId(){
            return _wfDefId;
        }

        public void setWfDefId(String value){
            this._wfDefId = value;
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


        private Integer _version;

    
        @PropMeta(propId=10)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=11)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=13)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=15)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _wfDefinition;

        public Map<String,Object> getWfDefinition(){
            return _wfDefinition;
        }

        public void setWfDefinition(Map<String,Object> value){
            this._wfDefinition = value;
        }


    }
