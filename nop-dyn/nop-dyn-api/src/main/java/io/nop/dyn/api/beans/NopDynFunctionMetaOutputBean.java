//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynFunctionMetaOutputBean {

    
        private String _funcMetaId;

    
        @PropMeta(propId=1)
    
        public String getFuncMetaId(){
            return _funcMetaId;
        }

        public void setFuncMetaId(String value){
            this._funcMetaId = value;
        }


        private String _entityMetaId;

    
        @PropMeta(propId=2)
    
        public String getEntityMetaId(){
            return _entityMetaId;
        }

        public void setEntityMetaId(String value){
            this._entityMetaId = value;
        }


        private String _name;

    
        @PropMeta(propId=3)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _displayName;

    
        @PropMeta(propId=4)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private String _functionType;

    
        @PropMeta(propId=5)
    
        public String getFunctionType(){
            return _functionType;
        }

        public void setFunctionType(String value){
            this._functionType = value;
        }


        private String _functionType_label;

    
        public String getFunctionType_label(){
            return _functionType_label;
        }

        public void setFunctionType_label(String value){
            this._functionType_label = value;
        }


        private String _returnType;

    
        @PropMeta(propId=6)
    
        public String getReturnType(){
            return _returnType;
        }

        public void setReturnType(String value){
            this._returnType = value;
        }


        private String _returnGqlType;

    
        @PropMeta(propId=7)
    
        public String getReturnGqlType(){
            return _returnGqlType;
        }

        public void setReturnGqlType(String value){
            this._returnGqlType = value;
        }


        private Integer _status;

    
        @PropMeta(propId=8)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private String _status_label;

    
        public String getStatus_label(){
            return _status_label;
        }

        public void setStatus_label(String value){
            this._status_label = value;
        }


        private String _tagsText;

    
        @PropMeta(propId=9)
    
        public String getTagsText(){
            return _tagsText;
        }

        public void setTagsText(String value){
            this._tagsText = value;
        }


        private String _scriptLang;

    
        @PropMeta(propId=10)
    
        public String getScriptLang(){
            return _scriptLang;
        }

        public void setScriptLang(String value){
            this._scriptLang = value;
        }


        private String _scriptLang_label;

    
        public String getScriptLang_label(){
            return _scriptLang_label;
        }

        public void setScriptLang_label(String value){
            this._scriptLang_label = value;
        }


        private String _funcMeta;

    
        @PropMeta(propId=11)
    
        public String getFuncMeta(){
            return _funcMeta;
        }

        public void setFuncMeta(String value){
            this._funcMeta = value;
        }


        private String _source;

    
        @PropMeta(propId=12)
    
        public String getSource(){
            return _source;
        }

        public void setSource(String value){
            this._source = value;
        }


        private Integer _version;

    
        @PropMeta(propId=13)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=14)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=15)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=16)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=17)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=18)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Object _args;

    
        public Object getArgs(){
            return _args;
        }

        public void setArgs(Object value){
            this._args = value;
        }


        private Map<String,Object> _entityMeta;

        public Map<String,Object> getEntityMeta(){
            return _entityMeta;
        }

        public void setEntityMeta(Map<String,Object> value){
            this._entityMeta = value;
        }


    }
