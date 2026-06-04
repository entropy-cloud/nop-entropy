//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiPromptTemplateOutputBean {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _name;

    
        @PropMeta(propId=2)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _content;

    
        @PropMeta(propId=3)
    
        public String getContent(){
            return _content;
        }

        public void setContent(String value){
            this._content = value;
        }


        private String _category;

    
        @PropMeta(propId=4)
    
        public String getCategory(){
            return _category;
        }

        public void setCategory(String value){
            this._category = value;
        }


        private String _inputs;

    
        @PropMeta(propId=5)
    
        public String getInputs(){
            return _inputs;
        }

        public void setInputs(String value){
            this._inputs = value;
        }


        private String _outputs;

    
        @PropMeta(propId=6)
    
        public String getOutputs(){
            return _outputs;
        }

        public void setOutputs(String value){
            this._outputs = value;
        }


    }
