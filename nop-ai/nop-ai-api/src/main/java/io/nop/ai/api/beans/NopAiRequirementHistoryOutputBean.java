//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiRequirementHistoryOutputBean {

    
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


        private String _version;

    
        @PropMeta(propId=3)
    
        public String getVersion(){
            return _version;
        }

        public void setVersion(String value){
            this._version = value;
        }


        private String _content;

    
        @PropMeta(propId=4)
    
        public String getContent(){
            return _content;
        }

        public void setContent(String value){
            this._content = value;
        }


    }
