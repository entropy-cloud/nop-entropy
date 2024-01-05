package io.nop.xlang.xt.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.xlang.xt.model.XtTransformModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from [6:2:0:0]/nop/schema/xt.xdef <p>
 * 类似xslt的tree转换规则定义
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _XtTransformModel extends io.nop.core.resource.component.AbstractComponentModel {
    
    /**
     *  
     * xml name: import
     * 导入其他的xt转换规则定义
     */
    private KeyedList<io.nop.xlang.xt.model.XtImportModel> _imports = KeyedList.emptyList();
    
    /**
     *  
     * xml name: main
     * 缺省执行main规则进行转换。转换生成的根节点上可能标注了x:schema，用于标识输出节点的schema定义
     */
    private io.nop.xlang.xt.model.XtRuleGroupModel _main ;
    
    /**
     *  
     * xml name: mapping
     * 按标签名映射到不同的规则
     */
    private KeyedList<io.nop.xlang.xt.model.XtMappingModel> _mappings = KeyedList.emptyList();
    
    /**
     *  
     * xml name: template
     * 
     */
    private KeyedList<io.nop.xlang.xt.model.XtTemplateModel> _templates = KeyedList.emptyList();
    
    /**
     * 
     * xml name: import
     *  导入其他的xt转换规则定义
     */
    
    public java.util.List<io.nop.xlang.xt.model.XtImportModel> getImports(){
      return _imports;
    }

    
    public void setImports(java.util.List<io.nop.xlang.xt.model.XtImportModel> value){
        checkAllowChange();
        
        this._imports = KeyedList.fromList(value, io.nop.xlang.xt.model.XtImportModel::getFrom);
           
    }

    
    public io.nop.xlang.xt.model.XtImportModel getImport(String name){
        return this._imports.getByKey(name);
    }

    public boolean hasImport(String name){
        return this._imports.containsKey(name);
    }

    public void addImport(io.nop.xlang.xt.model.XtImportModel item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xt.model.XtImportModel> list = this.getImports();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xt.model.XtImportModel::getFrom);
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
     * xml name: main
     *  缺省执行main规则进行转换。转换生成的根节点上可能标注了x:schema，用于标识输出节点的schema定义
     */
    
    public io.nop.xlang.xt.model.XtRuleGroupModel getMain(){
      return _main;
    }

    
    public void setMain(io.nop.xlang.xt.model.XtRuleGroupModel value){
        checkAllowChange();
        
        this._main = value;
           
    }

    
    /**
     * 
     * xml name: mapping
     *  按标签名映射到不同的规则
     */
    
    public java.util.List<io.nop.xlang.xt.model.XtMappingModel> getMappings(){
      return _mappings;
    }

    
    public void setMappings(java.util.List<io.nop.xlang.xt.model.XtMappingModel> value){
        checkAllowChange();
        
        this._mappings = KeyedList.fromList(value, io.nop.xlang.xt.model.XtMappingModel::getId);
           
    }

    
    public io.nop.xlang.xt.model.XtMappingModel getMapping(String name){
        return this._mappings.getByKey(name);
    }

    public boolean hasMapping(String name){
        return this._mappings.containsKey(name);
    }

    public void addMapping(io.nop.xlang.xt.model.XtMappingModel item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xt.model.XtMappingModel> list = this.getMappings();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xt.model.XtMappingModel::getId);
            setMappings(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_mappings(){
        return this._mappings.keySet();
    }

    public boolean hasMappings(){
        return !this._mappings.isEmpty();
    }
    
    /**
     * 
     * xml name: template
     *  
     */
    
    public java.util.List<io.nop.xlang.xt.model.XtTemplateModel> getTemplates(){
      return _templates;
    }

    
    public void setTemplates(java.util.List<io.nop.xlang.xt.model.XtTemplateModel> value){
        checkAllowChange();
        
        this._templates = KeyedList.fromList(value, io.nop.xlang.xt.model.XtTemplateModel::getId);
           
    }

    
    public io.nop.xlang.xt.model.XtTemplateModel getTemplate(String name){
        return this._templates.getByKey(name);
    }

    public boolean hasTemplate(String name){
        return this._templates.containsKey(name);
    }

    public void addTemplate(io.nop.xlang.xt.model.XtTemplateModel item) {
        checkAllowChange();
        java.util.List<io.nop.xlang.xt.model.XtTemplateModel> list = this.getTemplates();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.xlang.xt.model.XtTemplateModel::getId);
            setTemplates(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_templates(){
        return this._templates.keySet();
    }

    public boolean hasTemplates(){
        return !this._templates.isEmpty();
    }
    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._imports = io.nop.api.core.util.FreezeHelper.deepFreeze(this._imports);
            
           this._main = io.nop.api.core.util.FreezeHelper.deepFreeze(this._main);
            
           this._mappings = io.nop.api.core.util.FreezeHelper.deepFreeze(this._mappings);
            
           this._templates = io.nop.api.core.util.FreezeHelper.deepFreeze(this._templates);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.put("imports",this.getImports());
        out.put("main",this.getMain());
        out.put("mappings",this.getMappings());
        out.put("templates",this.getTemplates());
    }

    public XtTransformModel cloneInstance(){
        XtTransformModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(XtTransformModel instance){
        super.copyTo(instance);
        
        instance.setImports(this.getImports());
        instance.setMain(this.getMain());
        instance.setMappings(this.getMappings());
        instance.setTemplates(this.getTemplates());
    }

    protected XtTransformModel newInstance(){
        return (XtTransformModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
