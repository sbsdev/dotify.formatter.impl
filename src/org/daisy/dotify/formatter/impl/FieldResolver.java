package org.daisy.dotify.formatter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.daisy.dotify.api.formatter.CompoundField;
import org.daisy.dotify.api.formatter.CurrentPageField;
import org.daisy.dotify.api.formatter.Field;
import org.daisy.dotify.api.formatter.FieldList;
import org.daisy.dotify.api.formatter.MarkerReferenceField;
import org.daisy.dotify.api.formatter.NoField;
import org.daisy.dotify.api.translator.BrailleTranslator;
import org.daisy.dotify.api.translator.DefaultTextAttribute;
import org.daisy.dotify.api.translator.Translatable;
import org.daisy.dotify.api.translator.TranslationException;
import org.daisy.dotify.formatter.impl.search.CrossReferenceHandler;

class FieldResolver {
	private static final Pattern softHyphen = Pattern.compile("\u00ad");
	private final LayoutMaster master;
	private final FormatterContext fcontext;
	private final CrossReferenceHandler crh;

	FieldResolver(LayoutMaster master, FormatterContext fcontext, CrossReferenceHandler crh) {
		this.master = master;
		this.fcontext = fcontext;
		this.crh = crh;
	}
	
    List<RowImpl> renderFields(PageImpl p, List<FieldList> fields, BrailleTranslator translator) throws PaginatorException {
        ArrayList<RowImpl> ret = new ArrayList<>();
		for (FieldList row : fields) {
            try {
                RowImpl r = new RowImpl(distribute(p, row, master.getFlowWidth(), fcontext.getSpaceCharacter()+"", translator));
                r.setRowSpacing(row.getRowSpacing());
                ret.add(r);
            } catch (PaginatorToolsException e) {
                throw new PaginatorException("Error while rendering header", e);
			}
		}
		return ret;
	}
	
    private String distribute(PageImpl p, FieldList chunks, int width, String padding, BrailleTranslator translator) throws PaginatorToolsException {
		ArrayList<String> chunkF = new ArrayList<>();
		for (Field f : chunks.getFields()) {
			DefaultTextAttribute.Builder b = new DefaultTextAttribute.Builder(null);
            String resolved = softHyphen.matcher(resolveField(f, p, b)).replaceAll("");
			Translatable.Builder tr = Translatable.text(fcontext.getConfiguration().isMarkingCapitalLetters()?resolved:resolved.toLowerCase()).
										hyphenate(false);
			if (resolved.length()>0) {
				tr.attributes(b.build(resolved.length()));
			}
			try {
				chunkF.add(translator.translate(tr.build()).getTranslatedRemainder());
			} catch (TranslationException e) {
				throw new PaginatorToolsException(e);
			}
		}
        return PaginatorTools.distribute(chunkF, width, padding,
                fcontext.getConfiguration().isAllowingTextOverflowTrimming()?
                PaginatorTools.DistributeMode.EQUAL_SPACING_TRUNCATE:
                PaginatorTools.DistributeMode.EQUAL_SPACING
            );
	}
	
	/*
	 * Note that the result of this function is not constant because getPageInSequenceWithOffset(),
	 * getPageInVolumeWithOffset() and shouldAdjustOutOfBounds() are not constant.
	 */
	private String resolveField(Field field, PageImpl p, DefaultTextAttribute.Builder b) {
		if (field instanceof NoField) {
			throw new UnsupportedOperationException("Not implemented");
		}
		String ret;
		DefaultTextAttribute.Builder b2 = new DefaultTextAttribute.Builder(field.getTextStyle());
		if (field instanceof CompoundField) {
			ret = resolveCompoundField((CompoundField)field, p, b2);
		} else if (field instanceof MarkerReferenceField) {
			ret = crh.findMarker(p.getDetails().getPageId(), (MarkerReferenceField)field);
		} else if (field instanceof CurrentPageField) {
			ret = resolveCurrentPageField((CurrentPageField)field, p);
		} else {
			ret = field.toString();
		}
		if (ret.length()>0) {
			b.add(b2.build(ret.length()));
		}
		return ret;
	}

	private String resolveCompoundField(CompoundField f, PageImpl p, DefaultTextAttribute.Builder b) {
		return f.stream().map(f2 -> resolveField(f2, p, b)).collect(Collectors.joining());
	}
	
	private static String resolveCurrentPageField(CurrentPageField f, PageImpl p) {
		int pagenum = p.getPageIndex() + 1;
		return f.getNumeralStyle().format(pagenum);
	}


}
