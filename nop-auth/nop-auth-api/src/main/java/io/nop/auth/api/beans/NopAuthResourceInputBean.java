//__XGEN_FORCE_OVERRIDE__
    package io.nop.auth.api.beans;

    import io.nop.api.core.annotations.data.DataBean;
    import io.nop.api.core.annotations.meta.PropMeta;
    import io.nop.api.core.api.CrudInputBase;
    import java.util.List;
    
    @DataBean
    @SuppressWarnings({"PMD","java:S116","java:S115"})
    public class NopAuthResourceInputBean extends CrudInputBase {

    
        private String _resourceId;

    
        @PropMeta(propId=1)
    
        public String getResourceId(){
            return _resourceId;
        }

        public void setResourceId(String value){
            this._resourceId = value;
        }


        private String _siteId;

    
        @PropMeta(propId=2)
    
        public String getSiteId(){
            return _siteId;
        }

        public void setSiteId(String value){
            this._siteId = value;
        }


        private String _displayName;

    
        @PropMeta(propId=3)
    
        public String getDisplayName(){
            return _displayName;
        }

        public void setDisplayName(String value){
            this._displayName = value;
        }


        private Integer _orderNo;

    
        @PropMeta(propId=4)
    
        public Integer getOrderNo(){
            return _orderNo;
        }

        public void setOrderNo(Integer value){
            this._orderNo = value;
        }


        private String _resourceType;

    
        @PropMeta(propId=5)
    
        public String getResourceType(){
            return _resourceType;
        }

        public void setResourceType(String value){
            this._resourceType = value;
        }


        private String _parentId;

    
        @PropMeta(propId=6)
    
        public String getParentId(){
            return _parentId;
        }

        public void setParentId(String value){
            this._parentId = value;
        }


        private String _icon;

    
        @PropMeta(propId=7)
    
        public String getIcon(){
            return _icon;
        }

        public void setIcon(String value){
            this._icon = value;
        }


        private String _routePath;

    
        @PropMeta(propId=8)
    
        public String getRoutePath(){
            return _routePath;
        }

        public void setRoutePath(String value){
            this._routePath = value;
        }


        private String _url;

    
        @PropMeta(propId=9)
    
        public String getUrl(){
            return _url;
        }

        public void setUrl(String value){
            this._url = value;
        }


        private String _component;

    
        @PropMeta(propId=10)
    
        public String getComponent(){
            return _component;
        }

        public void setComponent(String value){
            this._component = value;
        }


        private String _target;

    
        @PropMeta(propId=11)
    
        public String getTarget(){
            return _target;
        }

        public void setTarget(String value){
            this._target = value;
        }


        private Byte _hidden;

    
        @PropMeta(propId=12)
    
        public Byte getHidden(){
            return _hidden;
        }

        public void setHidden(Byte value){
            this._hidden = value;
        }


        private Byte _keepAlive;

    
        @PropMeta(propId=13)
    
        public Byte getKeepAlive(){
            return _keepAlive;
        }

        public void setKeepAlive(Byte value){
            this._keepAlive = value;
        }


        private String _permissions;

    
        @PropMeta(propId=14)
    
        public String getPermissions(){
            return _permissions;
        }

        public void setPermissions(String value){
            this._permissions = value;
        }


        private Byte _noAuth;

    
        @PropMeta(propId=15)
    
        public Byte getNoAuth(){
            return _noAuth;
        }

        public void setNoAuth(Byte value){
            this._noAuth = value;
        }


        private String _depends;

    
        @PropMeta(propId=16)
    
        public String getDepends(){
            return _depends;
        }

        public void setDepends(String value){
            this._depends = value;
        }


        private Byte _isLeaf;

    
        @PropMeta(propId=17)
    
        public Byte getIsLeaf(){
            return _isLeaf;
        }

        public void setIsLeaf(Byte value){
            this._isLeaf = value;
        }


        private Integer _status;

    
        @PropMeta(propId=18)
    
        public Integer getStatus(){
            return _status;
        }

        public void setStatus(Integer value){
            this._status = value;
        }


        private Byte _authCascadeUp;

    
        @PropMeta(propId=19)
    
        public Byte getAuthCascadeUp(){
            return _authCascadeUp;
        }

        public void setAuthCascadeUp(Byte value){
            this._authCascadeUp = value;
        }


        private String _metaConfig;

    
        @PropMeta(propId=20)
    
        public String getMetaConfig(){
            return _metaConfig;
        }

        public void setMetaConfig(String value){
            this._metaConfig = value;
        }


        private String _propsConfig;

    
        @PropMeta(propId=21)
    
        public String getPropsConfig(){
            return _propsConfig;
        }

        public void setPropsConfig(String value){
            this._propsConfig = value;
        }


        private Byte _delFlag;

    
        @PropMeta(propId=22)
    
        public Byte getDelFlag(){
            return _delFlag;
        }

        public void setDelFlag(Byte value){
            this._delFlag = value;
        }


        private String _remark;

    
        @PropMeta(propId=28)
    
        public String getRemark(){
            return _remark;
        }

        public void setRemark(String value){
            this._remark = value;
        }


        private java.util.List<java.lang.String> _relatedRoleList_ids;

    
        public java.util.List<java.lang.String> getRelatedRoleList_ids(){
            return _relatedRoleList_ids;
        }

        public void setRelatedRoleList_ids(java.util.List<java.lang.String> value){
            this._relatedRoleList_ids = value;
        }


        private List<NopAuthRoleResourceInputBean> _roleMappings;

        public List<NopAuthRoleResourceInputBean> getRoleMappings(){
            return _roleMappings;
        }

        public void setRoleMappings(List<NopAuthRoleResourceInputBean> value){
            this._roleMappings = value;
        }


        private List<NopAuthRoleInputBean> _relatedRoleList;

        public List<NopAuthRoleInputBean> getRelatedRoleList(){
            return _relatedRoleList;
        }

        public void setRelatedRoleList(List<NopAuthRoleInputBean> value){
            this._relatedRoleList = value;
        }


    }
