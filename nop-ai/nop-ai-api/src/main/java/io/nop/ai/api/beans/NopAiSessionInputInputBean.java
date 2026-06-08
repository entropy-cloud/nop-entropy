//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiSessionInputInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _sessionId;

    
        @PropMeta(propId=2)
    
        public String getSessionId(){
            return _sessionId;
        }

        public void setSessionId(String value){
            this._sessionId = value;
        }


        private String _prompt;

    
        @PropMeta(propId=3)
    
        public String getPrompt(){
            return _prompt;
        }

        public void setPrompt(String value){
            this._prompt = value;
        }


        private Integer _delivery;

    
        @PropMeta(propId=4)
    
        public Integer getDelivery(){
            return _delivery;
        }

        public void setDelivery(Integer value){
            this._delivery = value;
        }


        private Long _admittedSeq;

    
        @PropMeta(propId=5)
    
        public Long getAdmittedSeq(){
            return _admittedSeq;
        }

        public void setAdmittedSeq(Long value){
            this._admittedSeq = value;
        }


        private Long _promotedSeq;

    
        @PropMeta(propId=6)
    
        public Long getPromotedSeq(){
            return _promotedSeq;
        }

        public void setPromotedSeq(Long value){
            this._promotedSeq = value;
        }


        private Integer _version;

    
        @PropMeta(propId=7)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


    }
