//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfDefinitionInputBean extends CrudInputBase {

    
        private String _wfDefId;

    
        @PropMeta(propId=1)
    
        public String getWfDefId(){
            return _wfDefId;
        }

        public void setWfDefId(String value){
            this._wfDefId = value;
        }


        private String _wfName;

    
        @PropMeta(propId=2)
    
        public String getWfName(){
            return _wfName;
        }

        public void setWfName(String value){
            this._wfName = value;
        }


        private Long _wfVersion;

    
        @PropMeta(propId=3)
    
        public Long getWfVersion(){
            return _wfVersion;
        }

        public void setWfVersion(Long value){
            this._wfVersion = value;
        }


        private String _displayName;

    
        @PropMeta(propId=4)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _description;

    
        @PropMeta(propId=5)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private String _modelText;

    
        @PropMeta(propId=6)
    
        public String getModelText(){
            return _modelText;
        }

        public void setModelText(String value){
            this._modelText = value;
        }


        private String _formPath;

    
        @PropMeta(propId=7)
    
        public String getFormPath(){
            return _formPath;
        }

        public void setFormPath(String value){
            this._formPath = value;
        }


        private Integer _status;

    
        @PropMeta(propId=8)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _publishedBy;

    
        @PropMeta(propId=9)
    
        public String getPublishedBy(){
            return _publishedBy;
        }

        public void setPublishedBy(String value){
            this._publishedBy = value;
        }


        private java.time.LocalDateTime _publishTime;

    
        @PropMeta(propId=10)
    
        public java.time.LocalDateTime getPublishTime(){
            return _publishTime;
        }

        public void setPublishTime(java.time.LocalDateTime value){
            this._publishTime = value;
        }


        private String _archivedBy;

    
        @PropMeta(propId=11)
    
        public String getArchivedBy(){
            return _archivedBy;
        }

        public void setArchivedBy(String value){
            this._archivedBy = value;
        }


        private java.time.LocalDateTime _archiveTime;

    
        @PropMeta(propId=12)
    
        public java.time.LocalDateTime getArchiveTime(){
            return _archiveTime;
        }

        public void setArchiveTime(java.time.LocalDateTime value){
            this._archiveTime = value;
        }


        private Boolean _isDeprecated;

    
        @PropMeta(propId=13)
    
        public Boolean getIsDeprecated(){
            return _isDeprecated;
        }

        public void setIsDeprecated(Boolean value){
            this._isDeprecated = value;
        }


        private String _remark;

    
        @PropMeta(propId=19)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<NopWfDefinitionAuthInputBean> _definitionAuths;

        public List<NopWfDefinitionAuthInputBean> getDefinitionAuths(){
            return _definitionAuths;
        }

        public void setDefinitionAuths(List<NopWfDefinitionAuthInputBean> value){
            this._definitionAuths = value;
        }


    }
