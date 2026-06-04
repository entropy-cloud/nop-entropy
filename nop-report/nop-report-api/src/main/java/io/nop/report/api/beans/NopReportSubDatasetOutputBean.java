//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportSubDatasetOutputBean {

    
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


        private Integer _version;

    
        @PropMeta(propId=6)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=7)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=9)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=11)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _reportDataset;

        public Map<String,Object> getReportDataset(){
            return _reportDataset;
        }

        public void setReportDataset(Map<String,Object> value){
            this._reportDataset = value;
        }


        private Map<String,Object> _reportSubDataset;

        public Map<String,Object> getReportSubDataset(){
            return _reportSubDataset;
        }

        public void setReportSubDataset(Map<String,Object> value){
            this._reportSubDataset = value;
        }


    }
