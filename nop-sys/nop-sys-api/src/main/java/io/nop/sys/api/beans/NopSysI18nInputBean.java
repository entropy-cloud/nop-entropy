//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysI18nInputBean extends CrudInputBase {

    
        private String _i18nKey;

    
        @PropMeta(propId=1)
    
        public String getI18nKey(){
            return _i18nKey;
        }

        public void setI18nKey(String value){
            this._i18nKey = value;
        }


        private String _i18nLocale;

    
        @PropMeta(propId=2)
    
        public String getI18nLocale(){
            return _i18nLocale;
        }

        public void setI18nLocale(String value){
            this._i18nLocale = value;
        }


        private String _value;

    
        @PropMeta(propId=3)
    
        public String getValue(){
            return _value;
        }

        public void setValue(String value){
            this._value = value;
        }


        private String _remark;

    
        @PropMeta(propId=9)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


    }
