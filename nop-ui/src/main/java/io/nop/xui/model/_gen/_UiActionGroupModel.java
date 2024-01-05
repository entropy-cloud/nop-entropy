package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiActionGroupModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [47:14:0:0]/nop/schema/xui/action.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiActionGroupModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: action
     * 
     */
    private KeyedList<io.nop.xui.model.UiActionModel> _actions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: batch
     * 
     */
    private java.lang.Boolean _batch ;
    
    /**
     *  
     * xml name: block
     * 
     */
    private java.lang.Boolean _block ;
    
    /**
     *  
     * xml name: btnClassName
     * 
     */
    private java.lang.String _btnClassName ;
    
    /**
     *  
     * xml name: className
     * 
     */
    private java.lang.String _className ;
    
    /**
     *  
     * xml name: closeOnClick
     * 
     */
    private java.lang.Boolean _closeOnClick ;
    
    /**
     *  
     * xml name: closeOnOutside
     * 
     */
    private java.lang.Boolean _closeOnOutside ;
    
    /**
     *  
     * xml name: defaultIsOpened
     * 
     */
    private java.lang.Boolean _defaultIsOpened ;
    
    /**
     *  
     * xml name: disabledOn
     * 
     */
    private java.lang.String _disabledOn ;
    
    /**
     *  
     * xml name: hideCaret
     * 
     */
    private java.lang.Boolean _hideCaret ;
    
    /**
     *  
     * xml name: icon
     * 
     */
    private java.lang.String _icon ;
    
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
     * xml name: onEvent
     * 
     */
    private java.util.Map<java.lang.String,java.lang.Object> _onEvent ;
    
    /**
     *  
     * xml name: size
     * 
     */
    private java.lang.String _size ;
    
    /**
     *  
     * xml name: trigger
     * 
     */
    private java.lang.String _trigger ;
    
    /**
     *  
     * xml name: 
     * 
     */
    private java.lang.String _type ;
    
    /**
     *  
     * xml name: visibleOn
     * 
     */
    private java.lang.String _visibleOn ;
    
    /**
     * 
     * xml name: action
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiActionModel> getActions(){
      return _actions;
    }

    
    public void setActions(java.util.List<io.nop.xui.model.UiActionModel> value){
        checkAllowChange();
        
        this._actions = KeyedList.fromList(value, io.nop.xui.model.UiActionModel::getId);
           
    }

    
    public io.nop.xui.model.UiActionModel getAction(String name){
        return this._actions.getByKey(name);
    }

    public boolean hasAction(String name){
        return this._actions.containsKey(name);
    }

    public void addAction(io.nop.xui.model.UiActionModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiActionModel> list = this.getActions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiActionModel::getId);
            setActions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_actions(){
        return this._actions.keySet();
    }

    public boolean hasActions(){
        return !this._actions.isEmpty();
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
     * xml name: btnClassName
     *  
     */
    
    public java.lang.String getBtnClassName(){
      return _btnClassName;
    }

    
    public void setBtnClassName(java.lang.String value){
        checkAllowChange();
        
        this._btnClassName = value;
           
    }

    
    /**
     * 
     * xml name: className
     *  
     */
    
    public java.lang.String getClassName(){
      return _className;
    }

    
    public void setClassName(java.lang.String value){
        checkAllowChange();
        
        this._className = value;
           
    }

    
    /**
     * 
     * xml name: closeOnClick
     *  
     */
    
    public java.lang.Boolean getCloseOnClick(){
      return _closeOnClick;
    }

    
    public void setCloseOnClick(java.lang.Boolean value){
        checkAllowChange();
        
        this._closeOnClick = value;
           
    }

    
    /**
     * 
     * xml name: closeOnOutside
     *  
     */
    
    public java.lang.Boolean getCloseOnOutside(){
      return _closeOnOutside;
    }

    
    public void setCloseOnOutside(java.lang.Boolean value){
        checkAllowChange();
        
        this._closeOnOutside = value;
           
    }

    
    /**
     * 
     * xml name: defaultIsOpened
     *  
     */
    
    public java.lang.Boolean getDefaultIsOpened(){
      return _defaultIsOpened;
    }

    
    public void setDefaultIsOpened(java.lang.Boolean value){
        checkAllowChange();
        
        this._defaultIsOpened = value;
           
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
     * xml name: hideCaret
     *  
     */
    
    public java.lang.Boolean getHideCaret(){
      return _hideCaret;
    }

    
    public void setHideCaret(java.lang.Boolean value){
        checkAllowChange();
        
        this._hideCaret = value;
           
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
     * xml name: trigger
     *  
     */
    
    public java.lang.String getTrigger(){
      return _trigger;
    }

    
    public void setTrigger(java.lang.String value){
        checkAllowChange();
        
        this._trigger = value;
           
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
        
           this._actions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._actions);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("actions",this.getActions());
        out.put("batch",this.getBatch());
        out.put("block",this.getBlock());
        out.put("btnClassName",this.getBtnClassName());
        out.put("className",this.getClassName());
        out.put("closeOnClick",this.getCloseOnClick());
        out.put("closeOnOutside",this.getCloseOnOutside());
        out.put("defaultIsOpened",this.getDefaultIsOpened());
        out.put("disabledOn",this.getDisabledOn());
        out.put("hideCaret",this.getHideCaret());
        out.put("icon",this.getIcon());
        out.put("iconOnly",this.getIconOnly());
        out.put("id",this.getId());
        out.put("label",this.getLabel());
        out.put("level",this.getLevel());
        out.put("onEvent",this.getOnEvent());
        out.put("size",this.getSize());
        out.put("trigger",this.getTrigger());
        out.put("type",this.getType());
        out.put("visibleOn",this.getVisibleOn());
    }

    public UiActionGroupModel cloneInstance(){
        UiActionGroupModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiActionGroupModel instance){
        super.copyTo(instance);
        
        instance.setActions(this.getActions());
        instance.setBatch(this.getBatch());
        instance.setBlock(this.getBlock());
        instance.setBtnClassName(this.getBtnClassName());
        instance.setClassName(this.getClassName());
        instance.setCloseOnClick(this.getCloseOnClick());
        instance.setCloseOnOutside(this.getCloseOnOutside());
        instance.setDefaultIsOpened(this.getDefaultIsOpened());
        instance.setDisabledOn(this.getDisabledOn());
        instance.setHideCaret(this.getHideCaret());
        instance.setIcon(this.getIcon());
        instance.setIconOnly(this.getIconOnly());
        instance.setId(this.getId());
        instance.setLabel(this.getLabel());
        instance.setLevel(this.getLevel());
        instance.setOnEvent(this.getOnEvent());
        instance.setSize(this.getSize());
        instance.setTrigger(this.getTrigger());
        instance.setType(this.getType());
        instance.setVisibleOn(this.getVisibleOn());
    }

    protected UiActionGroupModel newInstance(){
        return (UiActionGroupModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
