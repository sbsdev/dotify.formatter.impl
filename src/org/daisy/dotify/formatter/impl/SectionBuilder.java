package org.daisy.dotify.formatter.impl;

import java.util.List;
import java.util.Stack;

import org.daisy.dotify.writer.impl.Section;

class SectionBuilder {
    private Stack<Section> ret = new Stack<Section>();
    private PageSequence currentSeq = null;
    private int sheets = 0;
    
    private void addPage(PageImpl p) {
        if (ret.isEmpty() || currentSeq!=p.getSequenceParent()) {
            currentSeq = p.getSequenceParent();
            ret.add(
                    new SectionImpl(currentSeq.getSectionProperties())
                    //new PageSequence(ret, currentSeq.getLayoutMaster(), currentSeq.getPageNumberOffset())
                    );
        }
        ((SectionImpl)ret.peek()).addPage(p);
    }
    
    void addSheet(Sheet s) {
        sheets++;
        for (PageImpl p : s.getPages()) {
            addPage(p);
        }
    }
    
    List<Section> getSections() {
        return ret;
    }
    
    int getSheetCount() {
        return sheets;
    }

}