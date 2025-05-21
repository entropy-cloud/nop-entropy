package io.nop.pdf.extract.struct;

import java.util.List;

public class FullTableBlock {
	String name;
	int lastPageNo;
	
	List<TableBlock> tables;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getLastPageNo() {
		return lastPageNo;
	}

	public void setLastPageNo(int lastPageNo) {
		this.lastPageNo = lastPageNo;
	}
	
	public List<TableBlock> getTables(){
		return tables;
	}
	
	public void setTables(List<TableBlock> tables){
		this.tables = tables;
	}
}