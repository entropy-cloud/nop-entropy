//__XGEN_FORCE_OVERRIDE__
    package io.nop.code.api.beans;

    import com.fasterxml.jackson.annotation.JsonInclude;
    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    
    @DataBean
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopCodeSymbolInputBean extends CrudInputBase {

    
        private String _id;

    
        @PropMeta(propId=1)
    
        public String getId(){
            return _id;
        }

        public void setId(String value){
            this._id = value;
        }


        private String _indexId;

    
        @PropMeta(propId=2)
    
        public String getIndexId(){
            return _indexId;
        }

        public void setIndexId(String value){
            this._indexId = value;
        }


        private String _fileId;

    
        @PropMeta(propId=3)
    
        public String getFileId(){
            return _fileId;
        }

        public void setFileId(String value){
            this._fileId = value;
        }


        private String _kind;

    
        @PropMeta(propId=4)
    
        public String getKind(){
            return _kind;
        }

        public void setKind(String value){
            this._kind = value;
        }


        private String _name;

    
        @PropMeta(propId=5)
    
        public String getName(){
            return _name;
        }

        public void setName(String value){
            this._name = value;
        }


        private String _qualifiedName;

    
        @PropMeta(propId=6)
    
        public String getQualifiedName(){
            return _qualifiedName;
        }

        public void setQualifiedName(String value){
            this._qualifiedName = value;
        }


        private String _accessModifier;

    
        @PropMeta(propId=7)
    
        public String getAccessModifier(){
            return _accessModifier;
        }

        public void setAccessModifier(String value){
            this._accessModifier = value;
        }


        private Boolean _deprecated;

    
        @PropMeta(propId=8)
    
        public Boolean getDeprecated(){
            return _deprecated;
        }

        public void setDeprecated(Boolean value){
            this._deprecated = value;
        }


        private String _documentation;

    
        @PropMeta(propId=9)
    
        public String getDocumentation(){
            return _documentation;
        }

        public void setDocumentation(String value){
            this._documentation = value;
        }


        private Integer _line;

    
        @PropMeta(propId=10)
    
        public Integer getLine(){
            return _line;
        }

        public void setLine(Integer value){
            this._line = value;
        }


        private Integer _column;

    
        @PropMeta(propId=11)
    
        public Integer getColumn(){
            return _column;
        }

        public void setColumn(Integer value){
            this._column = value;
        }


        private Integer _endLine;

    
        @PropMeta(propId=12)
    
        public Integer getEndLine(){
            return _endLine;
        }

        public void setEndLine(Integer value){
            this._endLine = value;
        }


        private Integer _endColumn;

    
        @PropMeta(propId=13)
    
        public Integer getEndColumn(){
            return _endColumn;
        }

        public void setEndColumn(Integer value){
            this._endColumn = value;
        }


        private Integer _usageCount;

    
        @PropMeta(propId=14)
    
        public Integer getUsageCount(){
            return _usageCount;
        }

        public void setUsageCount(Integer value){
            this._usageCount = value;
        }


        private String _parentId;

    
        @PropMeta(propId=15)
    
        public String getParentId(){
            return _parentId;
        }

        public void setParentId(String value){
            this._parentId = value;
        }


        private String _declaringSymbolId;

    
        @PropMeta(propId=16)
    
        public String getDeclaringSymbolId(){
            return _declaringSymbolId;
        }

        public void setDeclaringSymbolId(String value){
            this._declaringSymbolId = value;
        }


        private String _superClassName;

    
        @PropMeta(propId=17)
    
        public String getSuperClassName(){
            return _superClassName;
        }

        public void setSuperClassName(String value){
            this._superClassName = value;
        }


        private Boolean _isAbstract;

    
        @PropMeta(propId=18)
    
        public Boolean getIsAbstract(){
            return _isAbstract;
        }

        public void setIsAbstract(Boolean value){
            this._isAbstract = value;
        }


        private Boolean _isFinal;

    
        @PropMeta(propId=19)
    
        public Boolean getIsFinal(){
            return _isFinal;
        }

        public void setIsFinal(Boolean value){
            this._isFinal = value;
        }


        private String _signature;

    
        @PropMeta(propId=20)
    
        public String getSignature(){
            return _signature;
        }

        public void setSignature(String value){
            this._signature = value;
        }


        private String _returnType;

    
        @PropMeta(propId=21)
    
        public String getReturnType(){
            return _returnType;
        }

        public void setReturnType(String value){
            this._returnType = value;
        }


        private Boolean _isStatic;

    
        @PropMeta(propId=22)
    
        public Boolean getIsStatic(){
            return _isStatic;
        }

        public void setIsStatic(Boolean value){
            this._isStatic = value;
        }


        private Boolean _isSynchronized;

    
        @PropMeta(propId=23)
    
        public Boolean getIsSynchronized(){
            return _isSynchronized;
        }

        public void setIsSynchronized(Boolean value){
            this._isSynchronized = value;
        }


        private Boolean _isNative;

    
        @PropMeta(propId=24)
    
        public Boolean getIsNative(){
            return _isNative;
        }

        public void setIsNative(Boolean value){
            this._isNative = value;
        }


        private String _fieldType;

    
        @PropMeta(propId=25)
    
        public String getFieldType(){
            return _fieldType;
        }

        public void setFieldType(String value){
            this._fieldType = value;
        }


        private Boolean _isVolatile;

    
        @PropMeta(propId=26)
    
        public Boolean getIsVolatile(){
            return _isVolatile;
        }

        public void setIsVolatile(Boolean value){
            this._isVolatile = value;
        }


        private Boolean _isTransient;

    
        @PropMeta(propId=27)
    
        public Boolean getIsTransient(){
            return _isTransient;
        }

        public void setIsTransient(Boolean value){
            this._isTransient = value;
        }


        private String _extData;

    
        @PropMeta(propId=28)
    
        public String getExtData(){
            return _extData;
        }

        public void setExtData(String value){
            this._extData = value;
        }


        private Boolean _asyncFlag;

    
        @PropMeta(propId=29)
    
        public Boolean getAsyncFlag(){
            return _asyncFlag;
        }

        public void setAsyncFlag(Boolean value){
            this._asyncFlag = value;
        }


        private Boolean _readonlyFlag;

    
        @PropMeta(propId=30)
    
        public Boolean getReadonlyFlag(){
            return _readonlyFlag;
        }

        public void setReadonlyFlag(Boolean value){
            this._readonlyFlag = value;
        }


        private String _rawReturnType;

    
        @PropMeta(propId=31)
    
        public String getRawReturnType(){
            return _rawReturnType;
        }

        public void setRawReturnType(String value){
            this._rawReturnType = value;
        }


        private String _rawFieldType;

    
        @PropMeta(propId=32)
    
        public String getRawFieldType(){
            return _rawFieldType;
        }

        public void setRawFieldType(String value){
            this._rawFieldType = value;
        }


    }
