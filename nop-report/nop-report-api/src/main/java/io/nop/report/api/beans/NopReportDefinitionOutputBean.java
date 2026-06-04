//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportDefinitionOutputBean {

    
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


        private String _status_label;

    
        public String getStatus_label(){
            return _status_label;
        }

        public void setStatus_label(String value){
            this._status_label = value;
        }


        private Integer _version;

    
        @PropMeta(propId=7)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=8)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=10)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=12)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<Map<String,Object>> _reportAuths;

        public List<Map<String,Object>> getReportAuths(){
            return _reportAuths;
        }

        public void setReportAuths(List<Map<String,Object>> value){
            this._reportAuths = value;
        }


        private List<Map<String,Object>> _datasetRefs;

        public List<Map<String,Object>> getDatasetRefs(){
            return _datasetRefs;
        }

        public void setDatasetRefs(List<Map<String,Object>> value){
            this._datasetRefs = value;
        }


    }
