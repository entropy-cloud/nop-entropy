//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlOrderByItem;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD NOSONAR - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S116","java:S3008","java:S1602",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlOrderByItem extends EqlASTNode {
    
    protected boolean asc;
    
    protected java.lang.String collate;
    
    protected io.nop.orm.eql.ast.SqlExpr expr;
    
    protected java.lang.Boolean nullsFirst;
    

    public _SqlOrderByItem(){
    }

    
    public boolean getAsc(){
        return asc;
    }

    public void setAsc(boolean value){
        checkAllowChange();
        
        this.asc = value;
    }
    
    public java.lang.String getCollate(){
        return collate;
    }

    public void setCollate(java.lang.String value){
        checkAllowChange();
        
        this.collate = value;
    }
    
    public io.nop.orm.eql.ast.SqlExpr getExpr(){
        return expr;
    }

    public void setExpr(io.nop.orm.eql.ast.SqlExpr value){
        checkAllowChange();
        if(value != null) value.setASTParent(this);
        
        this.expr = value;
    }
    
    public java.lang.Boolean getNullsFirst(){
        return nullsFirst;
    }

    public void setNullsFirst(java.lang.Boolean value){
        checkAllowChange();
        
        this.nullsFirst = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("expr",getExpr());
       
    }


    public SqlOrderByItem newInstance(){
      return new SqlOrderByItem();
    }

    @Override
    public SqlOrderByItem deepClone(){
       SqlOrderByItem ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(expr != null){
                  
                          ret.setExpr(expr.deepClone());
                      
                }
            
                if(collate != null){
                  
                          ret.setCollate(collate);
                      
                }
            
                ret.setAsc(asc);
            
                if(nullsFirst != null){
                  
                          ret.setNullsFirst(nullsFirst);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
            if(expr != null)
                processor.accept(expr);
        
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
            if(expr != null && processor.apply(expr) == ProcessResult.STOP)
               return ProcessResult.STOP;
        
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
            if(this.expr == oldChild){
               this.setExpr((io.nop.orm.eql.ast.SqlExpr)newChild);
               return true;
            }
        
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
            if(this.expr == child){
                this.setExpr(null);
                return true;
            }
        
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlOrderByItem other = (SqlOrderByItem)node;
    
            if(!isNodeEquivalent(this.expr,other.getExpr())){
               return false;
            }
        
                if(!isValueEquivalent(this.collate,other.getCollate())){
                   return false;
                }
            
                if(!isValueEquivalent(this.asc,other.getAsc())){
                   return false;
                }
            
                if(!isValueEquivalent(this.nullsFirst,other.getNullsFirst())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlOrderByItem;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(expr != null){
                      
                              json.put("expr", expr);
                          
                    }
                
                    if(collate != null){
                      
                              json.put("collate", collate);
                          
                    }
                
                   json.put("asc", asc);
                
                    if(nullsFirst != null){
                      
                              json.put("nullsFirst", nullsFirst);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
                if(expr != null)
                    expr.freeze(cascade);
    }

}
 // resume CPD analysis - CPD-ON
