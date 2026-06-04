//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthRoleDataAuthOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _roleIds;

    
        @PropMeta(propId=2)
    
        public String getRoleIds(){
            return _roleIds;
        }

        public void setRoleIds(String value){
            this._roleIds = value;
        }


        private String _bizObj;

    
        @PropMeta(propId=3)
    
        public String getBizObj(){
            return _bizObj;
        }

        public void setBizObj(String value){
            this._bizObj = value;
        }


        private Integer _priority;

    
        @PropMeta(propId=4)
    
        public Integer getPriority(){
            return _priority;
        }

        public void setPriority(Integer value){
            this._priority = value;
        }


        private String _filterConfig;

    
        @PropMeta(propId=5)
    
        public String getFilterConfig(){
            return _filterConfig;
        }

        public void setFilterConfig(String value){
            this._filterConfig = value;
        }


        private String _whenConfig;

    
        @PropMeta(propId=6)
    
        public String getWhenConfig(){
            return _whenConfig;
        }

        public void setWhenConfig(String value){
            this._whenConfig = value;
        }


        private String _description;

    
        @PropMeta(propId=7)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=8)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private Integer _version;

    
        @PropMeta(propId=9)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=10)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=12)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=14)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
