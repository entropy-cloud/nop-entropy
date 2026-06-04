//__XGEN_FORCE_OVERRIDE__
    package io.nop.rule.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopRuleRoleInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=10)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
