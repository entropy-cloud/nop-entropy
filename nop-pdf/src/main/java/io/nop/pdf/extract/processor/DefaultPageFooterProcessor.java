package io.nop.pdf.extract.processor;

import io.nop.pdf.extract.IResourceDocumentProcessor;
import io.nop.pdf.extract.ResourceParseConfig;
import io.nop.pdf.extract.struct.Block;
import io.nop.pdf.extract.struct.ResourceDocument;
import io.nop.pdf.extract.struct.ResourcePage;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class DefaultPageFooterProcessor implements IResourceDocumentProcessor {

    private static final Logger LOG = LoggerFactory
            .getLogger(DefaultPageFooterProcessor.class);

    /**
     * 模式匹配时检查的行数
     */
    private int maxSearchBlocks = 3;

    List<Pattern> matchPatterns = null;

    public DefaultPageFooterProcessor() {

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
    public void process(ResourceDocument doc, ResourceParseConfig config) {
        BlockSimilarityAnalyzer analyzer = new BlockSimilarityAnalyzer();
        List<List<String>> blockTexts = analyzer.prepareForFooter(doc,
                maxSearchBlocks);

        int totalPage = doc.getPages().size();
        List<List<Integer>> allPageNos = new ArrayList<List<Integer>>(totalPage);

        for (int i = 0, n = doc.getPages().size(); i < n; i++) {
            ResourcePage page = doc.getPages().get(i);

            List<Block> pageBlocks = page.getSortedBlocks();
            List<String> blocks = blockTexts.get(i);
            int idx = analyzer.findMatch(blocks, false);

            if (idx < 0)
                continue;

            List<Block> footer = new ArrayList<Block>(blocks.size() - idx);
            for (int j = blocks.size() - 1; j >= idx; j--) {
                Block block = pageBlocks.remove(pageBlocks.size() - 1);
                footer.add(block);

                if (block != null) {
                    LOG.info("page {} footer {}", page.getPageNo(),
                            analyzer.getContent(block));
                }
            }

            List<Integer> pageNos = this.findPossiblePageNo(footer,
                    page.getPageNo(), totalPage, analyzer);
            allPageNos.add(pageNos);

            page.setPageFooter(footer);
        }

        this.setDisplayPageNos(doc.getPages(), allPageNos);
    }

    // 返回所有可能的pageNo列表
    List<Integer> findPossiblePageNo(List<Block> blocks, int currentPage,
                                     int totalPage, BlockSimilarityAnalyzer analyzer) {
        List<Integer> ret = new ArrayList<Integer>();

        for (Block block : blocks) {
            if (block == null)
                continue;

            String content = analyzer.getContent(block);
            if (content == null || content.length() <= 0)
                continue;

            StringProcessor sp = new StringProcessor(content).removeWhitespace();

            // 如果找到非常确定的模式,则直接返回
            int pageNo = parseSpecPageNo(sp);
            if (pageNo >= 0)
                return Arrays.asList(pageNo);

            this.detectPageNo(sp, currentPage, totalPage, ret);
        }
        return ret;
    }

    void detectPageNo(StringProcessor sp, int currentPage, int totalPage,
                      List<Integer> ret) {
        do {
            String s = sp.searchDigits();
            if (s == null)
                break;
            if (s.length() >= 5)
                continue;

            int pageNo = Integer.parseInt(s);
            if (pageNo > totalPage + 10)
                continue;

            // 偏离当前页数太远
            if (pageNo < currentPage - 10 || pageNo > currentPage + 10)
                continue;

            ret.add(pageNo);
        } while (true);
    }

    // 第x页 是非常明确的信号,直接使用
    int parseSpecPageNo(StringProcessor sp) {
        if (!sp.startsWith("第"))
            return -1;

        int pos = sp.pos();

        int pos2 = sp.move(1).find("页");
        if (pos2 < 0)
            return -1;
        String s = sp.substring(pos+1, pos2);
        return ConvertHelper.toPrimitiveInt(s, -1, NopException::new);
    }

    void setDisplayPageNos(List<ResourcePage> pages,
                           List<List<Integer>> allPageNos) {
        for (int i = 0, n = pages.size(); i < n; i++) {
            ResourcePage page = pages.get(i);
            List<Integer> pageNos = allPageNos.get(i);
            if (pageNos.isEmpty())
                continue;

            int pageNo;
            if (pageNos.size() == 1) {
                // 只有一个备选数字,则直接作为页码
                pageNo = pageNos.get(0);
            } else {
                // 能向上向下查找到连续的数字
                pageNo = findContinuousNum(pageNos, i, allPageNos);
            }

            LOG.info("pdf find displayPageNo {}", pageNo);
            page.setDisplayPageNo(pageNo);
        }
    }

    int findContinuousNum(List<Integer> pageNos, int i,
                          List<List<Integer>> allPageNos) {
        for (int pageNo : pageNos) {
            if (matchNumber(i + 1, allPageNos, pageNo + 1)) {
                return pageNo;
            }

            if (matchNumber(i + 2, allPageNos, pageNo + 2)) {
                return pageNo;
            }

            if (matchNumber(i - 1, allPageNos, pageNo - 1))
                return pageNo;

            if (matchNumber(i - 2, allPageNos, pageNo - 2))
                return pageNo;
        }
        return -1;
    }

    boolean matchNumber(int i, List<List<Integer>> allPageNos, int pageNo) {
        if (i < 0 || i >= allPageNos.size())
            return false;
        List<Integer> list = allPageNos.get(i);
        if (!list.contains(pageNo))
            return false;

        // 既然已经和其他编号匹配,则删除多余的数字
		/*
		if(list.size() > 1){
			for(int j=0,n=list.size();j<n;j++){
				if(list.get(j) != pageNo){
					list.remove(j);
					j--;
					n--;
				}
			}
		}*/
        return true;
    }
}