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
    public class NopAuthRoleOutputBean {

    
        private String _roleId;

    
        @PropMeta(propId=1)
    
        public String getRoleId(){
            return _roleId;
        }

        public void setRoleId(String value){
            this._roleId = value;
        }


        private String _roleName;

    
        @PropMeta(propId=2)
    
        public String getRoleName(){
            return _roleName;
        }

        public void setRoleName(String value){
            this._roleName = value;
        }


        private String _childRoleIds;

    
        @PropMeta(propId=3)
    
        public String getChildRoleIds(){
            return _childRoleIds;
        }

        public void setChildRoleIds(String value){
            this._childRoleIds = value;
        }


        private Byte _isPrimary;

    
        @PropMeta(propId=4)
    
        public Byte getIsPrimary(){
            return _isPrimary;
        }

        public void setIsPrimary(Byte value){
            this._isPrimary = value;
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


        private java.util.List<java.lang.String> _relatedUserList_ids;

    
        public java.util.List<java.lang.String> getRelatedUserList_ids(){
            return _relatedUserList_ids;
        }

        public void setRelatedUserList_ids(java.util.List<java.lang.String> value){
            this._relatedUserList_ids = value;
        }


        private String _relatedUserList_label;

    
        public String getRelatedUserList_label(){
            return _relatedUserList_label;
        }

        public void setRelatedUserList_label(String value){
            this._relatedUserList_label = value;
        }


        private java.util.List<java.lang.String> _relatedResourceList_ids;

    
        public java.util.List<java.lang.String> getRelatedResourceList_ids(){
            return _relatedResourceList_ids;
        }

        public void setRelatedResourceList_ids(java.util.List<java.lang.String> value){
            this._relatedResourceList_ids = value;
        }


        private String _relatedResourceList_label;

    
        public String getRelatedResourceList_label(){
            return _relatedResourceList_label;
        }

        public void setRelatedResourceList_label(String value){
            this._relatedResourceList_label = value;
        }


        private java.util.List<java.lang.String> _roleResourceIds;

    
        public java.util.List<java.lang.String> getRoleResourceIds(){
            return _roleResourceIds;
        }

        public void setRoleResourceIds(java.util.List<java.lang.String> value){
            this._roleResourceIds = value;
        }


        private List<Map<String,Object>> _userMappings;

        public List<Map<String,Object>> getUserMappings(){
            return _userMappings;
        }

        public void setUserMappings(List<Map<String,Object>> value){
            this._userMappings = value;
        }


        private List<Map<String,Object>> _resourceMappings;

        public List<Map<String,Object>> getResourceMappings(){
            return _resourceMappings;
        }

        public void setResourceMappings(List<Map<String,Object>> value){
            this._resourceMappings = value;
        }


        private List<Map<String,Object>> _relatedUserList;

        public List<Map<String,Object>> getRelatedUserList(){
            return _relatedUserList;
        }

        public void setRelatedUserList(List<Map<String,Object>> value){
            this._relatedUserList = value;
        }


        private List<Map<String,Object>> _relatedResourceList;

        public List<Map<String,Object>> getRelatedResourceList(){
            return _relatedResourceList;
        }

        public void setRelatedResourceList(List<Map<String,Object>> value){
            this._relatedResourceList = value;
        }


        private List<Map<String,Object>> _roleUsers;

        public List<Map<String,Object>> getRoleUsers(){
            return _roleUsers;
        }

        public void setRoleUsers(List<Map<String,Object>> value){
            this._roleUsers = value;
        }


    }
