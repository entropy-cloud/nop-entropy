//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthGroupOutputBean {

    
        private String _groupId;

        @PropMeta(propId=1)
        public String getGroupId(){
            return _groupId;
        }

        public void setGroupId(String value){
            this._groupId = value;
        }


        private String _name;

        @PropMeta(propId=2)
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _parentId;

        @PropMeta(propId=3)
        public String getParentId(){
            return _parentId;
        }

        public void setParentId(String value){
            this._parentId = value;
        }


        private String _ownerId;

        @PropMeta(propId=4)
        public String getOwnerId(){
            return _ownerId;
        }

        public void setOwnerId(String value){
            this._ownerId = value;
        }


        private Byte _delFlag;

        @PropMeta(propId=5)
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private Integer _version;

        @PropMeta(propId=6)
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

        @PropMeta(propId=7)
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

        @PropMeta(propId=8)
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

        @PropMeta(propId=9)
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

        @PropMeta(propId=10)
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

        @PropMeta(propId=11)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _parent;

        public Map<String,Object> getParent(){
            return _parent;
        }

        public void setParent(Map<String,Object> value){
            this._parent = value;
        }


        private Map<String,Object> _owner;

        public Map<String,Object> getOwner(){
            return _owner;
        }

        public void setOwner(Map<String,Object> value){
            this._owner = value;
        }


        private List<Map<String,Object>> _children;

        public List<Map<String,Object>> getChildren(){
            return _children;
        }

        public void setChildren(List<Map<String,Object>> value){
            this._children = value;
        }


        private List<Map<String,Object>> _deptMappings;

        public List<Map<String,Object>> getDeptMappings(){
            return _deptMappings;
        }

        public void setDeptMappings(List<Map<String,Object>> value){
            this._deptMappings = value;
        }


        private List<Map<String,Object>> _userMappings;

        public List<Map<String,Object>> getUserMappings(){
            return _userMappings;
        }

        public void setUserMappings(List<Map<String,Object>> value){
            this._userMappings = value;
        }


    }
