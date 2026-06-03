//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthGroupInputBean extends CrudInputBase {

    
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


        private String _remark;

        @PropMeta(propId=11)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<_NopAuthGroupDeptInputBean> _deptMappings;

        public List<_NopAuthGroupDeptInputBean> getDeptMappings(){
            return _deptMappings;
        }

        public void setDeptMappings(List<_NopAuthGroupDeptInputBean> value){
            this._deptMappings = value;
        }


        private List<_NopAuthGroupUserInputBean> _userMappings;

        public List<_NopAuthGroupUserInputBean> getUserMappings(){
            return _userMappings;
        }

        public void setUserMappings(List<_NopAuthGroupUserInputBean> value){
            this._userMappings = value;
        }


    }
