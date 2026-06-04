//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportDatasetRefInputBean extends CrudInputBase {

    
        private String _rptId;

    
        @PropMeta(propId=1)
    
        public String getRptId(){
            return _rptId;
        }

        public void setRptId(String value){
            this._rptId = value;
        }


        private String _dsId;

    
        @PropMeta(propId=2)
    
        public String getDsId(){
            return _dsId;
        }

        public void setDsId(String value){
            this._dsId = value;
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
