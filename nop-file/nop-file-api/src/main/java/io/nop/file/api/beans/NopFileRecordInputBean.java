//__XGEN_FORCE_OVERRIDE__
    package io.nop.file.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopFileRecordInputBean extends CrudInputBase {

    
        private String _fileId;

    
        @PropMeta(propId=1)
    
        public String getFileId(){
            return _fileId;
        }

        public void setFileId(String value){
            this._fileId = value;
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


        private String _fileExt;

    
        @PropMeta(propId=4)
    
        public String getFileExt(){
            return _fileExt;
        }

        public void setFileExt(String value){
            this._fileExt = value;
        }


        private String _mimeType;

    
        @PropMeta(propId=5)
    
        public String getMimeType(){
            return _mimeType;
        }

        public void setMimeType(String value){
            this._mimeType = value;
        }


        private Long _fileLength;

    
        @PropMeta(propId=6)
    
        public Long getFileLength(){
            return _fileLength;
        }

        public void setFileLength(Long value){
            this._fileLength = value;
        }


        private java.sql.Timestamp _fileLastModified;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getFileLastModified(){
            return _fileLastModified;
        }

        public void setFileLastModified(java.sql.Timestamp value){
            this._fileLastModified = value;
        }


        private String _bizObjName;

    
        @PropMeta(propId=8)
    
        public String getBizObjName(){
            return _bizObjName;
        }

        public void setBizObjName(String value){
            this._bizObjName = value;
        }


        private String _bizObjId;

    
        @PropMeta(propId=9)
    
        public String getBizObjId(){
            return _bizObjId;
        }

        public void setBizObjId(String value){
            this._bizObjId = value;
        }


        private String _fieldName;

    
        @PropMeta(propId=10)
    
        public String getFieldName(){
            return _fieldName;
        }

        public void setFieldName(String value){
            this._fieldName = value;
        }


        private String _fileHash;

    
        @PropMeta(propId=11)
    
        public String getFileHash(){
            return _fileHash;
        }

        public void setFileHash(String value){
            this._fileHash = value;
        }


        private String _originFileId;

    
        @PropMeta(propId=12)
    
        public String getOriginFileId(){
            return _originFileId;
        }

        public void setOriginFileId(String value){
            this._originFileId = value;
        }


        private Boolean _isPublic;

    
        @PropMeta(propId=13)
    
        public Boolean getIsPublic(){
            return _isPublic;
        }

        public void setIsPublic(Boolean value){
            this._isPublic = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=14)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
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
