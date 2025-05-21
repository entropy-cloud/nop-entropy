package io.nop.pdf.extract.data;


public abstract class AbstractCellDataLocator implements ICellDataLocator {

    private String mKey = null;
    
    @Override
    public void setKey( String key ) {

        mKey = key;
    }

    @Override
    public String getKey() {

        return mKey;
    }
}
