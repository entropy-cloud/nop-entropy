//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportSubDatasetInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _dsId;

    
        @PropMeta(propId=2)
    
        public String getDsId(){
            return _dsId;
        }

        public void setDsId(String value){
            this._dsId = value;
        }


        private String _subDsId;

    
        @PropMeta(propId=3)
    
        public String getSubDsId(){
            return _subDsId;
        }

        public void setSubDsId(String value){
            this._subDsId = value;
        }


        private String _joinFields;

    
        @PropMeta(propId=4)
    
        public String getJoinFields(){
            return _joinFields;
        }

        public void setJoinFields(String value){
            this._joinFields = value;
        }


        private String _dsParams;

    
        @PropMeta(propId=5)
    
        public String getDsParams(){
            return _dsParams;
        }

        public void setDsParams(String value){
            this._dsParams = value;
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
