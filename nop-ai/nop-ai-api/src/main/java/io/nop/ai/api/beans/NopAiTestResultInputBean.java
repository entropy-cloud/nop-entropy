//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiTestResultInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _testCaseId;

    
        @PropMeta(propId=2)
    
        public String getTestCaseId(){
            return _testCaseId;
        }

        public void setTestCaseId(String value){
            this._testCaseId = value;
        }


        private java.sql.Timestamp _executionTime;

    
        @PropMeta(propId=3)
    
        public java.sql.Timestamp getExecutionTime(){
            return _executionTime;
        }

        public void setExecutionTime(java.sql.Timestamp value){
            this._executionTime = value;
        }


        private Boolean _success;

    
        @PropMeta(propId=4)
    
        public Boolean getSuccess(){
            return _success;
        }

        public void setSuccess(Boolean value){
            this._success = value;
        }


        private String _errorLog;

    
        @PropMeta(propId=5)
    
        public String getErrorLog(){
            return _errorLog;
        }

        public void setErrorLog(String value){
            this._errorLog = value;
        }


    }
