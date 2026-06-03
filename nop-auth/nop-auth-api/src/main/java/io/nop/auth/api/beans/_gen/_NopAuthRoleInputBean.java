//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthRoleInputBean extends CrudInputBase {

    
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


        private List<_NopAuthUserRoleInputBean> _userMappings;

        public List<_NopAuthUserRoleInputBean> getUserMappings(){
            return _userMappings;
        }

        public void setUserMappings(List<_NopAuthUserRoleInputBean> value){
            this._userMappings = value;
        }


        private List<_NopAuthRoleResourceInputBean> _resourceMappings;

        public List<_NopAuthRoleResourceInputBean> getResourceMappings(){
            return _resourceMappings;
        }

        public void setResourceMappings(List<_NopAuthRoleResourceInputBean> value){
            this._resourceMappings = value;
        }


    }
