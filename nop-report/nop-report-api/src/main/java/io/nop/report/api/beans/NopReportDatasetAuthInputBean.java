//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportDatasetAuthInputBean extends CrudInputBase {

    
        private String _dsId;

    
        @PropMeta(propId=1)
    
        public String getDsId(){
            return _dsId;
        }

        public void setDsId(String value){
            this._dsId = value;
        }


        private String _roleId;

    
        @PropMeta(propId=2)
    
        public String getRoleId(){
            return _roleId;
        }

        public void setRoleId(String value){
            this._roleId = value;
        }


        private String _permissions;

    
        @PropMeta(propId=3)
    
        public String getPermissions(){
            return _permissions;
        }

        public void setPermissions(String value){
            this._permissions = value;
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
