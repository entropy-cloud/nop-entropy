//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportDefinitionInputBean extends CrudInputBase {

    
        private String _rptId;

    
        @PropMeta(propId=1)
    
        public String getRptId(){
            return _rptId;
        }

        public void setRptId(String value){
            this._rptId = value;
        }


        private String _rptNo;

    
        @PropMeta(propId=2)
    
        public String getRptNo(){
            return _rptNo;
        }

        public void setRptNo(String value){
            this._rptNo = value;
        }


        private String _rptName;

    
        @PropMeta(propId=3)
    
        public String getRptName(){
            return _rptName;
        }

        public void setRptName(String value){
            this._rptName = value;
        }


        private String _description;

    
        @PropMeta(propId=4)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private String _rptText;

    
        @PropMeta(propId=5)
    
        public String getRptText(){
            return _rptText;
        }

        public void setRptText(String value){
            this._rptText = value;
        }


        private Integer _status;

    
        @PropMeta(propId=6)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _remark;

    
        @PropMeta(propId=12)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<NopReportDefinitionAuthInputBean> _reportAuths;

        public List<NopReportDefinitionAuthInputBean> getReportAuths(){
            return _reportAuths;
        }

        public void setReportAuths(List<NopReportDefinitionAuthInputBean> value){
            this._reportAuths = value;
        }


        private List<NopReportDatasetRefInputBean> _datasetRefs;

        public List<NopReportDatasetRefInputBean> getDatasetRefs(){
            return _datasetRefs;
        }

        public void setDatasetRefs(List<NopReportDatasetRefInputBean> value){
            this._datasetRefs = value;
        }


    }
