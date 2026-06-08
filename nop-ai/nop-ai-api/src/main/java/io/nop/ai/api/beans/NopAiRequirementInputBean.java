//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiRequirementInputBean extends CrudInputBase {

    
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


        private String _reqNumber;

    
        @PropMeta(propId=3)
    
        public String getReqNumber(){
            return _reqNumber;
        }

        public void setReqNumber(String value){
            this._reqNumber = value;
        }


        private String _title;

    
        @PropMeta(propId=4)
    
        public String getTitle(){
            return _title;
        }

        public void setTitle(String value){
            this._title = value;
        }


        private String _content;

    
        @PropMeta(propId=5)
    
        public String getContent(){
            return _content;
        }

        public void setContent(String value){
            this._content = value;
        }


        private String _parentId;

    
        @PropMeta(propId=7)
    
        public String getParentId(){
            return _parentId;
        }

        public void setParentId(String value){
            this._parentId = value;
        }


        private String _type;

    
        @PropMeta(propId=8)
    
        public String getType(){
            return _type;
        }

        public void setType(String value){
            this._type = value;
        }


        private String _aiSummary;

    
        @PropMeta(propId=9)
    
        public String getAiSummary(){
            return _aiSummary;
        }

        public void setAiSummary(String value){
            this._aiSummary = value;
        }


        private String _status;

    
        @PropMeta(propId=10)
    
        public String getStatus(){
            return _status;
        }

        public void setStatus(String value){
            this._status = value;
        }


    }
