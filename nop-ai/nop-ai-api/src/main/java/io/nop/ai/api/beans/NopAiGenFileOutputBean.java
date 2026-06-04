//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiGenFileOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _projectId;

    
        @PropMeta(propId=2)
    
        public String getProjectId(){
            return _projectId;
        }

        public void setProjectId(String value){
            this._projectId = value;
        }


        private String _requirementId;

    
        @PropMeta(propId=3)
    
        public String getRequirementId(){
            return _requirementId;
        }

        public void setRequirementId(String value){
            this._requirementId = value;
        }


        private String _moduleType;

    
        @PropMeta(propId=4)
    
        public String getModuleType(){
            return _moduleType;
        }

        public void setModuleType(String value){
            this._moduleType = value;
        }


        private String _moduleType_label;

    
        public String getModuleType_label(){
            return _moduleType_label;
        }

        public void setModuleType_label(String value){
            this._moduleType_label = value;
        }


        private String _content;

    
        @PropMeta(propId=5)
    
        public String getContent(){
            return _content;
        }

        public void setContent(String value){
            this._content = value;
        }


        private String _filePath;

    
        @PropMeta(propId=6)
    
        public String getFilePath(){
            return _filePath;
        }

        public void setFilePath(String value){
            this._filePath = value;
        }


        private String _chatResponseId;

    
        @PropMeta(propId=7)
    
        public String getChatResponseId(){
            return _chatResponseId;
        }

        public void setChatResponseId(String value){
            this._chatResponseId = value;
        }


        private String _status;

    
        @PropMeta(propId=8)
    
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
