package io.nop.pdf.extract.struct;

import java.util.List;

/**
 * 目录表
 * 
 */
public class TocTable {

	private List<TocItem> items;
	private int fromPageNo;
	private int toPageNo;

	public List<TocItem> getItems() {
		return items;
	}

	public void setItems(List<TocItem> items) {
		this.items = items;
	}

	public int getFromPageNo() {
		return fromPageNo;
	}

	public void setFromPageNo(int fromPageNo) {
		this.fromPageNo = fromPageNo;
	}

	public int getToPageNo() {
		return toPageNo;
	}

	public void setToPageNo(int toPageNo) {
		this.toPageNo = toPageNo;
	}
}
