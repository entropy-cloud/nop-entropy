//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthGroupDeptOutputBean {

    
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


        private Integer _version;

    
        @PropMeta(propId=4)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=5)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=6)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=7)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=9)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _dept;

        public Map<String,Object> getDept(){
            return _dept;
        }

        public void setDept(Map<String,Object> value){
            this._dept = value;
        }


        private Map<String,Object> _group;

        public Map<String,Object> getGroup(){
            return _group;
        }

        public void setGroup(Map<String,Object> value){
            this._group = value;
        }


    }
