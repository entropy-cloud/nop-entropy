//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthRoleMfaPolicyInputBean extends CrudInputBase {

    
        private String _roleId;

    
        @PropMeta(propId=1)
    
        public String getRoleId(){
            return _roleId;
        }

        public void setRoleId(String value){
            this._roleId = value;
        }


        private Integer _minMfaLevel;

    
        @PropMeta(propId=2)
    
        public Integer getMinMfaLevel(){
            return _minMfaLevel;
        }

        public void setMinMfaLevel(Integer value){
            this._minMfaLevel = value;
        }


        private Byte _allowTrustedDevice;

    
        @PropMeta(propId=3)
    
        public Byte getAllowTrustedDevice(){
            return _allowTrustedDevice;
        }

        public void setAllowTrustedDevice(Byte value){
            this._allowTrustedDevice = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=4)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private String _tenantId;

    
        @PropMeta(propId=6)
    
        public String getTenantId(){
            return _tenantId;
        }

        public void setTenantId(String value){
            this._tenantId = value;
        }


        private String _remark;

    
        @PropMeta(propId=11)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
