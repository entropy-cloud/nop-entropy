package io.nop.core.model.lang._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.core.model.lang.CompilationUnitModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/lang/compilation-unit.xdef <p>
 * 
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _CompilationUnitModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: classes
     * 
     */
    private KeyedList<io.nop.core.model.lang.ClassMetaModel> _classes = KeyedList.emptyList();
    
    /**
     *  
     * xml name: imports
     * 
     */
    private KeyedList<io.nop.core.model.lang.ImportMetaModel> _imports = KeyedList.emptyList();
    
    /**
     *  
     * xml name: methods
     * 
     */
    private KeyedList<io.nop.core.model.lang.MethodMetaModel> _methods = KeyedList.emptyList();
    
    /**
     * 
     * xml name: classes
     *  
     */
    
    public java.util.List<io.nop.core.model.lang.ClassMetaModel> getClasses(){
      return _classes;
    }

    
    public void setClasses(java.util.List<io.nop.core.model.lang.ClassMetaModel> value){
        checkAllowChange();
        
        this._classes = KeyedList.fromList(value, io.nop.core.model.lang.ClassMetaModel::getName);
           
    }

    
    public io.nop.core.model.lang.ClassMetaModel getClassName(String name){
        return this._classes.getByKey(name);
    }

    public boolean hasClassName(String name){
        return this._classes.containsKey(name);
    }

    public void addClassName(io.nop.core.model.lang.ClassMetaModel item) {
        checkAllowChange();
        java.util.List<io.nop.core.model.lang.ClassMetaModel> list = this.getClasses();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.core.model.lang.ClassMetaModel::getName);
            setClasses(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_classes(){
        return this._classes.keySet();
    }

    public boolean hasClasses(){
        return !this._classes.isEmpty();
    }
    
    /**
     * 
     * xml name: imports
     *  
     */
    
    public java.util.List<io.nop.core.model.lang.ImportMetaModel> getImports(){
      return _imports;
    }

    
    public void setImports(java.util.List<io.nop.core.model.lang.ImportMetaModel> value){
        checkAllowChange();
        
        this._imports = KeyedList.fromList(value, io.nop.core.model.lang.ImportMetaModel::getAs);
           
    }

    
    public io.nop.core.model.lang.ImportMetaModel getImport(String name){
        return this._imports.getByKey(name);
    }

    public boolean hasImport(String name){
        return this._imports.containsKey(name);
    }

    public void addImport(io.nop.core.model.lang.ImportMetaModel item) {
        checkAllowChange();
        java.util.List<io.nop.core.model.lang.ImportMetaModel> list = this.getImports();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.core.model.lang.ImportMetaModel::getAs);
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
     * xml name: methods
     *  
     */
    
    public java.util.List<io.nop.core.model.lang.MethodMetaModel> getMethods(){
      return _methods;
    }

    
    public void setMethods(java.util.List<io.nop.core.model.lang.MethodMetaModel> value){
        checkAllowChange();
        
        this._methods = KeyedList.fromList(value, io.nop.core.model.lang.MethodMetaModel::getName);
           
    }

    
    public io.nop.core.model.lang.MethodMetaModel getMethod(String name){
        return this._methods.getByKey(name);
    }

    public boolean hasMethod(String name){
        return this._methods.containsKey(name);
    }

    public void addMethod(io.nop.core.model.lang.MethodMetaModel item) {
        checkAllowChange();
        java.util.List<io.nop.core.model.lang.MethodMetaModel> list = this.getMethods();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.core.model.lang.MethodMetaModel::getName);
            setMethods(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_methods(){
        return this._methods.keySet();
    }

    public boolean hasMethods(){
        return !this._methods.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._classes = io.nop.api.core.util.FreezeHelper.deepFreeze(this._classes);
            
           this._imports = io.nop.api.core.util.FreezeHelper.deepFreeze(this._imports);
            
           this._methods = io.nop.api.core.util.FreezeHelper.deepFreeze(this._methods);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("classes",this.getClasses());
        out.putNotNull("imports",this.getImports());
        out.putNotNull("methods",this.getMethods());
    }

    public CompilationUnitModel cloneInstance(){
        CompilationUnitModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(CompilationUnitModel instance){
        super.copyTo(instance);
        
        instance.setClasses(this.getClasses());
        instance.setImports(this.getImports());
        instance.setMethods(this.getMethods());
    }

    protected CompilationUnitModel newInstance(){
        return (CompilationUnitModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
