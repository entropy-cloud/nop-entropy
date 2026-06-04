//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportDatasetOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _dsName;

    
        @PropMeta(propId=2)
    
        public String getDsName(){
            return _dsName;
        }

        public void setDsName(String value){
            this._dsName = value;
        }


        private Boolean _isSingleRow;

    
        @PropMeta(propId=3)
    
        public Boolean getIsSingleRow(){
            return _isSingleRow;
        }

        public void setIsSingleRow(Boolean value){
            this._isSingleRow = value;
        }


        private String _description;

    
        @PropMeta(propId=4)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private String _dsType;

    
        @PropMeta(propId=5)
    
        public String getDsType(){
            return _dsType;
        }

        public void setDsType(String value){
            this._dsType = value;
        }


        private String _datasourceId;

    
        @PropMeta(propId=6)
    
        public String getDatasourceId(){
            return _datasourceId;
        }

        public void setDatasourceId(String value){
            this._datasourceId = value;
        }


        private String _dsText;

    
        @PropMeta(propId=7)
    
        public String getDsText(){
            return _dsText;
        }

        public void setDsText(String value){
            this._dsText = value;
        }


        private String _dsMeta;

    
        @PropMeta(propId=8)
    
        public String getDsMeta(){
            return _dsMeta;
        }

        public void setDsMeta(String value){
            this._dsMeta = value;
        }


        private String _dsConfig;

    
        @PropMeta(propId=9)
    
        public String getDsConfig(){
            return _dsConfig;
        }

        public void setDsConfig(String value){
            this._dsConfig = value;
        }


        private String _filterRule;

    
        @PropMeta(propId=10)
    
        public String getFilterRule(){
            return _filterRule;
        }

        public void setFilterRule(String value){
            this._filterRule = value;
        }


        private Integer _status;

    
        @PropMeta(propId=11)
    
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

    
        @PropMeta(propId=12)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=13)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=15)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=16)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=17)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
