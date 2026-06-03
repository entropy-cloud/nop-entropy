//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthRoleResourceOutputBean {

    
        private String _sid;

        @PropMeta(propId=1)
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _roleId;

        @PropMeta(propId=2)
        public String getRoleId(){
            return _roleId;
        }

        public void setRoleId(String value){
            this._roleId = value;
        }


        private String _resourceId;

        @PropMeta(propId=3)
        public String getResourceId(){
            return _resourceId;
        }

        public void setResourceId(String value){
            this._resourceId = value;
        }


        private Byte _delFlag;

        @PropMeta(propId=4)
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private Integer _version;

        @PropMeta(propId=5)
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

        @PropMeta(propId=6)
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

        @PropMeta(propId=7)
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

        @PropMeta(propId=8)
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

        @PropMeta(propId=9)
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

        @PropMeta(propId=10)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _role;

        public Map<String,Object> getRole(){
            return _role;
        }

        public void setRole(Map<String,Object> value){
            this._role = value;
        }


        private Map<String,Object> _resource;

        public Map<String,Object> getResource(){
            return _resource;
        }

        public void setResource(Map<String,Object> value){
            this._resource = value;
        }


    }
