//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportResultFileOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _fileName;

    
        @PropMeta(propId=2)
    
        public String getFileName(){
            return _fileName;
        }

        public void setFileName(String value){
            this._fileName = value;
        }


        private String _fileType;

    
        @PropMeta(propId=3)
    
        public String getFileType(){
            return _fileType;
        }

        public void setFileType(String value){
            this._fileType = value;
        }


        private String _filePath;

    
        @PropMeta(propId=4)
    
        public String getFilePath(){
            return _filePath;
        }

        public void setFilePath(String value){
            this._filePath = value;
        }


        private Long _fileLength;

    
        @PropMeta(propId=5)
    
        public Long getFileLength(){
            return _fileLength;
        }

        public void setFileLength(Long value){
            this._fileLength = value;
        }


        private java.time.LocalDate _bizDate;

    
        @PropMeta(propId=6)
    
        public java.time.LocalDate getBizDate(){
            return _bizDate;
        }

        public void setBizDate(java.time.LocalDate value){
            this._bizDate = value;
        }


        private String _rptId;

    
        @PropMeta(propId=7)
    
        public String getRptId(){
            return _rptId;
        }

        public void setRptId(String value){
            this._rptId = value;
        }


        private String _rptParams;

    
        @PropMeta(propId=8)
    
        public String getRptParams(){
            return _rptParams;
        }

        public void setRptParams(String value){
            this._rptParams = value;
        }


        private Integer _status;

    
        @PropMeta(propId=9)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _description;

    
        @PropMeta(propId=10)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private Integer _version;

    
        @PropMeta(propId=11)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=12)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=14)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=16)
    
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
