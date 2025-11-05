package io.nop.pdf.extract.processor;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.pdf.extract.IResourceDocumentProcessor;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TableBlock;
import io.nop.pdf.extract.struct.TextlineBlock;

/**
 * 根据每页顶部几行重复情况处理页眉. 
 * A. 如果考虑折行,可以采用将前后两行合并的做法
 * B. 连续3个同样的页眉,即可认定为页眉, 排除表格的情况
 * C. 一个很长的横线上方是文字, 这必然是页眉
 */
public class DefaultPageHeaderProcessor1 implements IResourceDocumentProcessor {

	private static final Logger LOG = LoggerFactory
			.getLogger(DefaultPageHeaderProcessor1.class);

	/**
	 * 搜索页眉模式时最多检查多少行(块)
	 */
	private int maxSearchBlock = 5;

	/**
	 * 搜索页眉模式时用于判定的最小重复出现比例，超过比例的文本块或者图片。这个参数很关键。
	 */
	private double minRepeatRatio = 0.3;

	/**
	 * 页眉内的文本
	 */
	private List<String> headerTexts = new ArrayList<String>();

	/**
	 * 页眉内的图片
	 */
	private List<String> headerImages = new ArrayList<String>();

	public DefaultPageHeaderProcessor1() {
	}

	public DefaultPageHeaderProcessor1(int maxSearchBlock, double minRepeatRatio) {

		this.maxSearchBlock = maxSearchBlock;
		this.minRepeatRatio = minRepeatRatio;
	}

	public int getMaxSearchBlock() {
		return maxSearchBlock;
	}

	public void setMaxSearchBlock(int maxSearchBlock) {
		this.maxSearchBlock = maxSearchBlock;
	}

	public double getMinRepeatRatio() {
		return minRepeatRatio;
	}

	public void setMinRepeatRatio(double minRepeatRatio) {
		this.minRepeatRatio = minRepeatRatio;
	}

	@Override
	public void process(ResourceDocument doc, ResourceParseConfig config) {
		this.prepare(doc.getPages());

		for (ResourcePage page : doc.getPages()) {
			this.processPage(page);
		}
	}

	public void processPage(ResourcePage page) {

		List<Block> blocks = page.getSortedBlocks();
		if (blocks.size() == 0)
			return;

		List<Block> header = new ArrayList<Block>();

		int idx = -1;
		for (int i = 0; i < maxSearchBlock && i < blocks.size(); i++) {

			Block block = blocks.get(i);
			if (block == null)
				continue;

			if (block instanceof TextlineBlock) {

				String text = ((TextlineBlock) block).getContent();
				if (text != null) {
					text = normalizeText(text);
				}
				if(text == null || text.length() <= 0)
					continue;

				if (this.headerTexts.indexOf(text) > -1) {

					LOG.info("page {} header: {}", page.getPageNo(), text);
					idx = i;
				}

				continue;
			}

			if (block instanceof ImageBlock) {

				Rectangle2D rect = block.getViewBounding();
				if (!rect.isEmpty()) {
					String text = encodeImageSize(rect);
					if (this.headerImages.indexOf(text) > -1) {

						LOG.info("page {} header: {}", page.getPageNo(), text);
						idx = i;
					}
				}

				continue;
			}
			
			// 认为表格不可能是页眉的一部分
			if(block instanceof TableBlock){
				TableBlock table = (TableBlock)block;
				if(table.getColCount() > 1 || table.getRowCount() > 1)
					break;
			}
		}

		while (idx >= 0) {
			idx--;
			Block block = blocks.remove(0);
			header.add(block);
		}

		page.setPageHeader(header);
	}

	/**
	 * 遍历所有页，根据每页前几行，寻找页眉模式
	 * 
	 * @param pages
	 */
	private void prepare(List<ResourcePage> pages) {

		headerTexts.clear();
		headerImages.clear();

		// 按页统计
		Map<String, Integer> textCounter = new HashMap<String, Integer>();
		Map<String, Integer> imageCounter = new HashMap<String, Integer>();

		for (int i = 0; i < pages.size(); i++) {

			ResourcePage page = pages.get(i);

			Rectangle2D viewBounding = page.getPageViewBounding();

			List<Block> blocks = page.getSortedBlocks();
			if (blocks.size() == 0)
				continue;

			for (int j = 0; j < maxSearchBlock && j < blocks.size(); j++) {

				Block block = blocks.get(j);
				if (block == null)
					continue;

				Rectangle2D rect = block.getViewBounding();

				// 排除页面下半部分
				if (viewBounding != null && !viewBounding.isEmpty()) {

					if ((rect.getMinY() - viewBounding.getMinY())
							/ viewBounding.getHeight() > 0.3) {
						continue;
					}
				}

				if (block instanceof TextlineBlock) {

					String text = ((TextlineBlock) block).getContent();
					if (text != null) {
						LOG.info("prepare {} - {} : {}", page.getPageNo(),
								j, text);
						text = normalizeText(text);
						if (!text.isEmpty()) {
							if (textCounter.containsKey(text)) {

								int num = textCounter.get(text);
								textCounter.put(text, num + 1);
							} else
								textCounter.put(text, 1);
						}
					}
					continue;
				}
				if (block instanceof ImageBlock) {

					if (!rect.isEmpty()) {
						String text = encodeImageSize(rect);
						if (imageCounter.containsKey(text)) {

							int num = imageCounter.get(text);
							imageCounter.put(text, num + 1);
						} else
							imageCounter.put(text, 1);
					}
					continue;
				}
				
				// 认为表格不可能是页眉的一部分
				if(block instanceof TableBlock){
					TableBlock table = (TableBlock)block;
					if(table.getColCount() > 1 || table.getRowCount() > 1)
						break;
				}
			}
		}

		// 按出现次数判断是否为页眉内的东西
		Iterator<String> iterator = textCounter.keySet().iterator();
		while (iterator.hasNext()) {

			String text = iterator.next();
			int count = textCounter.containsKey(text) ? textCounter.get(text)
					: 0;

			if (isRepeatMany(count, pages.size())) {

				LOG.info("header pattern found! text = {} {}/{}", text, count,
						pages.size());
				this.headerTexts.add(text);
			}
		}

		iterator = imageCounter.keySet().iterator();
		while (iterator.hasNext()) {

			String text = iterator.next();
			int count = imageCounter.containsKey(text) ? imageCounter.get(text)
					: 0;

			if (isRepeatMany(count, pages.size())) {

				LOG.info("header pattern found! image = {} {}/{}", text, count,
						pages.size());
				this.headerImages.add(text);
			}
		}
	}

	/**
	 * 至少重复3次, 重复次数达到一定比例或者超过一定次数
	 * @param count
	 * @param pageCount
	 * @return
	 */
	boolean isRepeatMany(int count, int pageCount) {
		return (count >= 3 && count > pageCount * minRepeatRatio) || count > 7;
	}

	String normalizeText(String str) {
		return new StringProcessor(str).removeWhitespace().normalizeDigit()
				.replaceInteger("$").toString();
	}

	private String encodeImageSize(Rectangle2D rect) {

		int w = ((int) Math.floor(rect.getWidth() * 10000)) / 100;
		int h = ((int) Math.floor(rect.getHeight() * 10000)) / 100;

		return String.format("#IMAGE:%d,%d", w, h);
	}
}
