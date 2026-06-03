//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthGroupDeptInputBean extends CrudInputBase {

    
        private String _deptId;

        @PropMeta(propId=1)
        public String getDeptId(){
            return _deptId;
        }

        public void setDeptId(String value){
            this._deptId = value;
        }


        private String _groupId;

        @PropMeta(propId=2)
        public String getGroupId(){
            return _groupId;
        }

        public void setGroupId(String value){
            this._groupId = value;
        }


        private Byte _includeChild;

        @PropMeta(propId=3)
        public Byte getIncludeChild(){
            return _includeChild;
        }

        public void setIncludeChild(Byte value){
            this._includeChild = value;
        }


        private String _remark;

        @PropMeta(propId=9)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
