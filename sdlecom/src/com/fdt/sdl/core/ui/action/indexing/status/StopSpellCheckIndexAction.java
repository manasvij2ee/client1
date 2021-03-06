package com.fdt.sdl.core.ui.action.indexing.status;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import net.javacoding.xsearch.config.DatasetConfiguration;
import net.javacoding.xsearch.search.SpellCheckManager;

import org.apache.struts.action.ActionMapping;

/**
 * Close the reader, searcher.
 */

public final class StopSpellCheckIndexAction extends AbstractIndexAction {
    
    @Override
    void doExecute(ActionMapping mapping, HttpServletRequest request, DatasetConfiguration dc, List<String> errors) {
        SpellCheckManager.stopSpellChecker(dc);
        SpellCheckManager.stopPhraseSuggester(dc);
        /** This line is commented as it is not required **/
        //System.gc();
    }

}
