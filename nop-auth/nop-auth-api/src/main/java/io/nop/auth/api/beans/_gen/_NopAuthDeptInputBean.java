//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthDeptInputBean extends CrudInputBase {

    
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


        private String _remark;

        @PropMeta(propId=15)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<_NopAuthGroupDeptInputBean> _groupMappings;

        public List<_NopAuthGroupDeptInputBean> getGroupMappings(){
            return _groupMappings;
        }

        public void setGroupMappings(List<_NopAuthGroupDeptInputBean> value){
            this._groupMappings = value;
        }


    }
