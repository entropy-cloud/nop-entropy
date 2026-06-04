//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysChangeLogOutputBean {

    
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


        private String _objId;

    
        @PropMeta(propId=3)
    
        public String getObjId(){
            return _objId;
        }

        public void setObjId(String value){
            this._objId = value;
        }


        private String _bizKey;

    
        @PropMeta(propId=4)
    
        public String getBizKey(){
            return _bizKey;
        }

        public void setBizKey(String value){
            this._bizKey = value;
        }


        private String _operationName;

    
        @PropMeta(propId=5)
    
        public String getOperationName(){
            return _operationName;
        }

        public void setOperationName(String value){
            this._operationName = value;
        }


        private String _propName;

    
        @PropMeta(propId=6)
    
        public String getPropName(){
            return _propName;
        }

        public void setPropName(String value){
            this._propName = value;
        }


        private String _oldValue;

    
        @PropMeta(propId=7)
    
        public String getOldValue(){
            return _oldValue;
        }

        public void setOldValue(String value){
            this._oldValue = value;
        }


        private String _newValue;

    
        @PropMeta(propId=8)
    
        public String getNewValue(){
            return _newValue;
        }

        public void setNewValue(String value){
            this._newValue = value;
        }


        private java.sql.Timestamp _changeTime;

    
        @PropMeta(propId=9)
    
        public java.sql.Timestamp getChangeTime(){
            return _changeTime;
        }

        public void setChangeTime(java.sql.Timestamp value){
            this._changeTime = value;
        }


        private String _appId;

    
        @PropMeta(propId=10)
    
        public String getAppId(){
            return _appId;
        }

        public void setAppId(String value){
            this._appId = value;
        }


        private String _operatorId;

    
        @PropMeta(propId=11)
    
        public String getOperatorId(){
            return _operatorId;
        }

        public void setOperatorId(String value){
            this._operatorId = value;
        }


        private String _approverId;

    
        @PropMeta(propId=12)
    
        public String getApproverId(){
            return _approverId;
        }

        public void setApproverId(String value){
            this._approverId = value;
        }


    }
