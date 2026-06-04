//__XGEN_FORCE_OVERRIDE__
    package io.nop.tcc.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import java.util.List;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopTccRecordOutputBean {

    
        private String _txnId;

    
        @PropMeta(propId=1)
    
        public String getTxnId(){
            return _txnId;
        }

        public void setTxnId(String value){
            this._txnId = value;
        }


        private String _txnGroup;

    
        @PropMeta(propId=2)
    
        public String getTxnGroup(){
            return _txnGroup;
        }

        public void setTxnGroup(String value){
            this._txnGroup = value;
        }


        private String _txnName;

    
        @PropMeta(propId=3)
    
        public String getTxnName(){
            return _txnName;
        }

        public void setTxnName(String value){
            this._txnName = value;
        }


        private Integer _status;

    
        @PropMeta(propId=4)
    
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


        private java.sql.Timestamp _expireTime;

    
        @PropMeta(propId=5)
    
        public java.sql.Timestamp getExpireTime(){
            return _expireTime;
        }

        public void setExpireTime(java.sql.Timestamp value){
            this._expireTime = value;
        }


        private String _appId;

    
        @PropMeta(propId=6)
    
        public String getAppId(){
            return _appId;
        }

        public void setAppId(String value){
            this._appId = value;
        }


        private String _appData;

    
        @PropMeta(propId=7)
    
        public String getAppData(){
            return _appData;
        }

        public void setAppData(String value){
            this._appData = value;
        }


        private java.sql.Timestamp _beginTime;

    
        @PropMeta(propId=8)
    
        public java.sql.Timestamp getBeginTime(){
            return _beginTime;
        }

        public void setBeginTime(java.sql.Timestamp value){
            this._beginTime = value;
        }


        private java.sql.Timestamp _endTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.sql.Timestamp value){
            this._endTime = value;
        }


        private String _errorCode;

    
        @PropMeta(propId=10)
    
        public String getErrorCode(){
            return _errorCode;
        }

        public void setErrorCode(String value){
            this._errorCode = value;
        }


        private String _errorMessage;

    
        @PropMeta(propId=11)
    
        public String getErrorMessage(){
            return _errorMessage;
        }

        public void setErrorMessage(String value){
            this._errorMessage = value;
        }


        private String _errorStack;

    
        @PropMeta(propId=12)
    
        public String getErrorStack(){
            return _errorStack;
        }

        public void setErrorStack(String value){
            this._errorStack = value;
        }


        private Integer _version;

    
        @PropMeta(propId=13)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=14)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private List<Map<String,Object>> _branchRecords;

        public List<Map<String,Object>> getBranchRecords(){
            return _branchRecords;
        }

        public void setBranchRecords(List<Map<String,Object>> value){
            this._branchRecords = value;
        }


    }
