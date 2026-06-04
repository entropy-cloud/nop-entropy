//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfStatusHistoryOutputBean {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _wfId;

    
        @PropMeta(propId=2)
    
        public String getWfId(){
            return _wfId;
        }

        public void setWfId(String value){
            this._wfId = value;
        }


        private Integer _fromStatus;

    
        @PropMeta(propId=3)
    
        public Integer getFromStatus(){
            return _fromStatus;
        }

        public void setFromStatus(Integer value){
            this._fromStatus = value;
        }


        private String _fromStatus_label;

    
        public String getFromStatus_label(){
            return _fromStatus_label;
        }

        public void setFromStatus_label(String value){
            this._fromStatus_label = value;
        }


        private Integer _toStatus;

    
        @PropMeta(propId=4)
    
        public Integer getToStatus(){
            return _toStatus;
        }

        public void setToStatus(Integer value){
            this._toStatus = value;
        }


        private String _toStatus_label;

    
        public String getToStatus_label(){
            return _toStatus_label;
        }

        public void setToStatus_label(String value){
            this._toStatus_label = value;
        }


        private String _toAppState;

    
        @PropMeta(propId=6)
    
        public String getToAppState(){
            return _toAppState;
        }

        public void setToAppState(String value){
            this._toAppState = value;
        }


        private java.sql.Timestamp _changeTime;

    
        @PropMeta(propId=7)
    
        public java.sql.Timestamp getChangeTime(){
            return _changeTime;
        }

        public void setChangeTime(java.sql.Timestamp value){
            this._changeTime = value;
        }


        private String _operatorId;

    
        @PropMeta(propId=8)
    
        public String getOperatorId(){
            return _operatorId;
        }

        public void setOperatorId(String value){
            this._operatorId = value;
        }


        private String _operatorName;

    
        @PropMeta(propId=9)
    
        public String getOperatorName(){
            return _operatorName;
        }

        public void setOperatorName(String value){
            this._operatorName = value;
        }


        private String _operatorDeptId;

    
        @PropMeta(propId=10)
    
        public String getOperatorDeptId(){
            return _operatorDeptId;
        }

        public void setOperatorDeptId(String value){
            this._operatorDeptId = value;
        }


        private Integer _version;

    
        @PropMeta(propId=11)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=12)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=13)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private Map<String,Object> _wfInstance;

        public Map<String,Object> getWfInstance(){
            return _wfInstance;
        }

        public void setWfInstance(Map<String,Object> value){
            this._wfInstance = value;
        }


    }
