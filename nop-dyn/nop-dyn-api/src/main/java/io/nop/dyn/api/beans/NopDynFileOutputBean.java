//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynFileOutputBean {

    
        private String _fileId;

    
        @PropMeta(propId=1)
    
        public String getFileId(){
            return _fileId;
        }

        public void setFileId(String value){
            this._fileId = value;
        }


        private String _moduleId;

    
        @PropMeta(propId=2)
    
        public String getModuleId(){
            return _moduleId;
        }

        public void setModuleId(String value){
            this._moduleId = value;
        }


        private String _fileName;

    
        @PropMeta(propId=3)
    
        public String getFileName(){
            return _fileName;
        }

        public void setFileName(String value){
            this._fileName = value;
        }


        private String _filePath;

    
        @PropMeta(propId=4)
    
        public String getFilePath(){
            return _filePath;
        }

        public void setFilePath(String value){
            this._filePath = value;
        }


        private String _fileType;

    
        @PropMeta(propId=5)
    
        public String getFileType(){
            return _fileType;
        }

        public void setFileType(String value){
            this._fileType = value;
        }


        private Integer _fileLength;

    
        @PropMeta(propId=6)
    
        public Integer getFileLength(){
            return _fileLength;
        }

        public void setFileLength(Integer value){
            this._fileLength = value;
        }


        private Integer _status;

    
        @PropMeta(propId=7)
    
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

    
        @PropMeta(propId=10)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
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


        private Map<String,Object> _module;

        public Map<String,Object> getModule(){
            return _module;
        }

        public void setModule(Map<String,Object> value){
            this._module = value;
        }


    }
