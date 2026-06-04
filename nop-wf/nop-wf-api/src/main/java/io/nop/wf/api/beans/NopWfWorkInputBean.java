//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfWorkInputBean extends CrudInputBase {

    
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


        private String _remark;

    
        @PropMeta(propId=19)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
