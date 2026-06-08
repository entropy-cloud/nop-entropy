//__XGEN_FORCE_OVERRIDE__
    package io.nop.ai.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAiSessionContextInputBean extends CrudInputBase {

    
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


        private String _baseline;

    
        @PropMeta(propId=3)
    
        public String getBaseline(){
            return _baseline;
        }

        public void setBaseline(String value){
            this._baseline = value;
        }


        private String _snapshot;

    
        @PropMeta(propId=4)
    
        public String getSnapshot(){
            return _snapshot;
        }

        public void setSnapshot(String value){
            this._snapshot = value;
        }


        private Long _baselineSeq;

    
        @PropMeta(propId=5)
    
        public Long getBaselineSeq(){
            return _baselineSeq;
        }

        public void setBaselineSeq(Long value){
            this._baselineSeq = value;
        }


        private Long _replacementSeq;

    
        @PropMeta(propId=6)
    
        public Long getReplacementSeq(){
            return _replacementSeq;
        }

        public void setReplacementSeq(Long value){
            this._replacementSeq = value;
        }


    }
