//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthRoleDataAuthInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=14)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
