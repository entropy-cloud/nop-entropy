//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRuleRoleOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _ruleId;

    
        @PropMeta(propId=2)
    
        public String getRuleId(){
            return _ruleId;
        }

        public void setRuleId(String value){
            this._ruleId = value;
        }


        private String _roleId;

    
        @PropMeta(propId=3)
    
        public String getRoleId(){
            return _roleId;
        }

        public void setRoleId(String value){
            this._roleId = value;
        }


        private Byte _isAdmin;

    
        @PropMeta(propId=4)
    
        public Byte getIsAdmin(){
            return _isAdmin;
        }

        public void setIsAdmin(Byte value){
            this._isAdmin = value;
        }


        private Integer _version;

    
        @PropMeta(propId=5)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=6)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=8)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=10)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _ruleDefinition;

        public Map<String,Object> getRuleDefinition(){
            return _ruleDefinition;
        }

        public void setRuleDefinition(Map<String,Object> value){
            this._ruleDefinition = value;
        }


    }
