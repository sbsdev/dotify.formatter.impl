package org.daisy.dotify.formatter.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.daisy.dotify.api.formatter.ContentCollection;
import org.daisy.dotify.api.formatter.Formatter;
import org.daisy.dotify.api.formatter.FormatterConfiguration;
import org.daisy.dotify.api.formatter.FormatterSequence;
import org.daisy.dotify.api.formatter.LayoutMasterBuilder;
import org.daisy.dotify.api.formatter.LayoutMasterProperties;
import org.daisy.dotify.api.formatter.SequenceProperties;
import org.daisy.dotify.api.formatter.TableOfContents;
import org.daisy.dotify.api.formatter.VolumeTemplateBuilder;
import org.daisy.dotify.api.formatter.VolumeTemplateProperties;
import org.daisy.dotify.api.translator.BrailleTranslatorFactoryMakerService;
import org.daisy.dotify.api.translator.MarkerProcessorFactoryMakerService;
import org.daisy.dotify.api.translator.TextBorderFactoryMakerService;
import org.daisy.dotify.api.writer.PagedMediaWriter;
import org.daisy.dotify.writer.impl.Volume;
import org.daisy.dotify.writer.impl.WriterHandler;


/**
 * Breaks flow into rows, page related block properties are left to next step
 * @author Joel Håkansson
 */
public class FormatterImpl implements Formatter {
	private static final int DEFAULT_SPLITTER_MAX = 50;
	
	private final HashMap<String, TableOfContentsImpl> tocs;
	private final Stack<VolumeTemplate> volumeTemplates;
	private final Logger logger;

	private boolean unopened;
	private final Stack<BlockSequence> blocks;
	
	//CrossReferenceHandler
	private final CrossReferenceHandler crh;
	private final LazyFormatterContext context;

	/**
	 * Creates a new formatter.
	 * @param translatorFactory a braille translator factory maker service
	 * @param tbf a text border factory maker service
	 * @param mpf a marker processor factory maker service
	 * @param locale a locale
	 * @param mode a braille mode
	 */
	public FormatterImpl(BrailleTranslatorFactoryMakerService translatorFactory, TextBorderFactoryMakerService tbf, MarkerProcessorFactoryMakerService mpf, String locale, String mode) {
		this.context = new LazyFormatterContext(translatorFactory, tbf, mpf, FormatterConfiguration.with(locale, mode).build());
		this.blocks = new Stack<>();
		this.unopened = true;
		this.tocs = new HashMap<>();
		this.volumeTemplates = new Stack<>();
		
		this.logger = Logger.getLogger(this.getClass().getCanonicalName());
		
		//CrossReferenceHandler
		this.crh = new CrossReferenceHandler();
	}
	

	@Override
	public FormatterConfiguration getConfiguration() {
		return context.getFormatterContext().getConfiguration();
	}

	@Override
	public void setConfiguration(FormatterConfiguration config) {
		//TODO: we require unopened at the moment due to limitations in the implementation
		if (!unopened) {
			throw new IllegalStateException("Configuration must happen before use.");
		}
		context.setConfiguration(config);
	}
	
	@Override
	public FormatterSequence newSequence(SequenceProperties p) {
		unopened = false;
		BlockSequence currentSequence = new BlockSequence(context.getFormatterContext(), p, context.getFormatterContext().getMasters().get(p.getMasterName()));
		blocks.push(currentSequence);
		return currentSequence;
	}

	@Override
	public LayoutMasterBuilder newLayoutMaster(String name,
			LayoutMasterProperties properties) {
		unopened = false;
		return context.getFormatterContext().newLayoutMaster(name, properties);
	}

	@Override
	public VolumeTemplateBuilder newVolumeTemplate(VolumeTemplateProperties props) {
		unopened = false;
		VolumeTemplate template = new VolumeTemplate(context.getFormatterContext(), tocs, props.getCondition(), props.getSplitterMax());
		volumeTemplates.push(template);
		return template;
	}

	@Override
	public TableOfContents newToc(String tocName) {
		unopened = false;
		TableOfContentsImpl toc = new TableOfContentsImpl(context.getFormatterContext());
		tocs.put(tocName, toc);
		return toc;
	}

	@Override
	public ContentCollection newCollection(String collectionId) {
		unopened = false;
		return context.getFormatterContext().newContentCollection(collectionId);
	}
	
	@Override
	public void write(PagedMediaWriter writer) {
		unopened = false;
		try (WriterHandler wh = new WriterHandler(writer)) {
			wh.write(getVolumes());
		} catch (IOException e) {
			logger.log(Level.WARNING, "Failed to close resource.", e);
		}
	}

	private Iterable<? extends Volume> getVolumes() {
        SplitterLimit limit = volumeNumber -> {
            final DefaultContext c = new DefaultContext.Builder()
                    .currentVolume(volumeNumber)
                    .referenceHandler(crh)
                    .build();
            Optional<VolumeTemplate> ot = volumeTemplates.stream().filter(t -> t.appliesTo(c)).findFirst();
            if (ot.isPresent()) {
                return ot.get().getVolumeMaxSize();
            } else {
                logger.fine("Found no applicable volume template.");
                return DEFAULT_SPLITTER_MAX;                
            }
        };

		VolumeProvider volumeProvider = new VolumeProvider(blocks, volumeTemplates, limit, context, crh);

		ArrayList<VolumeImpl> ret;

		for (int j=1;j<=10;j++) {
			try {
				ret = new ArrayList<>();
				volumeProvider.prepare();
				for (int i=1;i<= crh.getVolumeCount();i++) {
					ret.add(volumeProvider.nextVolume());
				}
	
				volumeProvider.update();
				crh.setVolumeCount(volumeProvider.getVolumeCount());
				crh.setSheetsInDocument(volumeProvider.countTotalSheets());
				//crh.setPagesInDocument(value);
				if (volumeProvider.hasNext()) {
					if (logger.isLoggable(Level.FINE)) {
						logger.fine("There is more content (sheets: " + volumeProvider.countRemainingSheets() + ", pages: " + volumeProvider.countRemainingPages() + ")");
					}
					if (!crh.isDirty() && j>1) {
						volumeProvider.adjustVolumeCount();
					}
				}
				if (!crh.isDirty() && !volumeProvider.hasNext()) {
					//everything fits
					return ret;
				} else {
					crh.setDirty(false);
					logger.info("Things didn't add up, running another iteration (" + j + ")");
				}
			} catch (RestartPaginationException e) {
				// don't count this round, simply restart
				j--;
			}
		}
		throw new RuntimeException("Failed to complete volume division.");
	}

}
