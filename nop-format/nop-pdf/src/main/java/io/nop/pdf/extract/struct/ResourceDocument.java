package io.nop.pdf.extract.struct;

import java.util.Collections;
import java.util.List;


public class ResourceDocument {
	String fileName;
	List<ResourcePage> pages = Collections.emptyList();

	List<FullTableBlock> fullTables;

	TocTable tocTable;

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public List<ResourcePage> getPages() {
		return pages;
	}

	public void setPages(List<ResourcePage> pages) {
		this.pages = pages;
	}

	public List<FullTableBlock> getFullTables() {
		return fullTables;
	}

	public void setFullTables(List<FullTableBlock> fullTables) {
		this.fullTables = fullTables;
	}

	public TocTable getTocTable() {
		return tocTable;
	}

	public void setTocTable(TocTable tocTable) {
		this.tocTable = tocTable;
	}

	public ResourcePage getPageByPageNo(int pageNo) {
		for (ResourcePage page : pages) {
			if (page.getPageNo() == pageNo)
				return page;
		}
		return null;
	}

	public ResourcePage getPageByDisplayPageNo(int displayPageNo) {
		for (ResourcePage page : pages) {
			if (page.getDisplayPageNo() == displayPageNo)
				return page;
		}
		return null;
	}
	
	public int getPageIndexByDisplayPageNo(int displayPageNo){
		for(int i=0, n=pages.size();i<n;i++){
			ResourcePage page = pages.get(i);
			if (page.getDisplayPageNo() == displayPageNo)
				return i;
		}
		return -1;
	}

//	public TableBlock findFirstTableAfterText(int fromPageIndex,
//			ITextMatcher matcher, Predicate<TableBlock> filter) {
//		for (int i = fromPageIndex, n = pages.size(); i < n; i++) {
//			ResourcePage page = pages.get(i);
//			TableBlock block = page.findFirstTableAfterText(matcher, filter);
//			if (block != null){
//				return block;
//			}
//		}
//		return null;
//	}
}