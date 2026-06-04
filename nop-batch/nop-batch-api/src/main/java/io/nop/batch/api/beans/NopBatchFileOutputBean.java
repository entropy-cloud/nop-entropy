//__XGEN_FORCE_OVERRIDE__
    package io.nop.batch.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopBatchFileOutputBean {

    
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


        private String _filePath;

    
        @PropMeta(propId=3)
    
        public String getFilePath(){
            return _filePath;
        }

        public void setFilePath(String value){
            this._filePath = value;
        }


        private Long _fileLength;

    
        @PropMeta(propId=4)
    
        public Long getFileLength(){
            return _fileLength;
        }

        public void setFileLength(Long value){
            this._fileLength = value;
        }


        private String _fileCategory;

    
        @PropMeta(propId=5)
    
        public String getFileCategory(){
            return _fileCategory;
        }

        public void setFileCategory(String value){
            this._fileCategory = value;
        }


        private String _fileSource;

    
        @PropMeta(propId=6)
    
        public String getFileSource(){
            return _fileSource;
        }

        public void setFileSource(String value){
            this._fileSource = value;
        }


        private String _batchTaskId;

    
        @PropMeta(propId=7)
    
        public String getBatchTaskId(){
            return _batchTaskId;
        }

        public void setBatchTaskId(String value){
            this._batchTaskId = value;
        }


        private String _processState;

    
        @PropMeta(propId=8)
    
        public String getProcessState(){
            return _processState;
        }

        public void setProcessState(String value){
            this._processState = value;
        }


        private java.time.LocalDate _acceptDate;

    
        @PropMeta(propId=9)
    
        public java.time.LocalDate getAcceptDate(){
            return _acceptDate;
        }

        public void setAcceptDate(java.time.LocalDate value){
            this._acceptDate = value;
        }


        private Long _version;

    
        @PropMeta(propId=10)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=11)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=13)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=15)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
