//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthDeptOutputBean {

    
        private String _deptId;

    
        @PropMeta(propId=1)
    
        public String getDeptId(){
            return _deptId;
        }

        public void setDeptId(String value){
            this._deptId = value;
        }


        private String _deptName;

    
        @PropMeta(propId=2)
    
        public String getDeptName(){
            return _deptName;
        }

        public void setDeptName(String value){
            this._deptName = value;
        }


        private String _parentId;

    
        @PropMeta(propId=3)
    
        public String getParentId(){
            return _parentId;
        }

        public void setParentId(String value){
            this._parentId = value;
        }


        private Integer _orderNum;

    
        @PropMeta(propId=4)
    
        public Integer getOrderNum(){
            return _orderNum;
        }

        public void setOrderNum(Integer value){
            this._orderNum = value;
        }


        private String _deptType;

    
        @PropMeta(propId=5)
    
        public String getDeptType(){
            return _deptType;
        }

        public void setDeptType(String value){
            this._deptType = value;
        }


        private String _managerId;

    
        @PropMeta(propId=6)
    
        public String getManagerId(){
            return _managerId;
        }

        public void setManagerId(String value){
            this._managerId = value;
        }


        private String _email;

    
        @PropMeta(propId=7)
    
        public String getEmail(){
            return _email;
        }

        public void setEmail(String value){
            this._email = value;
        }


        private String _phone;

    
        @PropMeta(propId=8)
    
        public String getPhone(){
            return _phone;
        }

        public void setPhone(String value){
            this._phone = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=9)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
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


        private String _extFlags;

    
        @PropMeta(propId=16)
    
        public String getExtFlags(){
            return _extFlags;
        }

        public void setExtFlags(String value){
            this._extFlags = value;
        }


        private java.util.List<java.lang.String> _relatedGroupList_ids;

    
        public java.util.List<java.lang.String> getRelatedGroupList_ids(){
            return _relatedGroupList_ids;
        }

        public void setRelatedGroupList_ids(java.util.List<java.lang.String> value){
            this._relatedGroupList_ids = value;
        }


        private String _relatedGroupList_label;

    
        public String getRelatedGroupList_label(){
            return _relatedGroupList_label;
        }

        public void setRelatedGroupList_label(String value){
            this._relatedGroupList_label = value;
        }


        private Map<String,Object> _parent;

        public Map<String,Object> getParent(){
            return _parent;
        }

        public void setParent(Map<String,Object> value){
            this._parent = value;
        }


        private Map<String,Object> _manager;

        public Map<String,Object> getManager(){
            return _manager;
        }

        public void setManager(Map<String,Object> value){
            this._manager = value;
        }


        private List<Map<String,Object>> _deptUsers;

        public List<Map<String,Object>> getDeptUsers(){
            return _deptUsers;
        }

        public void setDeptUsers(List<Map<String,Object>> value){
            this._deptUsers = value;
        }


        private List<Map<String,Object>> _children;

        public List<Map<String,Object>> getChildren(){
            return _children;
        }

        public void setChildren(List<Map<String,Object>> value){
            this._children = value;
        }


        private List<Map<String,Object>> _groupMappings;

        public List<Map<String,Object>> getGroupMappings(){
            return _groupMappings;
        }

        public void setGroupMappings(List<Map<String,Object>> value){
            this._groupMappings = value;
        }


        private List<Map<String,Object>> _relatedGroupList;

        public List<Map<String,Object>> getRelatedGroupList(){
            return _relatedGroupList;
        }

        public void setRelatedGroupList(List<Map<String,Object>> value){
            this._relatedGroupList = value;
        }


    }
