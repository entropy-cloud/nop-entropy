//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans._gen;

    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class _NopAuthGroupUserInputBean extends CrudInputBase {

    
        private String _userId;

        @PropMeta(propId=1)
        public String getUserId(){
            return _userId;
        }

        public void setUserId(String value){
            this._userId = value;
        }


        private String _groupId;

        @PropMeta(propId=2)
        public String getGroupId(){
            return _groupId;
        }

        public void setGroupId(String value){
            this._groupId = value;
        }


        private String _remark;

        @PropMeta(propId=8)
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
