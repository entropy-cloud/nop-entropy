//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthRoleInputBean extends CrudInputBase {

    
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


        private java.util.List<java.lang.String> _relatedResourceList_ids;

    
        public java.util.List<java.lang.String> getRelatedResourceList_ids(){
            return _relatedResourceList_ids;
        }

        public void setRelatedResourceList_ids(java.util.List<java.lang.String> value){
            this._relatedResourceList_ids = value;
        }


        private List<NopAuthUserRoleInputBean> _userMappings;

        public List<NopAuthUserRoleInputBean> getUserMappings(){
            return _userMappings;
        }

        public void setUserMappings(List<NopAuthUserRoleInputBean> value){
            this._userMappings = value;
        }


        private List<NopAuthRoleResourceInputBean> _resourceMappings;

        public List<NopAuthRoleResourceInputBean> getResourceMappings(){
            return _resourceMappings;
        }

        public void setResourceMappings(List<NopAuthRoleResourceInputBean> value){
            this._resourceMappings = value;
        }


        private List<NopAuthUserInputBean> _relatedUserList;

        public List<NopAuthUserInputBean> getRelatedUserList(){
            return _relatedUserList;
        }

        public void setRelatedUserList(List<NopAuthUserInputBean> value){
            this._relatedUserList = value;
        }


        private List<NopAuthResourceInputBean> _relatedResourceList;

        public List<NopAuthResourceInputBean> getRelatedResourceList(){
            return _relatedResourceList;
        }

        public void setRelatedResourceList(List<NopAuthResourceInputBean> value){
            this._relatedResourceList = value;
        }


    }
