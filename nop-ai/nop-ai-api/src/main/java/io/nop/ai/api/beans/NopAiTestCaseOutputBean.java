//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiTestCaseOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _requirementId;

    
        @PropMeta(propId=2)
    
        public String getRequirementId(){
            return _requirementId;
        }

        public void setRequirementId(String value){
            this._requirementId = value;
        }


        private String _testContent;

    
        @PropMeta(propId=3)
    
        public String getTestContent(){
            return _testContent;
        }

        public void setTestContent(String value){
            this._testContent = value;
        }


        private String _testData;

    
        @PropMeta(propId=4)
    
        public String getTestData(){
            return _testData;
        }

        public void setTestData(String value){
            this._testData = value;
        }


        private String _genFileId;

    
        @PropMeta(propId=5)
    
        public String getGenFileId(){
            return _genFileId;
        }

        public void setGenFileId(String value){
            this._genFileId = value;
        }


        private String _chatResponseId;

    
        @PropMeta(propId=6)
    
        public String getChatResponseId(){
            return _chatResponseId;
        }

        public void setChatResponseId(String value){
            this._chatResponseId = value;
        }


        private String _status;

    
        @PropMeta(propId=7)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


        private String _status_label;

    
        public String getStatus_label(){
            return _status_label;
        }

        public void setStatus_label(String value){
            this._status_label = value;
        }


    }
