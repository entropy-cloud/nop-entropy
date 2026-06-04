//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysCheckerRecordOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _bizObjName;

    
        @PropMeta(propId=2)
    
        public String getBizObjName(){
            return _bizObjName;
        }

        public void setBizObjName(String value){
            this._bizObjName = value;
        }


        private String _bizObjId;

    
        @PropMeta(propId=3)
    
        public String getBizObjId(){
            return _bizObjId;
        }

        public void setBizObjId(String value){
            this._bizObjId = value;
        }


        private String _makerId;

    
        @PropMeta(propId=4)
    
        public String getMakerId(){
            return _makerId;
        }

        public void setMakerId(String value){
            this._makerId = value;
        }


        private String _makerName;

    
        @PropMeta(propId=5)
    
        public String getMakerName(){
            return _makerName;
        }

        public void setMakerName(String value){
            this._makerName = value;
        }


        private String _requestAction;

    
        @PropMeta(propId=6)
    
        public String getRequestAction(){
            return _requestAction;
        }

        public void setRequestAction(String value){
            this._requestAction = value;
        }


        private String _requestData;

    
        @PropMeta(propId=7)
    
        public String getRequestData(){
            return _requestData;
        }

        public void setRequestData(String value){
            this._requestData = value;
        }


        private java.time.LocalDateTime _requestTime;

    
        @PropMeta(propId=8)
    
        public java.time.LocalDateTime getRequestTime(){
            return _requestTime;
        }

        public void setRequestTime(java.time.LocalDateTime value){
            this._requestTime = value;
        }


        private String _checkerId;

    
        @PropMeta(propId=9)
    
        public String getCheckerId(){
            return _checkerId;
        }

        public void setCheckerId(String value){
            this._checkerId = value;
        }


        private String _checkerName;

    
        @PropMeta(propId=10)
    
        public String getCheckerName(){
            return _checkerName;
        }

        public void setCheckerName(String value){
            this._checkerName = value;
        }


        private java.time.LocalDateTime _checkTime;

    
        @PropMeta(propId=11)
    
        public java.time.LocalDateTime getCheckTime(){
            return _checkTime;
        }

        public void setCheckTime(java.time.LocalDateTime value){
            this._checkTime = value;
        }


        private String _tryResult;

    
        @PropMeta(propId=12)
    
        public String getTryResult(){
            return _tryResult;
        }

        public void setTryResult(String value){
            this._tryResult = value;
        }


        private String _inputPage;

    
        @PropMeta(propId=13)
    
        public String getInputPage(){
            return _inputPage;
        }

        public void setInputPage(String value){
            this._inputPage = value;
        }


        private Integer _status;

    
        @PropMeta(propId=14)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _cancelAction;

    
        @PropMeta(propId=15)
    
        public String getCancelAction(){
            return _cancelAction;
        }

        public void setCancelAction(String value){
            this._cancelAction = value;
        }


        private String _cbErrCode;

    
        @PropMeta(propId=16)
    
        public String getCbErrCode(){
            return _cbErrCode;
        }

        public void setCbErrCode(String value){
            this._cbErrCode = value;
        }


        private String _ceErrMsg;

    
        @PropMeta(propId=17)
    
        public String getCeErrMsg(){
            return _ceErrMsg;
        }

        public void setCeErrMsg(String value){
            this._ceErrMsg = value;
        }


        private Long _version;

    
        @PropMeta(propId=18)
    
        public Long getVersion(){
            return _version;
        }

        public void setVersion(Long value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=19)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=20)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=21)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=22)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=23)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
