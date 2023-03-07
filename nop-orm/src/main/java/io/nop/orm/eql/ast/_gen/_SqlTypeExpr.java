//__XGEN_FORCE_OVERRIDE__
package io.nop.orm.eql.ast._gen;

import io.nop.orm.eql.ast.SqlTypeExpr;
import io.nop.orm.eql.ast.EqlASTNode; //NOPMD - suppressed UnusedImports - Auto Gen Code

import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.core.lang.json.IJsonHandler;
import io.nop.api.core.util.ProcessResult;
import java.util.function.Function;
import java.util.function.Consumer;


// tell cpd to start ignoring code - CPD-OFF
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.UnnecessaryImport","PMD.EmptyControlStatement"})
public abstract class _SqlTypeExpr extends io.nop.orm.eql.ast.SqlExpr {
    
    protected java.lang.String characterSet;
    
    protected java.lang.String collate;
    
    protected java.lang.String name;
    
    protected int precision;
    
    protected int scale;
    

    public _SqlTypeExpr(){
    }

    
    public java.lang.String getCharacterSet(){
        return characterSet;
    }

    public void setCharacterSet(java.lang.String value){
        checkAllowChange();
        
        this.characterSet = value;
    }
    
    public java.lang.String getCollate(){
        return collate;
    }

    public void setCollate(java.lang.String value){
        checkAllowChange();
        
        this.collate = value;
    }
    
    public java.lang.String getName(){
        return name;
    }

    public void setName(java.lang.String value){
        checkAllowChange();
        
        this.name = value;
    }
    
    public int getPrecision(){
        return precision;
    }

    public void setPrecision(int value){
        checkAllowChange();
        
        this.precision = value;
    }
    
    public int getScale(){
        return scale;
    }

    public void setScale(int value){
        checkAllowChange();
        
        this.scale = value;
    }
    

    public void validate(){
       super.validate();
     
          checkMandatory("name",getName());
       
    }


    public SqlTypeExpr newInstance(){
      return new SqlTypeExpr();
    }

    @Override
    public SqlTypeExpr deepClone(){
       SqlTypeExpr ret = newInstance();
    ret.setLocation(getLocation());
    ret.setLeadingComment(getLeadingComment());
    ret.setTrailingComment(getTrailingComment());
    copyExtFieldsTo(ret);
    
                if(name != null){
                  
                          ret.setName(name);
                      
                }
            
                ret.setPrecision(precision);
            
                ret.setScale(scale);
            
                if(characterSet != null){
                  
                          ret.setCharacterSet(characterSet);
                      
                }
            
                if(collate != null){
                  
                          ret.setCollate(collate);
                      
                }
            
       return ret;
    }

    @Override
    public void forEachChild(Consumer<EqlASTNode> processor){
    
    }

    @Override
    public ProcessResult processChild(Function<EqlASTNode,ProcessResult> processor){
    
       return ProcessResult.CONTINUE;
    }

    @Override
    public boolean replaceChild(EqlASTNode oldChild, EqlASTNode newChild){
    
        return false;
    }

    @Override
    public boolean removeChild(EqlASTNode child){
    
    return false;
    }

    @Override
    public boolean isEquivalentTo(EqlASTNode node){
       if(this.getASTKind() != node.getASTKind())
          return false;
    SqlTypeExpr other = (SqlTypeExpr)node;
    
                if(!isValueEquivalent(this.name,other.getName())){
                   return false;
                }
            
                if(!isValueEquivalent(this.precision,other.getPrecision())){
                   return false;
                }
            
                if(!isValueEquivalent(this.scale,other.getScale())){
                   return false;
                }
            
                if(!isValueEquivalent(this.characterSet,other.getCharacterSet())){
                   return false;
                }
            
                if(!isValueEquivalent(this.collate,other.getCollate())){
                   return false;
                }
            
        return true;
    }

    @Override
    public EqlASTKind getASTKind(){
       return EqlASTKind.SqlTypeExpr;
    }

    protected void serializeFields(IJsonHandler json) {
        
                    if(name != null){
                      
                              json.put("name", name);
                          
                    }
                
                   json.put("precision", precision);
                
                   json.put("scale", scale);
                
                    if(characterSet != null){
                      
                              json.put("characterSet", characterSet);
                          
                    }
                
                    if(collate != null){
                      
                              json.put("collate", collate);
                          
                    }
                
    }

    @Override
    public void freeze(boolean cascade){
      super.freeze(cascade);
        
    }

}
 // resume CPD analysis - CPD-ON
