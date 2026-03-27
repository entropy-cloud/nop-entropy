package io.nop.db.migration.model._gen;

import io.nop.commons.collections.KeyedList; //NOPMD NOSONAR - suppressed UnusedImports - Used for List Prop
import io.nop.core.lang.json.IJsonHandler;
import io.nop.db.migration.model.DbMigrationModel;
import io.nop.commons.util.ClassHelper;



// tell cpd to start ignoring code - CPD-OFF
/**
 * generate from /nop/schema/db-migration/migration.xdef <p>
 * 数据库迁移元模型定义（Database Migration Meta-Model Definition）
 * 设计理念：
 * 1. 类似 Flyway 的易用性：每个变更一个文件，文件名包含版本号
 * 2. 类似 Liquibase 的数据库无关性：使用抽象的变更类型，支持多数据库方言
 * 3. 契合 Nop 平台：使用 xdef 元模型，支持 Delta 定制
 * 文件命名规范：
 * - 版本化迁移：V{version}__{description}.migration.xml
 * 示例：V1.0.0__create_user_table.migration.xml
 * - 可重复迁移：R__{description}.migration.xml
 * 示例：R__create_statistics_view.migration.xml
 * 版本号格式：主版本.次版本.补丁版本（如 1.0.0, 1.0.1, 1.1.0）
 * 执行顺序：按版本号排序，从小到大依次执行
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
    "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S101","java:S1128","java:S1161"})
public abstract class _DbMigrationModel extends io.nop.xlang.xdsl.AbstractDslModel {
    
    /**
     *  
     * xml name: author
     * 作者
     */
    private java.lang.String _author ;
    
    /**
     *  
     * xml name: changeset
     * 变更集（Changeset）
     * 包含一组要执行的数据库变更操作
     */
    private KeyedList<io.nop.db.migration.model.DbChangeModel> _changeset = KeyedList.emptyList();
    
    /**
     *  
     * xml name: comment
     * 
     */
    private java.lang.String _comment ;
    
    /**
     *  
     * xml name: contexts
     * 上下文（Contexts）
     * 用于在不同环境（开发、测试、生产）中控制迁移的执行
     */
    private java.lang.String _contexts ;
    
    /**
     *  
     * xml name: description
     * 迁移描述
     */
    private java.lang.String _description ;
    
    /**
     *  
     * xml name: failOnError
     * 失败时是否抛出异常停止迁移
     */
    private boolean _failOnError  = true;
    
    /**
     *  
     * xml name: ignore
     * 是否忽略此迁移（用于临时禁用某个迁移）
     */
    private boolean _ignore  = false;
    
    /**
     *  
     * xml name: labels
     * 标签（Labels）
     * 用于分组和选择性执行迁移
     */
    private java.lang.String _labels ;
    
    /**
     *  
     * xml name: preconditions
     * 前置条件检查（Preconditions）
     * 只有满足所有前置条件时才会执行此迁移
     */
    private KeyedList<io.nop.db.migration.model.DbPreconditionModel> _preconditions = KeyedList.emptyList();
    
    /**
     *  
     * xml name: properties
     * 扩展属性（用于存储额外的元数据）
     */
    private java.util.Map<java.lang.String,java.lang.Object> _properties = java.util.Collections.emptyMap();
    
    /**
     *  
     * xml name: rollback
     * 回滚定义（Rollback）
     * 定义如何回滚此迁移
     * 策略：
     * 1. 如果没有定义 rollback，Nop 会尝试自动生成回滚 SQL（仅限简单变更）
     * 2. 如果定义了 rollback，则使用定义的回滚逻辑
     * 3. 如果设置了 rollbackImpossible="true"，则标记此迁移不可回滚
     */
    private io.nop.db.migration.model.RollbackDefinition _rollback ;
    
    /**
     *  
     * xml name: runOn
     * 运行时机：always(总是) | onChange(变更时) | never(从不)
     */
    private io.nop.db.migration.RunOnChange _runOn ;
    
    /**
     *  
     * xml name: type
     * 迁移类型：versioned(版本化，只执行一次) | repeatable(可重复，每次校验和变化都重新执行)
     */
    private io.nop.db.migration.MigrationType _type ;
    
    /**
     *  
     * xml name: version
     * 迁移版本号（必须符合语义化版本规范）
     */
    private java.lang.String _version ;
    
    /**
     * 
     * xml name: author
     *  作者
     */
    
    public java.lang.String getAuthor(){
      return _author;
    }

    
    public void setAuthor(java.lang.String value){
        checkAllowChange();
        
        this._author = value;
           
    }

    
    /**
     * 
     * xml name: changeset
     *  变更集（Changeset）
     * 包含一组要执行的数据库变更操作
     */
    
    public java.util.List<io.nop.db.migration.model.DbChangeModel> getChangeset(){
      return _changeset;
    }

    
    public void setChangeset(java.util.List<io.nop.db.migration.model.DbChangeModel> value){
        checkAllowChange();
        
        this._changeset = KeyedList.fromList(value, io.nop.db.migration.model.DbChangeModel::getId);
           
    }

    
    public io.nop.db.migration.model.DbChangeModel getChange(String name){
        return this._changeset.getByKey(name);
    }

    public boolean hasChange(String name){
        return this._changeset.containsKey(name);
    }

    public void addChange(io.nop.db.migration.model.DbChangeModel item) {
        checkAllowChange();
        java.util.List<io.nop.db.migration.model.DbChangeModel> list = this.getChangeset();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.db.migration.model.DbChangeModel::getId);
            setChangeset(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_changeset(){
        return this._changeset.keySet();
    }

    public boolean hasChangeset(){
        return !this._changeset.isEmpty();
    }
    
    /**
     * 
     * xml name: comment
     *  
     */
    
    public java.lang.String getComment(){
      return _comment;
    }

    
    public void setComment(java.lang.String value){
        checkAllowChange();
        
        this._comment = value;
           
    }

    
    /**
     * 
     * xml name: contexts
     *  上下文（Contexts）
     * 用于在不同环境（开发、测试、生产）中控制迁移的执行
     */
    
    public java.lang.String getContexts(){
      return _contexts;
    }

    
    public void setContexts(java.lang.String value){
        checkAllowChange();
        
        this._contexts = value;
           
    }

    
    /**
     * 
     * xml name: description
     *  迁移描述
     */
    
    public java.lang.String getDescription(){
      return _description;
    }

    
    public void setDescription(java.lang.String value){
        checkAllowChange();
        
        this._description = value;
           
    }

    
    /**
     * 
     * xml name: failOnError
     *  失败时是否抛出异常停止迁移
     */
    
    public boolean isFailOnError(){
      return _failOnError;
    }

    
    public void setFailOnError(boolean value){
        checkAllowChange();
        
        this._failOnError = value;
           
    }

    
    /**
     * 
     * xml name: ignore
     *  是否忽略此迁移（用于临时禁用某个迁移）
     */
    
    public boolean isIgnore(){
      return _ignore;
    }

    
    public void setIgnore(boolean value){
        checkAllowChange();
        
        this._ignore = value;
           
    }

    
    /**
     * 
     * xml name: labels
     *  标签（Labels）
     * 用于分组和选择性执行迁移
     */
    
    public java.lang.String getLabels(){
      return _labels;
    }

    
    public void setLabels(java.lang.String value){
        checkAllowChange();
        
        this._labels = value;
           
    }

    
    /**
     * 
     * xml name: preconditions
     *  前置条件检查（Preconditions）
     * 只有满足所有前置条件时才会执行此迁移
     */
    
    public java.util.List<io.nop.db.migration.model.DbPreconditionModel> getPreconditions(){
      return _preconditions;
    }

    
    public void setPreconditions(java.util.List<io.nop.db.migration.model.DbPreconditionModel> value){
        checkAllowChange();
        
        this._preconditions = KeyedList.fromList(value, io.nop.db.migration.model.DbPreconditionModel::getId);
           
    }

    
    public io.nop.db.migration.model.DbPreconditionModel getPrecondition(String name){
        return this._preconditions.getByKey(name);
    }

    public boolean hasPrecondition(String name){
        return this._preconditions.containsKey(name);
    }

    public void addPrecondition(io.nop.db.migration.model.DbPreconditionModel item) {
        checkAllowChange();
        java.util.List<io.nop.db.migration.model.DbPreconditionModel> list = this.getPreconditions();
        if (list == null || list.isEmpty()) {
            list = new KeyedList<>(io.nop.db.migration.model.DbPreconditionModel::getId);
            setPreconditions(list);
        }
        list.add(item);
    }
    
    public java.util.Set<String> keySet_preconditions(){
        return this._preconditions.keySet();
    }

    public boolean hasPreconditions(){
        return !this._preconditions.isEmpty();
    }
    
    /**
     * 
     * xml name: properties
     *  扩展属性（用于存储额外的元数据）
     */
    
    public java.util.Map<java.lang.String,java.lang.Object> getProperties(){
      return _properties;
    }

    
    public void setProperties(java.util.Map<java.lang.String,java.lang.Object> value){
        checkAllowChange();
        
        this._properties = value;
           
    }

    
    public java.lang.Object getProperty(String name){
        return this._properties.get(name);
    }

    public boolean hasProperty(String name){
        return this._properties.containsKey(name);
    }
    
    public boolean hasProperties(){
        return this._properties != null && !this._properties.isEmpty();
    }
    
    /**
     * 
     * xml name: rollback
     *  回滚定义（Rollback）
     * 定义如何回滚此迁移
     * 策略：
     * 1. 如果没有定义 rollback，Nop 会尝试自动生成回滚 SQL（仅限简单变更）
     * 2. 如果定义了 rollback，则使用定义的回滚逻辑
     * 3. 如果设置了 rollbackImpossible="true"，则标记此迁移不可回滚
     */
    
    public io.nop.db.migration.model.RollbackDefinition getRollback(){
      return _rollback;
    }

    
    public void setRollback(io.nop.db.migration.model.RollbackDefinition value){
        checkAllowChange();
        
        this._rollback = value;
           
    }

    
    /**
     * 
     * xml name: runOn
     *  运行时机：always(总是) | onChange(变更时) | never(从不)
     */
    
    public io.nop.db.migration.RunOnChange getRunOn(){
      return _runOn;
    }

    
    public void setRunOn(io.nop.db.migration.RunOnChange value){
        checkAllowChange();
        
        this._runOn = value;
           
    }

    
    /**
     * 
     * xml name: type
     *  迁移类型：versioned(版本化，只执行一次) | repeatable(可重复，每次校验和变化都重新执行)
     */
    
    public io.nop.db.migration.MigrationType getType(){
      return _type;
    }

    
    public void setType(io.nop.db.migration.MigrationType value){
        checkAllowChange();
        
        this._type = value;
           
    }

    
    /**
     * 
     * xml name: version
     *  迁移版本号（必须符合语义化版本规范）
     */
    
    public java.lang.String getVersion(){
      return _version;
    }

    
    public void setVersion(java.lang.String value){
        checkAllowChange();
        
        this._version = value;
           
    }

    

    @Override
    public void freeze(boolean cascade){
        if(frozen()) return;
        super.freeze(cascade);

        if(cascade){ //NOPMD - suppressed EmptyControlStatement - Auto Gen Code
        
           this._changeset = io.nop.api.core.util.FreezeHelper.deepFreeze(this._changeset);
            
           this._preconditions = io.nop.api.core.util.FreezeHelper.deepFreeze(this._preconditions);
            
           this._properties = io.nop.api.core.util.FreezeHelper.deepFreeze(this._properties);
            
           this._rollback = io.nop.api.core.util.FreezeHelper.deepFreeze(this._rollback);
            
        }
    }

    @Override
    protected void outputJson(IJsonHandler out){
        super.outputJson(out);
        
        out.putNotNull("author",this.getAuthor());
        out.putNotNull("changeset",this.getChangeset());
        out.putNotNull("comment",this.getComment());
        out.putNotNull("contexts",this.getContexts());
        out.putNotNull("description",this.getDescription());
        out.putNotNull("failOnError",this.isFailOnError());
        out.putNotNull("ignore",this.isIgnore());
        out.putNotNull("labels",this.getLabels());
        out.putNotNull("preconditions",this.getPreconditions());
        out.putNotNull("properties",this.getProperties());
        out.putNotNull("rollback",this.getRollback());
        out.putNotNull("runOn",this.getRunOn());
        out.putNotNull("type",this.getType());
        out.putNotNull("version",this.getVersion());
    }

    public DbMigrationModel cloneInstance(){
        DbMigrationModel instance = newInstance();
        this.copyTo(instance);
        return instance;
    }

    protected void copyTo(DbMigrationModel instance){
        super.copyTo(instance);
        
        instance.setAuthor(this.getAuthor());
        instance.setChangeset(this.getChangeset());
        instance.setComment(this.getComment());
        instance.setContexts(this.getContexts());
        instance.setDescription(this.getDescription());
        instance.setFailOnError(this.isFailOnError());
        instance.setIgnore(this.isIgnore());
        instance.setLabels(this.getLabels());
        instance.setPreconditions(this.getPreconditions());
        instance.setProperties(this.getProperties());
        instance.setRollback(this.getRollback());
        instance.setRunOn(this.getRunOn());
        instance.setType(this.getType());
        instance.setVersion(this.getVersion());
    }

    protected DbMigrationModel newInstance(){
        return (DbMigrationModel) ClassHelper.newInstance(getClass());
    }
}
 // resume CPD analysis - CPD-ON
