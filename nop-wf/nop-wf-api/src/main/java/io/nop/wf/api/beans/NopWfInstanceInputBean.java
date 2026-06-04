//__XGEN_FORCE_OVERRIDE__
    package io.nop.wf.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopWfInstanceInputBean extends CrudInputBase {

    
        private String _wfId;

    
        @PropMeta(propId=1)
    
        public String getWfId(){
            return _wfId;
        }

        public void setWfId(String value){
            this._wfId = value;
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


        private String _wfParams;

    
        @PropMeta(propId=4)
    
        public String getWfParams(){
            return _wfParams;
        }

        public void setWfParams(String value){
            this._wfParams = value;
        }


        private String _wfGroup;

    
        @PropMeta(propId=5)
    
        public String getWfGroup(){
            return _wfGroup;
        }

        public void setWfGroup(String value){
            this._wfGroup = value;
        }


        private String _workScope;

    
        @PropMeta(propId=6)
    
        public String getWorkScope(){
            return _workScope;
        }

        public void setWorkScope(String value){
            this._workScope = value;
        }


        private String _title;

    
        @PropMeta(propId=7)
    
        public String getTitle(){
            return _title;
        }

        public void setTitle(String value){
            this._title = value;
        }


        private Integer _status;

    
        @PropMeta(propId=8)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _appState;

    
        @PropMeta(propId=9)
    
        public String getAppState(){
            return _appState;
        }

        public void setAppState(String value){
            this._appState = value;
        }


        private java.sql.Timestamp _startTime;

    
        @PropMeta(propId=10)
    
        public java.sql.Timestamp getStartTime(){
            return _startTime;
        }

        public void setStartTime(java.sql.Timestamp value){
            this._startTime = value;
        }


        private java.sql.Timestamp _endTime;

    
        @PropMeta(propId=11)
    
        public java.sql.Timestamp getEndTime(){
            return _endTime;
        }

        public void setEndTime(java.sql.Timestamp value){
            this._endTime = value;
        }


        private java.sql.Timestamp _dueTime;

    
        @PropMeta(propId=12)
    
        public java.sql.Timestamp getDueTime(){
            return _dueTime;
        }

        public void setDueTime(java.sql.Timestamp value){
            this._dueTime = value;
        }


        private String _bizKey;

    
        @PropMeta(propId=13)
    
        public String getBizKey(){
            return _bizKey;
        }

        public void setBizKey(String value){
            this._bizKey = value;
        }


        private String _bizObjName;

    
        @PropMeta(propId=14)
    
        public String getBizObjName(){
            return _bizObjName;
        }

        public void setBizObjName(String value){
            this._bizObjName = value;
        }


        private String _bizObjId;

    
        @PropMeta(propId=15)
    
        public String getBizObjId(){
            return _bizObjId;
        }

        public void setBizObjId(String value){
            this._bizObjId = value;
        }


        private String _parentWfName;

    
        @PropMeta(propId=16)
    
        public String getParentWfName(){
            return _parentWfName;
        }

        public void setParentWfName(String value){
            this._parentWfName = value;
        }


        private Long _parentWfVersion;

    
        @PropMeta(propId=17)
    
        public Long getParentWfVersion(){
            return _parentWfVersion;
        }

        public void setParentWfVersion(Long value){
            this._parentWfVersion = value;
        }


        private String _parentWfId;

    
        @PropMeta(propId=18)
    
        public String getParentWfId(){
            return _parentWfId;
        }

        public void setParentWfId(String value){
            this._parentWfId = value;
        }


        private String _parentStepId;

    
        @PropMeta(propId=19)
    
        public String getParentStepId(){
            return _parentStepId;
        }

        public void setParentStepId(String value){
            this._parentStepId = value;
        }


        private String _starterId;

    
        @PropMeta(propId=20)
    
        public String getStarterId(){
            return _starterId;
        }

        public void setStarterId(String value){
            this._starterId = value;
        }


        private String _starterName;

    
        @PropMeta(propId=21)
    
        public String getStarterName(){
            return _starterName;
        }

        public void setStarterName(String value){
            this._starterName = value;
        }


        private String _starterDeptId;

    
        @PropMeta(propId=22)
    
        public String getStarterDeptId(){
            return _starterDeptId;
        }

        public void setStarterDeptId(String value){
            this._starterDeptId = value;
        }


        private String _lastOperatorId;

    
        @PropMeta(propId=23)
    
        public String getLastOperatorId(){
            return _lastOperatorId;
        }

        public void setLastOperatorId(String value){
            this._lastOperatorId = value;
        }


        private String _lastOperatorName;

    
        @PropMeta(propId=24)
    
        public String getLastOperatorName(){
            return _lastOperatorName;
        }

        public void setLastOperatorName(String value){
            this._lastOperatorName = value;
        }


        private String _lastOperatorDeptId;

    
        @PropMeta(propId=25)
    
        public String getLastOperatorDeptId(){
            return _lastOperatorDeptId;
        }

        public void setLastOperatorDeptId(String value){
            this._lastOperatorDeptId = value;
        }


        private java.sql.Timestamp _lastOperateTime;

    
        @PropMeta(propId=26)
    
        public java.sql.Timestamp getLastOperateTime(){
            return _lastOperateTime;
        }

        public void setLastOperateTime(java.sql.Timestamp value){
            this._lastOperateTime = value;
        }


        private String _managerType;

    
        @PropMeta(propId=27)
    
        public String getManagerType(){
            return _managerType;
        }

        public void setManagerType(String value){
            this._managerType = value;
        }


        private String _managerDeptId;

    
        @PropMeta(propId=28)
    
        public String getManagerDeptId(){
            return _managerDeptId;
        }

        public void setManagerDeptId(String value){
            this._managerDeptId = value;
        }


        private String _managerName;

    
        @PropMeta(propId=29)
    
        public String getManagerName(){
            return _managerName;
        }

        public void setManagerName(String value){
            this._managerName = value;
        }


        private String _managerId;

    
        @PropMeta(propId=30)
    
        public String getManagerId(){
            return _managerId;
        }

        public void setManagerId(String value){
            this._managerId = value;
        }


        private Integer _priority;

    
        @PropMeta(propId=31)
    
        public Integer getPriority(){
            return _priority;
        }

        public void setPriority(Integer value){
            this._priority = value;
        }


        private String _signalText;

    
        @PropMeta(propId=32)
    
        public String getSignalText(){
            return _signalText;
        }

        public void setSignalText(String value){
            this._signalText = value;
        }


        private String _tagText;

    
        @PropMeta(propId=33)
    
        public String getTagText(){
            return _tagText;
        }

        public void setTagText(String value){
            this._tagText = value;
        }


        private String _remark;

    
        @PropMeta(propId=40)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
