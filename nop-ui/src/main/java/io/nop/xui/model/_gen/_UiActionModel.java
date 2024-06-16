package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiActionModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xui/action.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiActionModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: actionType
     * 
     */
    private java.lang.String _actionType ;
    
    /**
     *  
     * xml name: active
     * 
     */
    private java.lang.Boolean _active ;
    
    /**
     *  
     * xml name: activeClassName
     * 
     */
    private java.lang.String _activeClassName ;
    
    /**
     *  
     * xml name: activeLevel
     * 
     */
    private java.lang.String _activeLevel ;
    
    /**
     *  
     * xml name: api
     * 
     */
    private io.nop.xui.model.UiApiModel _api ;
    
    /**
     *  
     * xml name: batch
     * 
     */
    private java.lang.Boolean _batch ;
    
    /**
     *  
     * xml name: blank
     * 
     */
    private java.lang.Boolean _blank ;
    
    /**
     *  
     * xml name: block
     * 
     */
    private java.lang.Boolean _block ;
    
    /**
     *  
     * xml name: body
     * 可以通过body定制按钮的显示
     */
    private java.util.Map<java.lang.String,java.lang.Object> _body ;
    
    /**
     *  
     * xml name: close
     * 
     */
    private java.lang.Object _close ;
    
    /**
     *  
     * xml name: confirmText
     * 
     */
    private java.lang.String _confirmText ;
    
    /**
     *  
     * xml name: content
     * 
     */
    private java.lang.String _content ;
    
    /**
     *  
     * xml name: copyFormat
     * 
     */
    private java.lang.String _copyFormat ;
    
    /**
     *  
     * xml name: countDown
     * 
     */
    private java.lang.Integer _countDown ;
    
    /**
     *  
     * xml name: countDownTpl
     * 
     */
    private java.lang.String _countDownTpl ;
    
    /**
     *  
     * xml name: dialog
     * 
     */
    private io.nop.xui.model.UiDialogModel _dialog ;
    
    /**
     *  
     * xml name: disabledOn
     * 
     */
    private java.lang.String _disabledOn ;
    
    /**
     *  
     * xml name: disabledTip
     * 
     */
    private java.lang.String _disabledTip ;
    
    /**
     *  
     * xml name: drawer
     * 
     */
    private io.nop.xui.model.UiDialogModel _drawer ;
    
    /**
     *  
     * xml name: feedback
     * 
     */
    private io.nop.xui.model.UiDialogModel _feedback ;
    
    /**
     *  
     * xml name: hotKey
     * 
     */
    private java.lang.String _hotKey ;
    
    /**
     *  
     * xml name: icon
     * 
     */
    private java.lang.String _icon ;
    
    /**
     *  
     * xml name: iconClassName
     * 
     */
    private java.lang.String _iconClassName ;
    
    /**
     *  
     * xml name: iconOnly
     * 
     */
    private java.lang.Boolean _iconOnly ;
    
    /**
     *  
     * xml name: id
     * 
     */
    private java.lang.String _id ;
    
    /**
     *  
     * xml name: initApi
     * 
     */
    private io.nop.xui.model.UiApiModel _initApi ;
    
    /**
     *  
     * xml name: label
     * 
     */
    private java.lang.String _label ;
    
    /**
     *  
     * xml name: level
     * 
     */
    private java.lang.String _level ;
    
    /**
     *  
     * xml name: link
     * actionType=link时起作用。用来指定跳转地址，跟 url 不同的是，这是单页跳转方式，不会渲染浏览器，请指定 amis 平台内的页面。可用 ${xxx} 取值。
     */
    private java.lang.String _link ;
    
    /**
     *  
     * xml name: messages
     * 
     */
    private java.util.Map _messages ;
    
    /**
     *  
     * xml name: onClick
     * 点击事件的响应函数，内容为js代码。上下文中存在props变量
     */
    private java.lang.String _onClick ;
    
    /**
     *  
     * xml name: onEvent
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _onEvent ;
    
    /**
     *  
     * xml name: redirect
     * 
     */
    private java.lang.String _redirect ;
    
    /**
     *  
     * xml name: reload
     * 
     */
    private java.lang.String _reload ;
    
    /**
     *  
     * xml name: required
     * 
     */
    private java.util.List<java.lang.String> _required ;
    
    /**
     *  
     * xml name: rightIcon
     * 
     */
    private java.lang.String _rightIcon ;
    
    /**
     *  
     * xml name: rightIconClassName
     * 
     */
    private java.lang.String _rightIconClassName ;
    
    /**
     *  
     * xml name: size
     * 
     */
    private java.lang.String _size ;
    
    /**
     *  
     * xml name: target
     * 
     */
    private java.lang.String _target ;
    
    /**
     *  
     * xml name: tooltip
     * 
     */
    private java.lang.String _tooltip ;
    
    /**
     *  
     * xml name: tooltipPlacement
     * 
     */
    private java.lang.String _tooltipPlacement ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: url
     * actionType=url时，url和blank参数起作用，用于浏览器整体跳转
     */
    private java.lang.String _url ;
    
    /**
     *  
     * xml name: visibleOn
     * 
     */
    private java.lang.String _visibleOn ;
    
    /**
     * 
     * xml name: actionType
     *  
     */
    
    public java.lang.String getActionType(){
      return _actionType;
    }

    
    public void setActionType(java.lang.String value){
        checkAllowChange();
        
        this._actionType = value;
           
    }

    
    /**
     * 
     * xml name: active
     *  
     */
    
    public java.lang.Boolean getActive(){
      return _active;
    }

    
    public void setActive(java.lang.Boolean value){
        checkAllowChange();
        
        this._active = value;
           
    }

    
    /**
     * 
     * xml name: activeClassName
     *  
     */
    
    public java.lang.String getActiveClassName(){
      return _activeClassName;
    }

    
    public void setActiveClassName(java.lang.String value){
        checkAllowChange();
        
        this._activeClassName = value;
           
    }

    
    /**
     * 
     * xml name: activeLevel
     *  
     */
    
    public java.lang.String getActiveLevel(){
      return _activeLevel;
    }

    
    public void setActiveLevel(java.lang.String value){
        checkAllowChange();
        
        this._activeLevel = value;
           
    }

    
    /**
     * 
     * xml name: api
     *  
     */
    
    public io.nop.xui.model.UiApiModel getApi(){
      return _api;
    }

    
    public void setApi(io.nop.xui.model.UiApiModel value){
        checkAllowChange();
        
        this._api = value;
           
    }

    
    /**
     * 
     * xml name: batch
     *  
     */
    
    public java.lang.Boolean getBatch(){
      return _batch;
    }

    
    public void setBatch(java.lang.Boolean value){
        checkAllowChange();
        
        this._batch = value;
           
    }

    
    /**
     * 
     * xml name: blank
     *  
     */
    
    public java.lang.Boolean getBlank(){
      return _blank;
    }

    
    public void setBlank(java.lang.Boolean value){
        checkAllowChange();
        
        this._blank = value;
           
    }

    
    /**
     * 
     * xml name: block
     *  
     */
    
    public java.lang.Boolean getBlock(){
      return _block;
    }

    
    public void setBlock(java.lang.Boolean value){
        checkAllowChange();
        
        this._block = value;
           
    }

    
    /**
     * 
     * xml name: body
     *  可以通过body定制按钮的显示
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getBody(){
      return _body;
    }

    
    public void setBody(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._body = value;
           
    }

    
    public boolean hasBody(){
        return this._body != null && !this._body.isEmpty();
    }
    
    /**
     * 
     * xml name: close
     *  
     */
    
    public java.lang.Object getClose(){
      return _close;
    }

    
    public void setClose(java.lang.Object value){
        checkAllowChange();
        
        this._close = value;
           
    }

    
    /**
     * 
     * xml name: confirmText
     *  
     */
    
    public java.lang.String getConfirmText(){
      return _confirmText;
    }

    
    public void setConfirmText(java.lang.String value){
        checkAllowChange();
        
        this._confirmText = value;
           
    }

    
    /**
     * 
     * xml name: content
     *  
     */
    
    public java.lang.String getContent(){
      return _content;
    }

    
    public void setContent(java.lang.String value){
        checkAllowChange();
        
        this._content = value;
           
    }

    
    /**
     * 
     * xml name: copyFormat
     *  
     */
    
    public java.lang.String getCopyFormat(){
      return _copyFormat;
    }

    
    public void setCopyFormat(java.lang.String value){
        checkAllowChange();
        
        this._copyFormat = value;
           
    }

    
    /**
     * 
     * xml name: countDown
     *  
     */
    
    public java.lang.Integer getCountDown(){
      return _countDown;
    }

    
    public void setCountDown(java.lang.Integer value){
        checkAllowChange();
        
        this._countDown = value;
           
    }

    
    /**
     * 
     * xml name: countDownTpl
     *  
     */
    
    public java.lang.String getCountDownTpl(){
      return _countDownTpl;
    }

    
    public void setCountDownTpl(java.lang.String value){
        checkAllowChange();
        
        this._countDownTpl = value;
           
    }

    
    /**
     * 
     * xml name: dialog
     *  
     */
    
    public io.nop.xui.model.UiDialogModel getDialog(){
      return _dialog;
    }

    
    public void setDialog(io.nop.xui.model.UiDialogModel value){
        checkAllowChange();
        
        this._dialog = value;
           
    }

    
    /**
     * 
     * xml name: disabledOn
     *  
     */
    
    public java.lang.String getDisabledOn(){
      return _disabledOn;
    }

    
    public void setDisabledOn(java.lang.String value){
        checkAllowChange();
        
        this._disabledOn = value;
           
    }

    
    /**
     * 
     * xml name: disabledTip
     *  
     */
    
    public java.lang.String getDisabledTip(){
      return _disabledTip;
    }

    
    public void setDisabledTip(java.lang.String value){
        checkAllowChange();
        
        this._disabledTip = value;
           
    }

    
    /**
     * 
     * xml name: drawer
     *  
     */
    
    public io.nop.xui.model.UiDialogModel getDrawer(){
      return _drawer;
    }

    
    public void setDrawer(io.nop.xui.model.UiDialogModel value){
        checkAllowChange();
        
        this._drawer = value;
           
    }

    
    /**
     * 
     * xml name: feedback
     *  
     */
    
    public io.nop.xui.model.UiDialogModel getFeedback(){
      return _feedback;
    }

    
    public void setFeedback(io.nop.xui.model.UiDialogModel value){
        checkAllowChange();
        
        this._feedback = value;
           
    }

    
    /**
     * 
     * xml name: hotKey
     *  
     */
    
    public java.lang.String getHotKey(){
      return _hotKey;
    }

    
    public void setHotKey(java.lang.String value){
        checkAllowChange();
        
        this._hotKey = value;
           
    }

    
    /**
     * 
     * xml name: icon
     *  
     */
    
    public java.lang.String getIcon(){
      return _icon;
    }

    
    public void setIcon(java.lang.String value){
        checkAllowChange();
        
        this._icon = value;
           
    }

    
    /**
     * 
     * xml name: iconClassName
     *  
     */
    
    public java.lang.String getIconClassName(){
      return _iconClassName;
    }

    
    public void setIconClassName(java.lang.String value){
        checkAllowChange();
        
        this._iconClassName = value;
           
    }

    
    /**
     * 
     * xml name: iconOnly
     *  
     */
    
    public java.lang.Boolean getIconOnly(){
      return _iconOnly;
    }

    
    public void setIconOnly(java.lang.Boolean value){
        checkAllowChange();
        
        this._iconOnly = value;
           
    }

    
    /**
     * 
     * xml name: id
     *  
     */
    
    public java.lang.String getId(){
      return _id;
    }

    
    public void setId(java.lang.String value){
        checkAllowChange();
        
        this._id = value;
           
    }

    
    /**
     * 
     * xml name: initApi
     *  
     */
    
    public io.nop.xui.model.UiApiModel getInitApi(){
      return _initApi;
    }

    
    public void setInitApi(io.nop.xui.model.UiApiModel value){
        checkAllowChange();
        
        this._initApi = value;
           
    }

    
    /**
     * 
     * xml name: label
     *  
     */
    
    public java.lang.String getLabel(){
      return _label;
    }

    
    public void setLabel(java.lang.String value){
        checkAllowChange();
        
        this._label = value;
           
    }

    
    /**
     * 
     * xml name: level
     *  
     */
    
    public java.lang.String getLevel(){
      return _level;
    }

    
    public void setLevel(java.lang.String value){
        checkAllowChange();
        
        this._level = value;
           
    }

    
    /**
     * 
     * xml name: link
     *  actionType=link时起作用。用来指定跳转地址，跟 url 不同的是，这是单页跳转方式，不会渲染浏览器，请指定 amis 平台内的页面。可用 ${xxx} 取值。
     */
    
    public java.lang.String getLink(){
      return _link;
    }

    
    public void setLink(java.lang.String value){
        checkAllowChange();
        
        this._link = value;
           
    }

    
    /**
     * 
     * xml name: messages
     *  
     */
    
    public java.util.Map getMessages(){
      return _messages;
    }

    
    public void setMessages(java.util.Map value){
        checkAllowChange();
        
        this._messages = value;
           
    }

    
    public boolean hasMessages(){
        return this._messages != null && !this._messages.isEmpty();
    }
    
    /**
     * 
     * xml name: onClick
     *  点击事件的响应函数，内容为js代码。上下文中存在props变量
     */
    
    public java.lang.String getOnClick(){
      return _onClick;
    }

    
    public void setOnClick(java.lang.String value){
        checkAllowChange();
        
        this._onClick = value;
           
    }

    
    /**
     * 
     * xml name: onEvent
     *  
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getOnEvent(){
      return _onEvent;
    }

    
    public void setOnEvent(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._onEvent = value;
           
    }

    
    public boolean hasOnEvent(){
        return this._onEvent != null && !this._onEvent.isEmpty();
    }
    
    /**
     * 
     * xml name: redirect
     *  
     */
    
    public java.lang.String getRedirect(){
      return _redirect;
    }

    
    public void setRedirect(java.lang.String value){
        checkAllowChange();
        
        this._redirect = value;
           
    }

    
    /**
     * 
     * xml name: reload
     *  
     */
    
    public java.lang.String getReload(){
      return _reload;
    }

    
    public void setReload(java.lang.String value){
        checkAllowChange();
        
        this._reload = value;
           
    }

    
    /**
     * 
     * xml name: required
     *  
     */
    
    public java.util.List<java.lang.String> getRequired(){
      return _required;
    }

    
    public void setRequired(java.util.List<java.lang.String> value){
        checkAllowChange();
        
        this._required = value;
           
    }

    
    /**
     * 
     * xml name: rightIcon
     *  
     */
    
    public java.lang.String getRightIcon(){
      return _rightIcon;
    }

    
    public void setRightIcon(java.lang.String value){
        checkAllowChange();
        
        this._rightIcon = value;
           
    }

    
    /**
     * 
     * xml name: rightIconClassName
     *  
     */
    
    public java.lang.String getRightIconClassName(){
      return _rightIconClassName;
    }

    
    public void setRightIconClassName(java.lang.String value){
        checkAllowChange();
        
        this._rightIconClassName = value;
           
    }

    
    /**
     * 
     * xml name: size
     *  
     */
    
    public java.lang.String getSize(){
      return _size;
    }

    
    public void setSize(java.lang.String value){
        checkAllowChange();
        
        this._size = value;
           
    }

    
    /**
     * 
     * xml name: target
     *  
     */
    
    public java.lang.String getTarget(){
      return _target;
    }

    
    public void setTarget(java.lang.String value){
        checkAllowChange();
        
        this._target = value;
           
    }

    
    /**
     * 
     * xml name: tooltip
     *  
     */
    
    public java.lang.String getTooltip(){
      return _tooltip;
    }

    
    public void setTooltip(java.lang.String value){
        checkAllowChange();
        
        this._tooltip = value;
           
    }

    
    /**
     * 
     * xml name: tooltipPlacement
     *  
     */
    
    public java.lang.String getTooltipPlacement(){
      return _tooltipPlacement;
    }

    
    public void setTooltipPlacement(java.lang.String value){
        checkAllowChange();
        
        this._tooltipPlacement = value;
           
    }

    
    /**
     * 
     * xml name: 
     *  
     */
    
    public java.lang.String getType(){
      return _type;
    }

    
    public void setType(java.lang.String value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: url
     *  actionType=url时，url和blank参数起作用，用于浏览器整体跳转
     */
    
    public java.lang.String getUrl(){
      return _url;
    }

    
    public void setUrl(java.lang.String value){
        checkAllowChange();
        
        this._url = value;
           
    }

    
    /**
     * 
     * xml name: visibleOn
     *  
     */
    
    public java.lang.String getVisibleOn(){
      return _visibleOn;
    }

    
    public void setVisibleOn(java.lang.String value){
        checkAllowChange();
        
        this._visibleOn = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._api = io.nop.api.core.util.FreezeHelper.deepFreeze(this._api);
            
           this._dialog = io.nop.api.core.util.FreezeHelper.deepFreeze(this._dialog);
            
           this._drawer = io.nop.api.core.util.FreezeHelper.deepFreeze(this._drawer);
            
           this._feedback = io.nop.api.core.util.FreezeHelper.deepFreeze(this._feedback);
            
           this._initApi = io.nop.api.core.util.FreezeHelper.deepFreeze(this._initApi);
            
           this._messages = io.nop.api.core.util.FreezeHelper.deepFreeze(this._messages);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("actionType",this.getActionType());
        out.putNotNull("active",this.getActive());
        out.putNotNull("activeClassName",this.getActiveClassName());
        out.putNotNull("activeLevel",this.getActiveLevel());
        out.putNotNull("api",this.getApi());
        out.putNotNull("batch",this.getBatch());
        out.putNotNull("blank",this.getBlank());
        out.putNotNull("block",this.getBlock());
        out.putNotNull("body",this.getBody());
        out.putNotNull("close",this.getClose());
        out.putNotNull("confirmText",this.getConfirmText());
        out.putNotNull("content",this.getContent());
        out.putNotNull("copyFormat",this.getCopyFormat());
        out.putNotNull("countDown",this.getCountDown());
        out.putNotNull("countDownTpl",this.getCountDownTpl());
        out.putNotNull("dialog",this.getDialog());
        out.putNotNull("disabledOn",this.getDisabledOn());
        out.putNotNull("disabledTip",this.getDisabledTip());
        out.putNotNull("drawer",this.getDrawer());
        out.putNotNull("feedback",this.getFeedback());
        out.putNotNull("hotKey",this.getHotKey());
        out.putNotNull("icon",this.getIcon());
        out.putNotNull("iconClassName",this.getIconClassName());
        out.putNotNull("iconOnly",this.getIconOnly());
        out.putNotNull("id",this.getId());
        out.putNotNull("initApi",this.getInitApi());
        out.putNotNull("label",this.getLabel());
        out.putNotNull("level",this.getLevel());
        out.putNotNull("link",this.getLink());
        out.putNotNull("messages",this.getMessages());
        out.putNotNull("onClick",this.getOnClick());
        out.putNotNull("onEvent",this.getOnEvent());
        out.putNotNull("redirect",this.getRedirect());
        out.putNotNull("reload",this.getReload());
        out.putNotNull("required",this.getRequired());
        out.putNotNull("rightIcon",this.getRightIcon());
        out.putNotNull("rightIconClassName",this.getRightIconClassName());
        out.putNotNull("size",this.getSize());
        out.putNotNull("target",this.getTarget());
        out.putNotNull("tooltip",this.getTooltip());
        out.putNotNull("tooltipPlacement",this.getTooltipPlacement());
        out.putNotNull("type",this.getType());
        out.putNotNull("url",this.getUrl());
        out.putNotNull("visibleOn",this.getVisibleOn());
    }

    public UiActionModel cloneInstance(){
        UiActionModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiActionModel instance){
        super.copyTo(instance);
        
        instance.setActionType(this.getActionType());
        instance.setActive(this.getActive());
        instance.setActiveClassName(this.getActiveClassName());
        instance.setActiveLevel(this.getActiveLevel());
        instance.setApi(this.getApi());
        instance.setBatch(this.getBatch());
        instance.setBlank(this.getBlank());
        instance.setBlock(this.getBlock());
        instance.setBody(this.getBody());
        instance.setClose(this.getClose());
        instance.setConfirmText(this.getConfirmText());
        instance.setContent(this.getContent());
        instance.setCopyFormat(this.getCopyFormat());
        instance.setCountDown(this.getCountDown());
        instance.setCountDownTpl(this.getCountDownTpl());
        instance.setDialog(this.getDialog());
        instance.setDisabledOn(this.getDisabledOn());
        instance.setDisabledTip(this.getDisabledTip());
        instance.setDrawer(this.getDrawer());
        instance.setFeedback(this.getFeedback());
        instance.setHotKey(this.getHotKey());
        instance.setIcon(this.getIcon());
        instance.setIconClassName(this.getIconClassName());
        instance.setIconOnly(this.getIconOnly());
        instance.setId(this.getId());
        instance.setInitApi(this.getInitApi());
        instance.setLabel(this.getLabel());
        instance.setLevel(this.getLevel());
        instance.setLink(this.getLink());
        instance.setMessages(this.getMessages());
        instance.setOnClick(this.getOnClick());
        instance.setOnEvent(this.getOnEvent());
        instance.setRedirect(this.getRedirect());
        instance.setReload(this.getReload());
        instance.setRequired(this.getRequired());
        instance.setRightIcon(this.getRightIcon());
        instance.setRightIconClassName(this.getRightIconClassName());
        instance.setSize(this.getSize());
        instance.setTarget(this.getTarget());
        instance.setTooltip(this.getTooltip());
        instance.setTooltipPlacement(this.getTooltipPlacement());
        instance.setType(this.getType());
        instance.setUrl(this.getUrl());
        instance.setVisibleOn(this.getVisibleOn());
    }

    protected UiActionModel newInstance(){
        return (UiActionModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
