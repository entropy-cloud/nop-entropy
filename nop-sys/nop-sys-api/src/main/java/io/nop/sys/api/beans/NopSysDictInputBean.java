//__XGEN_FORCE_OVERRIDE__
    package io.nop.sys.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopSysDictInputBean extends CrudInputBase {

    
        private String _sid;

    
        @PropMeta(propId=1)
    
        public String getSid(){
            return _sid;
        }

        public void setSid(String value){
            this._sid = value;
        }


        private String _dictName;

    
        @PropMeta(propId=2)
    
        public String getDictName(){
            return _dictName;
        }

        public void setDictName(String value){
            this._dictName = value;
        }


        private String _displayName;

    
        @PropMeta(propId=3)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=4)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private String _remark;

    
        @PropMeta(propId=10)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private List<NopSysDictOptionInputBean> _dictOptions;

        public List<NopSysDictOptionInputBean> getDictOptions(){
            return _dictOptions;
        }

        public void setDictOptions(List<NopSysDictOptionInputBean> value){
            this._dictOptions = value;
        }


    }
