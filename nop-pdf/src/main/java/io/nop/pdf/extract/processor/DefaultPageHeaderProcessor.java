package io.nop.pdf.extract.processor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.pdf.extract.IResourceDocumentProcessor;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;

/**
 * 根据每页顶部几行重复情况处理页眉. 
 * A. 如果考虑折行,可以采用将前后两行合并的做法 
 * B. 连续3个同样的页眉,即可认定为页眉, 排除表格的情况 
 * C. 一个很长的横线上方是文字, 这必然是页眉
 */
public class DefaultPageHeaderProcessor implements IResourceDocumentProcessor {

	private static final Logger LOG = LoggerFactory
			.getLogger(DefaultPageHeaderProcessor.class);

	/**
	 * 搜索页眉模式时最多检查多少行(块)
	 */
	private int maxSearchBlock = 5;

	/**
	 * 搜索页眉模式时用于判定的最小重复出现比例，超过比例的文本块或者图片。这个参数很关键。
	 */
	private double minRepeatRatio = 0.3;

	public DefaultPageHeaderProcessor() {
	}

	public DefaultPageHeaderProcessor(int maxSearchBlock, double minRepeatRatio) {

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
		BlockSimilarityAnalyzer analyzer = new BlockSimilarityAnalyzer();
		List<List<String>> blockTexts = analyzer.prepareForHeader(doc, maxSearchBlock);

		for(int i=0, n=doc.getPages().size();i<n;i++){
			ResourcePage page = doc.getPages().get(i);
			
			List<Block> pageBlocks = page.getSortedBlocks();
			List<String> blocks = blockTexts.get(i);
			int idx = analyzer.findMatch(blocks, true);
			
			if (idx < 0)
				continue;

			List<Block> header = new ArrayList<Block>(idx + 1);
			while (idx >= 0) {
				idx--;
				Block block = pageBlocks.remove(0);
				header.add(block);

				if(block != null){
					LOG.info("page {} header {}", page.getPageNo(),
							analyzer.getContent(block));
				}
			}

			page.setPageHeader(header);
		}
	}
}