//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfWorkOutputBean {

    
        private String _workId;

    
        @PropMeta(propId=1)
    
        public String getWorkId(){
            return _workId;
        }

        public void setWorkId(String value){
            this._workId = value;
        }


        private String _wfId;

    
        @PropMeta(propId=2)
    
        public String getWfId(){
            return _wfId;
        }

        public void setWfId(String value){
            this._wfId = value;
        }


        private String _stepId;

    
        @PropMeta(propId=3)
    
        public String getStepId(){
            return _stepId;
        }

        public void setStepId(String value){
            this._stepId = value;
        }


        private String _workType;

    
        @PropMeta(propId=4)
    
        public String getWorkType(){
            return _workType;
        }

        public void setWorkType(String value){
            this._workType = value;
        }


        private String _title;

    
        @PropMeta(propId=5)
    
        public String getTitle(){
            return _title;
        }

        public void setTitle(String value){
            this._title = value;
        }


        private String _linkUrl;

    
        @PropMeta(propId=6)
    
        public String getLinkUrl(){
            return _linkUrl;
        }

        public void setLinkUrl(String value){
            this._linkUrl = value;
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


        private String _ownerId;

    
        @PropMeta(propId=8)
    
        public String getOwnerId(){
            return _ownerId;
        }

        public void setOwnerId(String value){
            this._ownerId = value;
        }


        private String _ownerName;

    
        @PropMeta(propId=9)
    
        public String getOwnerName(){
            return _ownerName;
        }

        public void setOwnerName(String value){
            this._ownerName = value;
        }


        private String _callerId;

    
        @PropMeta(propId=10)
    
        public String getCallerId(){
            return _callerId;
        }

        public void setCallerId(String value){
            this._callerId = value;
        }


        private String _callerName;

    
        @PropMeta(propId=11)
    
        public String getCallerName(){
            return _callerName;
        }

        public void setCallerName(String value){
            this._callerName = value;
        }


        private java.sql.Timestamp _readTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getReadTime(){
            return _readTime;
        }

        public void setReadTime(java.sql.Timestamp value){
            this._readTime = value;
        }


        private java.sql.Timestamp _finishTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getFinishTime(){
            return _finishTime;
        }

        public void setFinishTime(java.sql.Timestamp value){
            this._finishTime = value;
        }


        private Integer _version;

    
        @PropMeta(propId=14)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=15)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=16)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=17)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=18)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=19)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _wfInstance;

        public Map<String,Object> getWfInstance(){
            return _wfInstance;
        }

        public void setWfInstance(Map<String,Object> value){
            this._wfInstance = value;
        }


        private Map<String,Object> _wfStepInstance;

        public Map<String,Object> getWfStepInstance(){
            return _wfStepInstance;
        }

        public void setWfStepInstance(Map<String,Object> value){
            this._wfStepInstance = value;
        }


    }
