//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthGroupInputBean extends CrudInputBase {

    
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


        private java.util.List<java.lang.String> _relatedDeptList_ids;

    
        public java.util.List<java.lang.String> getRelatedDeptList_ids(){
            return _relatedDeptList_ids;
        }

        public void setRelatedDeptList_ids(java.util.List<java.lang.String> value){
            this._relatedDeptList_ids = value;
        }


        private java.util.List<java.lang.String> _relatedUserList_ids;

    
        public java.util.List<java.lang.String> getRelatedUserList_ids(){
            return _relatedUserList_ids;
        }

        public void setRelatedUserList_ids(java.util.List<java.lang.String> value){
            this._relatedUserList_ids = value;
        }


        private List<NopAuthGroupDeptInputBean> _deptMappings;

        public List<NopAuthGroupDeptInputBean> getDeptMappings(){
            return _deptMappings;
        }

        public void setDeptMappings(List<NopAuthGroupDeptInputBean> value){
            this._deptMappings = value;
        }


        private List<NopAuthGroupUserInputBean> _userMappings;

        public List<NopAuthGroupUserInputBean> getUserMappings(){
            return _userMappings;
        }

        public void setUserMappings(List<NopAuthGroupUserInputBean> value){
            this._userMappings = value;
        }


        private List<NopAuthDeptInputBean> _relatedDeptList;

        public List<NopAuthDeptInputBean> getRelatedDeptList(){
            return _relatedDeptList;
        }

        public void setRelatedDeptList(List<NopAuthDeptInputBean> value){
            this._relatedDeptList = value;
        }


        private List<NopAuthUserInputBean> _relatedUserList;

        public List<NopAuthUserInputBean> getRelatedUserList(){
            return _relatedUserList;
        }

        public void setRelatedUserList(List<NopAuthUserInputBean> value){
            this._relatedUserList = value;
        }


    }
