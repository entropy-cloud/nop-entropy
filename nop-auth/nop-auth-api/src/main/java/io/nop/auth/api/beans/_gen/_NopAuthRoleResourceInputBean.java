//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthRoleResourceInputBean extends CrudInputBase {

    
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


        private String _remark;

        @PropMeta(propId=10)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
