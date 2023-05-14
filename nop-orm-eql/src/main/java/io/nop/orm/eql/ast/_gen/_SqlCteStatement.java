//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlCteStatement;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlCteStatement extends EqlASTNode {
    
    protected java.lang.String name;
    
    protected io.nop.orm.eql.ast.SqlSelect statement;
    

    public _SqlCteStatement(){
    }

    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    
    public io.nop.orm.eql.ast.SqlSelect getStatement(){
        return statement;
    }

    public void setStatement(io.nop.orm.eql.ast.SqlSelect value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.statement = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
          checkMandatory("statement",getStatement());
       
    }


    public SqlCteStatement newInstance(){
      return new SqlCteStatement();
    }

    @Override
    public SqlCteStatement deepClone(){
       SqlCteStatement ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                if(statement != null){
                  
                          ret.setStatement(statement.deepClone());
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(statement != null)
                processor.accept(statement);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(statement != null && processor.apply(statement) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.statement == oldChild){
               this.setStatement((io.nop.orm.eql.ast.SqlSelect)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.statement == child){
                this.setStatement(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlCteStatement other = (SqlCteStatement)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
            if(!isNodeEquivalent(this.statement,other.getStatement())){
               return false;
            }
        
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlCteStatement;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                    if(statement != null){
                      
                              json.put("statement", statement);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(statement != null)
                    statement.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
