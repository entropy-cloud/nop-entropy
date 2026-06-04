//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportDatasetRefOutputBean {

    
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


        private Integer _version;

    
        @PropMeta(propId=3)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=4)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=6)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=8)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _reportDefinition;

        public Map<String,Object> getReportDefinition(){
            return _reportDefinition;
        }

        public void setReportDefinition(Map<String,Object> value){
            this._reportDefinition = value;
        }


    }
