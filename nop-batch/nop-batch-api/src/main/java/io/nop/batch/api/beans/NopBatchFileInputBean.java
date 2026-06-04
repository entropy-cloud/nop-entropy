//__XGEN_FORCE_OVERRIDE__
    package io.nop.batch.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopBatchFileInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=15)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
