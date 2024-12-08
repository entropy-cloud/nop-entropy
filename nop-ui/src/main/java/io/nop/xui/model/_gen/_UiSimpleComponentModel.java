package io.nop.xui.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xui.model.UiSimpleComponentModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/xui/simple-component.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _UiSimpleComponentModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: components
     * 
     */
    private KeyedList<io.nop.xui.model.UiSimpleComponentModel> _components = KeyedList.emptyList();
    
    /**
     *  
     * xml name: context
     * 
     */
    private KeyedList<io.nop.xui.model.UiSimpleContextModel> _contexts = KeyedList.emptyList();
    
    /**
     *  
     * xml name: imports
     * 
     */
    private KeyedList<io.nop.xui.model.UiImportModel> _imports = KeyedList.emptyList();
    
    /**
     *  
     * xml name: name
     * 
     */
    private java.lang.String _name ;
    
    /**
     *  
     * xml name: prop
     * 
     */
    private KeyedList<io.nop.xui.model.UiSimplePropModel> _props = KeyedList.emptyList();
    
    /**
     *  
     * xml name: slot
     * 
     */
    private KeyedList<io.nop.xui.model.UiSimpleSlotModel> _slots = KeyedList.emptyList();
    
    /**
     *  
     * xml name: state
     * 
     */
    private KeyedList<io.nop.xui.model.UiSimpleStateModel> _states = KeyedList.emptyList();
    
    /**
     *  
     * xml name: template
     * 
     */
    private io.nop.xui.vue.VueNode _template ;
    
    /**
     * 
     * xml name: components
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiSimpleComponentModel> getComponents(){
      return _components;
    }

    
    public void setComponents(java.util.List<io.nop.xui.model.UiSimpleComponentModel> value){
        checkAllowChange();
        
        this._components = KeyedList.fromList(value, io.nop.xui.model.UiSimpleComponentModel::getName);
           
    }

    
    public io.nop.xui.model.UiSimpleComponentModel getComponent(String name){
        return this._components.getByKey(name);
    }

    public boolean hasComponent(String name){
        return this._components.containsKey(name);
    }

    public void addComponent(io.nop.xui.model.UiSimpleComponentModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiSimpleComponentModel> list = this.getComponents();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiSimpleComponentModel::getName);
            setComponents(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_components(){
        return this._components.keySet();
    }

    public boolean hasComponents(){
        return !this._components.isEmpty();
    }
    
    /**
     * 
     * xml name: context
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiSimpleContextModel> getContexts(){
      return _contexts;
    }

    
    public void setContexts(java.util.List<io.nop.xui.model.UiSimpleContextModel> value){
        checkAllowChange();
        
        this._contexts = KeyedList.fromList(value, io.nop.xui.model.UiSimpleContextModel::getName);
           
    }

    
    public io.nop.xui.model.UiSimpleContextModel getContext(String name){
        return this._contexts.getByKey(name);
    }

    public boolean hasContext(String name){
        return this._contexts.containsKey(name);
    }

    public void addContext(io.nop.xui.model.UiSimpleContextModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiSimpleContextModel> list = this.getContexts();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiSimpleContextModel::getName);
            setContexts(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_contexts(){
        return this._contexts.keySet();
    }

    public boolean hasContexts(){
        return !this._contexts.isEmpty();
    }
    
    /**
     * 
     * xml name: imports
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiImportModel> getImports(){
      return _imports;
    }

    
    public void setImports(java.util.List<io.nop.xui.model.UiImportModel> value){
        checkAllowChange();
        
        this._imports = KeyedList.fromList(value, io.nop.xui.model.UiImportModel::getAs);
           
    }

    
    public io.nop.xui.model.UiImportModel getImport(String name){
        return this._imports.getByKey(name);
    }

    public boolean hasImport(String name){
        return this._imports.containsKey(name);
    }

    public void addImport(io.nop.xui.model.UiImportModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiImportModel> list = this.getImports();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiImportModel::getAs);
            setImports(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_imports(){
        return this._imports.keySet();
    }

    public boolean hasImports(){
        return !this._imports.isEmpty();
    }
    
    /**
     * 
     * xml name: name
     *  
     */
    
    public java.lang.String getName(){
      return _name;
    }

    
    public void setName(java.lang.String value){
        checkAllowChange();
        
        this._name = value;
           
    }

    
    /**
     * 
     * xml name: prop
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiSimplePropModel> getProps(){
      return _props;
    }

    
    public void setProps(java.util.List<io.nop.xui.model.UiSimplePropModel> value){
        checkAllowChange();
        
        this._props = KeyedList.fromList(value, io.nop.xui.model.UiSimplePropModel::getName);
           
    }

    
    public io.nop.xui.model.UiSimplePropModel getProp(String name){
        return this._props.getByKey(name);
    }

    public boolean hasProp(String name){
        return this._props.containsKey(name);
    }

    public void addProp(io.nop.xui.model.UiSimplePropModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiSimplePropModel> list = this.getProps();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiSimplePropModel::getName);
            setProps(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_props(){
        return this._props.keySet();
    }

    public boolean hasProps(){
        return !this._props.isEmpty();
    }
    
    /**
     * 
     * xml name: slot
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiSimpleSlotModel> getSlots(){
      return _slots;
    }

    
    public void setSlots(java.util.List<io.nop.xui.model.UiSimpleSlotModel> value){
        checkAllowChange();
        
        this._slots = KeyedList.fromList(value, io.nop.xui.model.UiSimpleSlotModel::getName);
           
    }

    
    public io.nop.xui.model.UiSimpleSlotModel getSlot(String name){
        return this._slots.getByKey(name);
    }

    public boolean hasSlot(String name){
        return this._slots.containsKey(name);
    }

    public void addSlot(io.nop.xui.model.UiSimpleSlotModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiSimpleSlotModel> list = this.getSlots();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiSimpleSlotModel::getName);
            setSlots(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_slots(){
        return this._slots.keySet();
    }

    public boolean hasSlots(){
        return !this._slots.isEmpty();
    }
    
    /**
     * 
     * xml name: state
     *  
     */
    
    public java.util.List<io.nop.xui.model.UiSimpleStateModel> getStates(){
      return _states;
    }

    
    public void setStates(java.util.List<io.nop.xui.model.UiSimpleStateModel> value){
        checkAllowChange();
        
        this._states = KeyedList.fromList(value, io.nop.xui.model.UiSimpleStateModel::getName);
           
    }

    
    public io.nop.xui.model.UiSimpleStateModel getState(String name){
        return this._states.getByKey(name);
    }

    public boolean hasState(String name){
        return this._states.containsKey(name);
    }

    public void addState(io.nop.xui.model.UiSimpleStateModel item) {
        checkAllowChange();
        java.util.List<io.nop.xui.model.UiSimpleStateModel> list = this.getStates();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xui.model.UiSimpleStateModel::getName);
            setStates(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_states(){
        return this._states.keySet();
    }

    public boolean hasStates(){
        return !this._states.isEmpty();
    }
    
    /**
     * 
     * xml name: template
     *  
     */
    
    public io.nop.xui.vue.VueNode getTemplate(){
      return _template;
    }

    
    public void setTemplate(io.nop.xui.vue.VueNode value){
        checkAllowChange();
        
        this._template = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._components = io.nop.api.core.util.FreezeHelper.deepFreeze(this._components);
            
           this._contexts = io.nop.api.core.util.FreezeHelper.deepFreeze(this._contexts);
            
           this._imports = io.nop.api.core.util.FreezeHelper.deepFreeze(this._imports);
            
           this._props = io.nop.api.core.util.FreezeHelper.deepFreeze(this._props);
            
           this._slots = io.nop.api.core.util.FreezeHelper.deepFreeze(this._slots);
            
           this._states = io.nop.api.core.util.FreezeHelper.deepFreeze(this._states);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("components",this.getComponents());
        out.putNotNull("contexts",this.getContexts());
        out.putNotNull("imports",this.getImports());
        out.putNotNull("name",this.getName());
        out.putNotNull("props",this.getProps());
        out.putNotNull("slots",this.getSlots());
        out.putNotNull("states",this.getStates());
        out.putNotNull("template",this.getTemplate());
    }

    public UiSimpleComponentModel cloneInstance(){
        UiSimpleComponentModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(UiSimpleComponentModel instance){
        super.copyTo(instance);
        
        instance.setComponents(this.getComponents());
        instance.setContexts(this.getContexts());
        instance.setImports(this.getImports());
        instance.setName(this.getName());
        instance.setProps(this.getProps());
        instance.setSlots(this.getSlots());
        instance.setStates(this.getStates());
        instance.setTemplate(this.getTemplate());
    }

    protected UiSimpleComponentModel newInstance(){
        return (UiSimpleComponentModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
