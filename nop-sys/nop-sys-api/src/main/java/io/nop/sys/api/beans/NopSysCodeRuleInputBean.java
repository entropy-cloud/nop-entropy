//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysCodeRuleInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _name;

    
        @PropMeta(propId=2)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _displayName;

    
        @PropMeta(propId=3)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _codePattern;

    
        @PropMeta(propId=4)
    
        public String getCodePattern(){
            return _codePattern;
        }

        public void setCodePattern(String value){
            this._codePattern = value;
        }


        private String _seqName;

    
        @PropMeta(propId=5)
    
        public String getSeqName(){
            return _seqName;
        }

        public void setSeqName(String value){
            this._seqName = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=6)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private String _remark;

    
        @PropMeta(propId=12)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
