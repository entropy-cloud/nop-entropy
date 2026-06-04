//__XGEN_FORCE_OVERRIDE__
    package io.nop.dyn.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    
    import java.util.Map;

    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopDynSqlOutputBean {

    
        private String _sqlId;

    
        @PropMeta(propId=1)
    
        public String getSqlId(){
            return _sqlId;
        }

        public void setSqlId(String value){
            this._sqlId = value;
        }


        private String _moduleId;

    
        @PropMeta(propId=2)
    
        public String getModuleId(){
            return _moduleId;
        }

        public void setModuleId(String value){
            this._moduleId = value;
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


        private String _sqlMethod;

    
        @PropMeta(propId=5)
    
        public String getSqlMethod(){
            return _sqlMethod;
        }

        public void setSqlMethod(String value){
            this._sqlMethod = value;
        }


        private String _sqlMethod_label;

    
        public String getSqlMethod_label(){
            return _sqlMethod_label;
        }

        public void setSqlMethod_label(String value){
            this._sqlMethod_label = value;
        }


        private String _rowType;

    
        @PropMeta(propId=6)
    
        public String getRowType(){
            return _rowType;
        }

        public void setRowType(String value){
            this._rowType = value;
        }


        private String _description;

    
        @PropMeta(propId=7)
    
        public String getDescription(){
            return _description;
        }

        public void setDescription(String value){
            this._description = value;
        }


        private String _cacheName;

    
        @PropMeta(propId=8)
    
        public String getCacheName(){
            return _cacheName;
        }

        public void setCacheName(String value){
            this._cacheName = value;
        }


        private String _cacheKeyExpr;

    
        @PropMeta(propId=9)
    
        public String getCacheKeyExpr(){
            return _cacheKeyExpr;
        }

        public void setCacheKeyExpr(String value){
            this._cacheKeyExpr = value;
        }


        private String _batchLoadSelection;

    
        @PropMeta(propId=10)
    
        public String getBatchLoadSelection(){
            return _batchLoadSelection;
        }

        public void setBatchLoadSelection(String value){
            this._batchLoadSelection = value;
        }


        private String _sqlKind;

    
        @PropMeta(propId=11)
    
        public String getSqlKind(){
            return _sqlKind;
        }

        public void setSqlKind(String value){
            this._sqlKind = value;
        }


        private String _sqlKind_label;

    
        public String getSqlKind_label(){
            return _sqlKind_label;
        }

        public void setSqlKind_label(String value){
            this._sqlKind_label = value;
        }


        private String _querySpace;

    
        @PropMeta(propId=12)
    
        public String getQuerySpace(){
            return _querySpace;
        }

        public void setQuerySpace(String value){
            this._querySpace = value;
        }


        private String _source;

    
        @PropMeta(propId=13)
    
        public String getSource(){
            return _source;
        }

        public void setSource(String value){
            this._source = value;
        }


        private Integer _fetchSize;

    
        @PropMeta(propId=14)
    
        public Integer getFetchSize(){
            return _fetchSize;
        }

        public void setFetchSize(Integer value){
            this._fetchSize = value;
        }


        private Integer _timeout;

    
        @PropMeta(propId=15)
    
        public Integer getTimeout(){
            return _timeout;
        }

        public void setTimeout(Integer value){
            this._timeout = value;
        }


        private Byte _disableLogicalDelete;

    
        @PropMeta(propId=16)
    
        public Byte getDisableLogicalDelete(){
            return _disableLogicalDelete;
        }

        public void setDisableLogicalDelete(Byte value){
            this._disableLogicalDelete = value;
        }


        private Byte _enableFilter;

    
        @PropMeta(propId=17)
    
        public Byte getEnableFilter(){
            return _enableFilter;
        }

        public void setEnableFilter(Byte value){
            this._enableFilter = value;
        }


        private String _refreshBehavior;

    
        @PropMeta(propId=18)
    
        public String getRefreshBehavior(){
            return _refreshBehavior;
        }

        public void setRefreshBehavior(String value){
            this._refreshBehavior = value;
        }


        private String _refreshBehavior_label;

    
        public String getRefreshBehavior_label(){
            return _refreshBehavior_label;
        }

        public void setRefreshBehavior_label(String value){
            this._refreshBehavior_label = value;
        }


        private Byte _colNameCamelCase;

    
        @PropMeta(propId=19)
    
        public Byte getColNameCamelCase(){
            return _colNameCamelCase;
        }

        public void setColNameCamelCase(Byte value){
            this._colNameCamelCase = value;
        }


        private String _args;

    
        @PropMeta(propId=20)
    
        public String getArgs(){
            return _args;
        }

        public void setArgs(String value){
            this._args = value;
        }


        private Integer _status;

    
        @PropMeta(propId=21)
    
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


        private Integer _version;

    
        @PropMeta(propId=22)
    
        public Integer getVersion(){
            return _version;
        }

        public void setVersion(Integer value){
            this._version = value;
        }


        private String _createdBy;

    
        @PropMeta(propId=23)
    
        public String getCreatedBy(){
            return _createdBy;
        }

        public void setCreatedBy(String value){
            this._createdBy = value;
        }


        private java.sql.Timestamp _createTime;

    
        @PropMeta(propId=24)
    
        public java.sql.Timestamp getCreateTime(){
            return _createTime;
        }

        public void setCreateTime(java.sql.Timestamp value){
            this._createTime = value;
        }


        private String _updatedBy;

    
        @PropMeta(propId=25)
    
        public String getUpdatedBy(){
            return _updatedBy;
        }

        public void setUpdatedBy(String value){
            this._updatedBy = value;
        }


        private java.sql.Timestamp _updateTime;

    
        @PropMeta(propId=26)
    
        public java.sql.Timestamp getUpdateTime(){
            return _updateTime;
        }

        public void setUpdateTime(java.sql.Timestamp value){
            this._updateTime = value;
        }


        private String _remark;

    
        @PropMeta(propId=27)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private Map<String,Object> _module;

        public Map<String,Object> getModule(){
            return _module;
        }

        public void setModule(Map<String,Object> value){
            this._module = value;
        }


    }
