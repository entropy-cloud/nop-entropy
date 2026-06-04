//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiKnowledgeInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _title;

    
        @PropMeta(propId=2)
    
        public String getTitle(){
            return _title;
        }

        public void setTitle(String value){
            this._title = value;
        }


        private String _content;

    
        @PropMeta(propId=3)
    
        public String getContent(){
            return _content;
        }

        public void setContent(String value){
            this._content = value;
        }


        private String _format;

    
        @PropMeta(propId=4)
    
        public String getFormat(){
            return _format;
        }

        public void setFormat(String value){
            this._format = value;
        }


    }
