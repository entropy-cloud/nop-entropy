package io.nop.pdf.extract.processor;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.pdf.extract.IResourceDocumentProcessor;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ImageBlock;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.pdf.extract.struct.TextlineBlock;

public class DefaultPageFooterProcessor1 implements IResourceDocumentProcessor {

	private static final Logger LOG = LoggerFactory
			.getLogger(DefaultPageFooterProcessor.class);

	/**
	 * 模式匹配时检查的行数
	 */
	private int maxSearchBlocks = 3;

	private List<Pattern> matchPatterns = null;

	public DefaultPageFooterProcessor1() {

		this.loadDefaultPatterns();
	}

	public int getMaxSearchBlocks() {
		return maxSearchBlocks;
	}

	public void setMaxSearchBlocks(int maxSearchBlocks) {
		this.maxSearchBlocks = maxSearchBlocks;
	}

	public void setMatchPatterns(List<String> matchPatterns) {
		List<Pattern> patterns = new ArrayList<Pattern>(matchPatterns.size());
		for (String pattern : matchPatterns) {
			patterns.add(Pattern.compile(pattern));
		}

		this.matchPatterns = patterns;
	}

	private void loadDefaultPatterns() {

		List<String> patterns = new ArrayList<String>();
		patterns.add(".*第.+页.*共.+页");
		patterns.add(".*第.+页.*");
		patterns.add("\\d+/\\d+");
		patterns.add("-.{0,1}\\d+.{0,1}-");
		patterns.add("\\d+");

		this.setMatchPatterns(patterns);
	}

	@Override
	public void process(ResourceDocument doc, ResourceParseConfig config){
		for(ResourcePage page: doc.getPages()){
			this.processPage(page);
		}
	}

	public void processPage(ResourcePage page) {

		List<Block> blocks = page.getSortedBlocks();
		if (blocks.size() == 0)
			return;

		Rectangle2D pageViewRect = page.getPageViewBounding();
		
		List<Block> footers = new ArrayList<Block>();

		int n = maxSearchBlocks;
		for (int i = 0; i < n; i++) {

			int index = blocks.size() - n + i;
			if (index < 0)
				continue;

			Block block = blocks.get(index);
			if (block == null)
				continue;

			Rectangle2D rect = block.getViewBounding();

			// 排除页面上部分
			if (pageViewRect != null && !pageViewRect.isEmpty()) {

				double percent = (rect.getMinY() - pageViewRect.getMinY())
						/ pageViewRect.getHeight();
				if (percent < 0.75)
					continue;
			}

			if (block instanceof TextlineBlock) {

				String text = ((TextlineBlock) block).getContent();
				if (text != null)
					text = text.trim();
				if (!text.isEmpty()) {
					if (this.matchText(text, rect, pageViewRect)) {
						LOG.info("page {} footer: {}", page.getPageNo(), text);
						footers.add(block);
						blocks.remove(index);
						i--;
						n--;
					}
				}

				continue;
			}

			if (block instanceof ImageBlock) {
				continue;
			}
		}
	}

	private boolean matchText(String text, Rectangle2D rect,
			Rectangle2D pageViewRect) {

		for (Pattern pattern : this.matchPatterns) {
			if (pattern.matcher(text).matches())
				return true;
		}
		return false;
	}

}
