package io.nop.pdf.extract.struct;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;


/**
 * 用于遍历ResourceDocument
 */
public class BlockPointer {
	ResourceDocument doc;
	int pageIndex;
	int blockIndex;

	public BlockPointer(ResourceDocument doc) {
		this.doc = doc;
	}

	public BlockPointer moveToPage(int pageIndex) {
		this.pageIndex = pageIndex;
		return this;
	}

	public BlockPointer moveToBlock(int blockIndex) {
		this.blockIndex = blockIndex;
		return this;
	}

	public int getPageIndex() {
		return pageIndex;
	}

	public int getBlockIndex() {
		return blockIndex;
	}

	public BlockPointer duplicate() {
		return new BlockPointer(doc).moveToPage(pageIndex).moveToBlock(
				blockIndex);
	}

	public BlockPointer moveTo(BlockPointer pt) {
		this.moveToPage(pt.getPageIndex()).moveToBlock(pt.getBlockIndex());
		return this;
	}

	public ResourceDocument doc() {
		return doc;
	}

	public ResourcePage page() {
		List<ResourcePage> pages = this.doc.getPages();
		if (pageIndex >= pages.size())
			return null;
		return pages.get(pageIndex);
	}

	public ResourcePage nextPage() {
		List<ResourcePage> pages = this.doc.getPages();
		pageIndex++;
		if (pageIndex >= pages.size()) {
			pageIndex = pages.size();
			return null;
		}
		return pages.get(pageIndex);
	}

	public Block block() {
		List<Block> blocks = page().getSortedBlocks();
		if (blockIndex >= blocks.size())
			return null;
		return blocks.get(blockIndex);
	}

	public Block nextBlock(boolean allowNextPage) {
		List<Block> blocks = page().getSortedBlocks();
		blockIndex++;
		if (blockIndex >= blocks.size()) {
			if (allowNextPage) {
				this.nextPage();
				blockIndex = -1;
				return this.nextBlock(allowNextPage);
			}
			blockIndex = blocks.size();
			return null;
		}
		return blocks.get(blockIndex);
	}

//	public boolean findText(ITextMatcher matcher, boolean allowNextPage, int maxCount,
//			TextMatchState state) {
//		if (state == null)
//			state = new TextMatchState();
//
//		int count = 0;
//		do {
//			Block block = nextBlock(true);
//			if (block == null)
//				break;
//			count ++;
//
//			if (block instanceof TextlineBlock) {
//				TextInput input = ((TextlineBlock) block).getNormalizedInput();
//				if (matcher.match(input, state)) {
//					return false;
//				}
//			}
//
//			if(maxCount > 0 && count >= maxCount)
//				break;
//
//		} while (true);
//		return true;
//	}
//
//	public boolean findText(ITextMatcher matcher, TextMatchState state) {
//		return findText(matcher, true, -1,  state);
//	}
//
//	public boolean findTextInPage(ITextMatcher matcher, TextMatchState state) {
//		return findText(matcher, false, -1, state);
//	}
//
//	public TocItem findTocItem(ITextMatcher matcher, TextMatchState state) {
//		TocTable toc = doc.getTocTable();
//		if (toc == null || toc.getItems() == null)
//			return null;
//		for (TocItem item : toc.getItems()) {
//			if (matcher.match(item.getNormalizedInput(), state))
//				return item;
//		}
//		return null;
//	}

	/**
	 * 从目录中查找到段落对应的页面位置，　并定位到段落所在文本块
	 * @param matcher
	 * @param state
	 * @return
	 */
//	public boolean findByToc(ITextMatcher matcher, TextMatchState state) {
//		TocItem item = this.findTocItem(matcher, state);
//		if (item == null)
//			return false;
//
//		int pageIndex = doc.getPageIndexByDisplayPageNo(item.getTocPageNo());
//		if (pageIndex < 0)
//			return false;
//
//		this.moveToPage(pageIndex);
//		return findTextInPage(matcher, state);
//	}

	/**
	 * 向后查找第一个表格
	 * @param filter
	 * @return
	 */
	public boolean findTable(Predicate<TableBlock> filter) {
		do {
			Block block = nextBlock(true);
			if (block == null)
				break;
			if (block instanceof TableBlock) {
				TableBlock table = (TableBlock) block;
				if (filter == null || filter.test(table))
					return true;
			}
		} while (true);
		return false;
	}

	public TableBlock table() {
		return (TableBlock) block();
	}

	/**
	 * 从当前的TableBlock开始，向后收集所有连续的表格. PDF打印时一些表格会因为分页的原因被分隔为多个表格
	 * @return
	 */
	public List<TableBlock> collectPageTables() {
		List<TableBlock> ret = new ArrayList<TableBlock>();
		Block block = block();
		if (block == null)
			return ret;
		if (!(block instanceof TableBlock))
			return ret;

		ret.add((TableBlock) block);

		do {
			Block nextBlock = nextBlock(true);
			if (nextBlock == null)
				break;

			if (!(nextBlock instanceof TableBlock)) {
				// 不是表格且不是空行，　则中断
				if (nextBlock instanceof TextlineBlock) {
					TextlineBlock text = ((TextlineBlock) nextBlock);
					if (text.isEmpty())
						continue;
				}
				break;
			} else {
				TableBlock table = (TableBlock)nextBlock;
				// 如果是分页导致的表格拆分，则下一个表格必然在下一页
				if(nextBlock.getPageNo() != block.getPageNo() + 1)
					break;
				
				ret.add(table);
			}
		} while (true);

		return ret;
	}
}