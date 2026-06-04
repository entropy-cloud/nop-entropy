//__XGEN_FORCE_OVERRIDE__
    package io.nop.report.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopReportResultFileInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=16)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
