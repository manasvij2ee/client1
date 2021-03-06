package com.fdt.sdl.core.ui.action.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.javacoding.xsearch.api.FacetChoice;
import net.javacoding.xsearch.api.FacetCount;
import net.javacoding.xsearch.api.Result;
import net.javacoding.xsearch.api.SearchConnection;
import net.javacoding.xsearch.api.SearchQuery;
import net.javacoding.xsearch.config.Column;
import net.javacoding.xsearch.config.DatasetConfiguration;
import net.javacoding.xsearch.config.ServerConfiguration;
import net.javacoding.xsearch.core.exception.ConfigurationException;
import net.javacoding.xsearch.foundation.WebserverStatic;
import net.javacoding.xsearch.search.HTMLEntities;
import net.javacoding.xsearch.search.HitDocument;
import net.javacoding.xsearch.search.QueryTranslator;
import net.javacoding.xsearch.search.SearchQueryParser;
import net.javacoding.xsearch.search.analysis.AdvancedQueryAnalysis;
import net.javacoding.xsearch.search.analysis.QueryAnalysis;
import net.javacoding.xsearch.search.function.RandomQuery;
import net.javacoding.xsearch.search.function.TimeWeightQuery;
import net.javacoding.xsearch.search.memory.BufferIndexManager;
import net.javacoding.xsearch.search.query.DbsQuery;
import net.javacoding.xsearch.search.result.SearchResult;
import net.javacoding.xsearch.search.result.SearchSort;
import net.javacoding.xsearch.search.result.filter.Count;
import net.javacoding.xsearch.search.result.filter.FilterColumn;
import net.javacoding.xsearch.search.result.filter.FilterResult;
import net.javacoding.xsearch.search.searcher.IndexReaderSearcher;
import net.javacoding.xsearch.search.searcher.SearcherManager;
import net.javacoding.xsearch.search.searcher.SearcherProvider;
import net.javacoding.xsearch.search.searcher.collector.FilterablesHitCollector;
import net.javacoding.xsearch.status.QueryLogger;
import net.javacoding.xsearch.utility.FileUtil;
import net.javacoding.xsearch.utility.HttpUtil;
import net.javacoding.xsearch.utility.U;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.struts.action.Action;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fdt.common.util.SystemUtil;
import com.fdt.sdl.styledesigner.Template;
import com.fdt.sdl.styledesigner.util.DeviceDetectorUtil;
import com.fdt.sdl.styledesigner.util.TemplateUtil;

/**
 * Implementation of <strong>Action </strong> that performs search.
 */

public class SearchAction extends Action {
	private static Logger logger = LoggerFactory.getLogger(SearchAction.class);

	private void displayFilterResult(FilterResult filterResult, Result result) {
		List<FilterColumn> filterColumns = filterResult.getFilterColumns();

		for (FilterColumn filterColumn : filterColumns) {
			System.out.println("***************************************************");
			Column column = filterColumn.getColumn();
			System.out.println(column);
			List<Count> counts = filterColumn.getCounts();
			for (Count count : counts) {
				System.out.println("------------------------------------------------");
				System.out.println(count);
			}
		}

	}

	/**
	 * Process the search request.
	 */
	public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request,
			HttpServletResponse response) throws IOException, ServletException {

		if ("Y".equals(request.getParameter("displayCached"))) {
			return mapping.findForward("displayCached");
		}
		if ("1".equals(request.getParameter("rss"))) {
			response.addHeader("content-disposition", "inline; filename=\"search.xml\"");
		}

		SearchContext sc = null;
		SearchResult sr = new SearchResult();

		String q = request.getParameter("q");
		if (q == null) {
			q = "";
		}
		String lq = request.getParameter("lq");
		String defaultQ = request.getParameter("defaultQ");

		ActionMessages errors = new ActionMessages();
		try {
			long _start = System.currentTimeMillis();

			sc = findSearchContext(mapping, request);
			if (sc.af == null) {
				return mapping.findForward("continue");
			}

			if (sc.debug)
				logger.info("Got config: " + (System.currentTimeMillis() - _start));

			// Get searchers from the indexNames
			sc.irs = getIndexReaderSearcher(sc.indexNames);
			if (sc.irs == null) {
				if (sc.debug)
					logger.warn("Can not get searcher ");
				errors.add("error", new ActionMessage("action.search.index.error", sc.indexName));
				return (sc.af);
			}
			if (sc.debug)
				logger.info("Got searcher: " + (System.currentTimeMillis() - _start));

			FilterResult filterResult = new FilterResult();

			// parse the query
			Query query = null;
			if (!sc.dc.getIsEmptyQueryMatchAll() && U.isEmpty(q) && U.isEmpty(lq)) {
				query = null;
			} else {
				query = getSearchQuery(sr, q, lq, filterResult, request, sc.dc, sc.irs, getBooleanOperator(request),
						request.getParameter("searchable"), U.getInt(request.getParameter("randomQuerySeed"), 0),
						sc.debug);
			}

			// pagination
			int offset = U.getInt(request.getParameter("start"), 0);
			if (offset < 0)
				offset = 0;

			int topRows = U.getInt(request.getParameter("topRows"), 0);
			if (topRows < 0)
				topRows = 0;

			int defaultLength = sc.template != null && sc.template.defaultLength != null ? sc.template.defaultLength
					: 30;
			Cookie cLength = HttpUtil.getCookie(request, "resultspp");
			if (cLength != null) {
				defaultLength = U.getInt(cLength.getValue(), defaultLength);
			}
			int rowsToReturn = U.getInt(request.getParameter("length"),
					U.getInt(request.getParameter("limit"), defaultLength));
			if (rowsToReturn <= 0)
				rowsToReturn = 100;

			List<HitDocument> docs = null;
			List<HitDocument> defaultDocs = null;
			int[] total = new int[1];
			long searchTime = 0;
			List<SearchSort> sortBys = findSearchSorts(sc.dc, request);
			if (sc.debug)
				logger.info("Start Searching: " + (System.currentTimeMillis() - _start));
			File propertiesFile = FileUtil.resolveFile(WebserverStatic.getRootDirectoryFile(), "WEB-INF", "conf",
					"spring", "properties", "client.properties");
			String threeTier = null;
			String indexServerUrl = null;

			long start = System.currentTimeMillis();
			if (Boolean.parseBoolean(request.getServletContext().getInitParameter("isPSOOnlyMachine"))) {
				if (propertiesFile != null && propertiesFile.getAbsolutePath() != null) {
					indexServerUrl = SystemUtil.readProperty("indexServerUrl", propertiesFile.getAbsolutePath());
					SearchConnection searchConnection = new SearchConnection(indexServerUrl).setIndex(sc.indexName);
					SearchQuery searchQuery = new SearchQuery().setStart(offset).setLength(rowsToReturn);

					if (!StringUtils.isBlank(q)) {
						searchQuery.setBasicQuery(q);
					}
					if (!StringUtils.isBlank(lq)) {
						searchQuery.setAdvancedQuery(lq);
					}
					Result result = searchConnection.search(searchQuery);
					// Populate Filters
					narrowBySearch(query, sc.irs, sc.dc, filterResult, errors, request);
					populateFilterResult(filterResult, result);
					sr.initFor3Tier(sc, q, lq, query, result.getDocList(), defaultDocs, searchTime, result.getTotal(),
							offset, rowsToReturn, sortBys, filterResult, request, response);
					// displayFilterResult(filterResult, result);
				}

			} else {
				if (query != null) {
					Hits hits = null;
					TopDocs topDocs = null;
					if (sortBys == null) {
						if (topRows == 0) {
							hits = directSearch(query, sc.irs, sc.dc, hits, errors, request);
							searchTime = System.currentTimeMillis() - start;
							docs = collectHits(sc.dc, hits, rowsToReturn, offset, total);
						} else {
							topDocs = topDocsSearch(query, sc.irs, sc.dc, errors, request, topRows);
							searchTime = System.currentTimeMillis() - start;
							docs = collectHits(sc.dc, topDocs, sc.irs.getSearcher());
						}
					} else {
						hits = sortBySearch(query, sc.irs, sc.dc, sortBys, request, hits, errors);
						searchTime = System.currentTimeMillis() - start;
						docs = collectHits(sc.dc, hits, rowsToReturn, offset, total);
					}
					if (hits != null && hits.length() > 0) {
						narrowBySearch(query, sc.irs, sc.dc, filterResult, errors, request);
					} else {
						if (!U.isEmpty(defaultQ)) {
							Query defaultQuery = getSearchQuery(sr, defaultQ, lq, filterResult, request, sc.dc, sc.irs,
									getBooleanOperator(request), request.getParameter("searchable"),
									U.getInt(request.getParameter("randomQuerySeed"), 0), sc.debug);
							hits = directSearch(defaultQuery, sc.irs, sc.dc, hits, errors, request);
							searchTime = System.currentTimeMillis() - start;
							defaultDocs = collectHits(sc.dc, hits, rowsToReturn, offset, total);
						}
					}
				}
				sr.init(sc, q, lq, query, docs, defaultDocs, searchTime, total[0], offset, rowsToReturn, sortBys,
						filterResult, request, response);
			}
			if (sc.debug)
				logger.info("Got docs from disk: " + (System.currentTimeMillis() - _start));
			sr.setAttributes();
			request.setAttribute("searchResult", sr);

			// logger.debug("Set velocity variables: " +
			// (System.currentTimeMillis() - _start));

			if (sc.debug)
				logger.info("Found " + total[0] + " MATCHING with \"" + q + "\" in " + searchTime + " milliseconds");

			// searcher.close();

			log(q, lq, request, sc.indexName,
					(sc.actualTemplateName == null ? sc.templateName : sc.actualTemplateName),
					System.currentTimeMillis(), searchTime, System.currentTimeMillis() - _start, total[0]);

			return sc.af;
		} catch (TooManyClauses tooManyClauseExcep) {
			errors.add("error", new ActionMessage("action.search.runtime.error.toomanyclause"));
			logger.info("Error while using wildcard search:" + tooManyClauseExcep);
			return (mapping.findForward("error"));
		} catch (IOException ioe) {
			errors.add("error", new ActionMessage("action.showIndexStatus.index.error", sc.indexName));
			System.err.println("Search IOE:" + q);
			ioe.printStackTrace();
			return (mapping.findForward("error"));
		} catch (NullPointerException se) {
			errors.add("error", new ActionMessage("action.showIndexStatus.index.error", sc.indexName));
			System.err.println("Search NPE:" + q);
			se.printStackTrace();
			return (mapping.findForward("error"));
		} catch (Throwable t) {
			errors.add("error", new ActionMessage("action.showIndexStatus.index.error", sc.indexName));
			System.err.println("Search Error:" + q);
			t.printStackTrace();
			request.setAttribute("errors", t);
			return (mapping.findForward("error"));
		} finally {
			if (sc != null && sc.indexName != null && sc.irs != null) {
				SearchAction.closeIndexReaderSearcher(sc.irs);
			}
			request.setAttribute("layout", "Empty.vm");
			saveErrors(request, errors);
		}

	}

	private void populateFilterResult(FilterResult filterResult, Result result) {
		for (FacetChoice fChoice : result.getFacetChoiceList()) {
			FilterColumn filterColumn = filterResult.getFilterColumn(fChoice.getColumn());
			if (filterColumn != null) {
				Map<Object, Count> counts = new HashMap<Object, Count>();
				for (FacetCount fc : fChoice.getFacetCountList()) {
					Count count = new Count(fChoice.getColumn(), fc.getValue(), fc.getCount());
					counts.put(fc.getValue(), count);
				}
				filterColumn.setCounts(counts);
			}
		}

	}

	/**
	 * indexName: Optional. If empty, all indexes are searched. If using
	 * multiple indexes, the index names are separated by comma. <br/>
	 * templateName: Optional. If empty, use the default template. <br/>
	 * If it's in the format of "x|y" and together with multiple index,
	 * "indexName=a,b,c", search will use the template "y" defined in index "x". <br/>
	 * fileName: Optional. Default is main.stl. For backward compatibility, if
	 * main.stl doesn't exists and main.vm exists, main.vm will be used. It can
	 * also be main.jsp to support jsp rendering, or any file that's rendering
	 * searchResult object. For example, tags.stl or documents.stl, for a
	 * partial page rendering. This partial rendering would be great for AJAX. <br/>
	 */
	public static SearchContext findSearchContext(ActionMapping mapping, HttpServletRequest request) {
		SearchContext sc = new SearchContext();
		sc.debug = U.getBoolean(request.getParameter("debug"), "y", false);
		sc.indexName = request.getParameter("indexName");
		sc.templateName = request.getParameter("templateName");
		sc.actualFileName = "main.stl";

		if (!U.getBoolean(request.getParameter("renderer"), "stl", true)) {
			sc.actualFileName = "main." + request.getParameter("renderer");
		}

		sc.indexNames = getIndexNames(sc.indexName);
		String[] tnames = null;
		if (!U.isEmpty(sc.templateName)) {
			tnames = sc.templateName.split("/|\\||\\\\");
		}

		if (sc.indexNames == null && tnames != null) {
			sc.indexNames = new String[1];
			if (tnames.length == 3) {
				sc.indexNames[0] = sc.actualIndexName = tnames[0];
				sc.actualTemplateName = tnames[1];
				sc.actualFileName = tnames[2];
			} else if (tnames.length == 2) {
				sc.indexNames[0] = sc.actualIndexName = tnames[0];
				sc.actualTemplateName = tnames[1];
			} else {
				// wrong format of inputs
			}
		} else if (sc.indexNames == null && tnames == null) {
			// wrong format of inputs
		} else if (sc.indexNames != null && tnames == null) {
			sc.actualIndexName = sc.indexNames[0];
		} else {// if(indexNames!=null && tnames !=null){
			sc.actualIndexName = sc.indexNames[0];
			if (tnames.length == 1) {
				sc.actualTemplateName = tnames[0];
			} else if (tnames.length == 2) {
				if (tnames[1] != null && tnames[1].indexOf(".") > 0) {
					sc.actualTemplateName = tnames[0];
					sc.actualFileName = tnames[1];
				} else {
					sc.actualIndexName = tnames[0];
					sc.actualTemplateName = tnames[1];
				}
			} else if (tnames.length == 3) {
				sc.actualIndexName = tnames[0];
				sc.actualTemplateName = tnames[1];
				if (!U.isEmpty(tnames[2])) {
					sc.actualFileName = tnames[2];
				}
			}
		}
		if (sc.actualFileName.indexOf(".") < 0) {
			sc.actualFileName = sc.actualFileName + ".stl";
		}
		sc.actualFileName = U.getText(request.getParameter("fileName"), sc.actualFileName);

		sc.dc = ServerConfiguration.getDatasetConfiguration(sc.actualIndexName);

		if (sc.dc == null) {
			if (sc.debug)
				logger.info("Can not find this Index for rendering: " + sc.actualIndexName);
			return sc;
		} else {
			if (sc.debug)
				logger.info("search index: " + sc.indexName);
			if (sc.debug)
				logger.info("Using This Index to render: " + sc.actualIndexName);
		}

		// Forward control to the result rendering page
		String templateName = U.getText(sc.actualTemplateName, DeviceDetectorUtil.identifyDevice(sc.dc, request));
		// this "if" clause is only for backward compatibility
		if (!TemplateUtil.getTemplateFile(sc.dc.getName(), templateName, sc.actualFileName).exists()) {
			sc.actualFileName = "main.vm";
		}
		String m_templateFile = TemplateUtil.getTemplateFilePath(sc.dc.getName(), templateName, sc.actualFileName);
		try {
			sc.template = TemplateUtil.getTemplate(sc.dc.getName(), templateName);
			sc.templateName = sc.template.name;
		} catch (IOException e) {
		}

		request.setAttribute("templateName", templateName);
		if (sc.debug)
			logger.info("templateName:" + templateName + " templateFile:" + m_templateFile);
		sc.af = new ActionForward(m_templateFile);
		return sc;
	}

	protected static ArrayList<SearchSort> findSearchSorts(DatasetConfiguration dc, HttpServletRequest request) {
		ArrayList<SearchSort> ret = null;
		if ("1".equals(request.getParameter("rss"))) {
			Column c = dc.getWorkingQueueDataquery().getModifiedDateColumn();
			if (c == null)
				return null;
			request.setAttribute("modifiedDateColumn", c.getColumnName());
			ret = new ArrayList<SearchSort>();
			ret.add(new SearchSort(c));
			return ret;
		}

		String sortBy = request.getParameter("sortBy");
		if (U.isEmpty(sortBy))
			return null;

		ret = new ArrayList<SearchSort>();
		ArrayList<Column> al = dc.getColumns();
		String[] sorts = sortBy.split(",");
		for (int i = 0; i < sorts.length; i++) {
			if ("_relevance_".equalsIgnoreCase(sorts[i])) {
				ret.add(new SearchSort(null));
			} else {
				for (int j = 0; j < al.size(); j++) {
					if (al.get(j).getColumnName().equalsIgnoreCase(sorts[i])) {
						ret.add(new SearchSort(al.get(j)));
						break;
					}
				}
			}
		}

		String descendings = request.getParameter("desc");
		if (U.isEmpty(descendings))
			return ret;
		String[] descs = descendings.split(",");
		for (int i = 0; i < descs.length && i < ret.size(); i++) {
			ret.get(i).descending = U.getBoolean(descs[i], "Y", ret.get(i).descending);
		}

		return ret;
	}

	public static Query getSearchQuery(SearchResult sr, String q, String lq, FilterResult filterResult,
			HttpServletRequest request, DatasetConfiguration dc, IndexReaderSearcher irs, int booleanOperator,
			String dynamicSearchable, int randomQuerySeed, boolean debug) {
		// parse the query
		Query advancedQuery = null;
		Query query = null;
		if (filterResult == null) {
			filterResult = new FilterResult();
		}
		try {
			if ("Y".equalsIgnoreCase(request.getParameter("lucene"))) {
				// option to switch to Lucene's RAW query parser
				query = SearchQueryParser.luceneParse(dc.getAnalyzer(), q);
			} else {
				if (U.isEmpty(q) && dc.getIsEmptyQueryMatchAll() && U.isEmpty(lq)) {
					advancedQuery = new MatchAllDocsQuery();
				}
				// query = SearchQueryParser.parse(dc, analyzer, q,
				// filteredColumns);
				// copy SearchQueryParser.parse to here
				if (!U.isEmpty(q)) {
					AdvancedQueryAnalysis aqa = new AdvancedQueryAnalysis(dc, q, filterResult);
					q = aqa.getRemainingQueryString();
					advancedQuery = aqa.getAdvancedQuery();
					if (debug)
						logger.info("Advanced query: " + advancedQuery);
					if (aqa.getIsAllNegative() && advancedQuery != null) {
						advancedQuery = AdvancedQueryAnalysis.appendQuery(advancedQuery, new MatchAllDocsQuery(),
								Occur.MUST);
					}
				}
				if (!U.isEmpty(q)) {
					QueryTranslator translator = new QueryTranslator(dc.getColumns(), dynamicSearchable);
					if (debug)
						logger.info("Start parse query: " + HTMLEntities.encode(q));
					DbsQuery myQuery = QueryAnalysis.parseQuery(q, dc);
					request.setAttribute("parsedQuery", myQuery);
					if (sr != null) {
						sr.setUserInput(myQuery.getUserInput());
					}
					if (debug)
						logger.info("parsed query: " + HTMLEntities.encode(myQuery.toString()));
					translator.setSlop(5);
					translator.setBooleanOperator(booleanOperator);
					query = translator.translate(dc.getAnalyzer(), myQuery, filterResult, dc);
					if (debug)
						logger.info("translated query: " + HTMLEntities.encode(query.toString()));
					if (translator.getIsAllNegative()) {
						query = AdvancedQueryAnalysis.appendQuery(query, new MatchAllDocsQuery(), Occur.MUST);
					}
				}
				query = AdvancedQueryAnalysis.andQuery(query, advancedQuery);
				if (!U.isEmpty(lq)) {
					Query luceneQuery = SearchQueryParser.luceneParse(dc.getAnalyzer(), lq);
					query = AdvancedQueryAnalysis.appendQuery(query, luceneQuery);
					if (debug)
						logger.info("added Lucene query: " + HTMLEntities.encode(query.toString()));
				}
			}
			if (dc.getDateWeightColumnName() != null) {
				query = new TimeWeightQuery(query, dc, irs.getIndexReader());
				if (debug)
					logger.info("added time-weighted query: " + HTMLEntities.encode(query.toString()));
			}
			if (randomQuerySeed != 0) {
				query = new RandomQuery(query, randomQuerySeed);
				if (debug)
					logger.info("added random query: " + HTMLEntities.encode(query.toString()));
			}
		} catch (Exception ex) {
			if (debug)
				logger.info("Exception Occurred", ex);
			if (debug)
				logger.error("Cannot parse query: " + q);
		}
		// logger.debug("Parsed Query: " + (System.currentTimeMillis() -
		// _start));
		return query;
	}

	public static Query getSearchQuery(String q, String lq, FilterResult filterResult, HttpServletRequest request,
			DatasetConfiguration dc, IndexReaderSearcher irs, int booleanOperator, String dynamicSearchable,
			int randomQuerySeed, boolean debug) {
		// this is used for cases you don't have a SearchResult available
		return getSearchQuery(null, q, lq, filterResult, request, dc, irs, booleanOperator, dynamicSearchable,
				randomQuerySeed, debug);
	}

	protected static void narrowBySearch(Query query, IndexReaderSearcher irs, DatasetConfiguration dc,
			FilterResult filterResult, ActionMessages errors, HttpServletRequest request) throws IOException {
		try {
			filterResult.setFilterColumns(dc.getFilterableColumns());

			FilterablesHitCollector fhc = new FilterablesHitCollector(irs, filterResult);
			irs.getSearcher().search(getAccessFilterQuery(dc, query, request, errors), fhc);

			filterResult.finish();

			ArrayList<ArrayList<Count>> columnCounts = filterResult.getColumnCounts();

			if (filterResult.filterColumns != null && filterResult.filterColumns.size() > 0) {
				request.setAttribute("filterColumnCounts", columnCounts);
				request.setAttribute("filterIndexName", dc.getName());
				request.setAttribute("previousQuery", query);
			}
			// narrowBy search
		} catch (RuntimeException re) {
			re.printStackTrace();
			if (errors.size() <= 0) {
				errors.add("error", new ActionMessage("action.search.runtime.error", re.getMessage()));
			}
			logger.info("Error when narrowBySearch:" + query, re);
		}
	}

	protected static Hits directSearch(Query query, IndexReaderSearcher irs, DatasetConfiguration dc, Hits hits,
			ActionMessages errors, HttpServletRequest request) throws IOException {
		try {
			// search when no sortBy search
			// for debugging:
			// dc.setDateWeightColumnName(dc.getModifiedDateColumn().getColumnName());
			return irs.getSearcher().search(getAccessFilterQuery(dc, query, request, errors));
		} catch (TooManyClauses tooManyClauseExcep) {
			logger.info("Error while using wildcard search:" + query);
			throw tooManyClauseExcep;
		} catch (RuntimeException re) {
			re.printStackTrace();
			if (errors.size() <= 0) {
				errors.add("error", new ActionMessage("action.search.runtime.error", re.getMessage()));
			}
			logger.info("Error when directSearch:" + query, re);
		}
		return null;
	}

	protected static TopDocs topDocsSearch(Query query, IndexReaderSearcher irs, DatasetConfiguration dc,
			ActionMessages errors, HttpServletRequest request, int topNDocs) throws IOException {
		try {
			// search when no sortBy search
			// for debugging:
			// dc.setDateWeightColumnName(dc.getModifiedDateColumn().getColumnName());
			return irs.getSearcher().search(getAccessFilterQuery(dc, query, request, errors), topNDocs);
		} catch (TooManyClauses tooManyClauseExcep) {
			logger.info("Error while using wildcard search:" + query);
			throw tooManyClauseExcep;
		} catch (RuntimeException re) {
			re.printStackTrace();
			if (errors.size() <= 0) {
				errors.add("error", new ActionMessage("action.search.runtime.error", re.getMessage()));
			}
			logger.info("Error when directSearch:" + query, re);
		}
		return null;
	}

	protected static Hits sortBySearch(Query query, IndexReaderSearcher irs, DatasetConfiguration dc,
			List<SearchSort> sortBys, HttpServletRequest request, Hits hits, ActionMessages errors) throws IOException {
		try {
			return irs.getSearcher().search(getAccessFilterQuery(dc, query, request, errors),
					SearchSort.getLuceneSort(sortBys));
		} catch (RuntimeException re) {
			System.err.println("Error when querying:" + query);
			re.printStackTrace();
			if (errors.size() <= 0) {
				errors.add("error", new ActionMessage("action.search.runtime.error", re.getMessage()));
			}
			logger.info("Error when sortBySearch:" + query, re);
		}
		return null;
	}

	protected static List<HitDocument> collectHits(DatasetConfiguration dc, Hits hits, int rowsToReturn, int offset,
			int[] total) throws IOException {
		List<HitDocument> retValue = null;
		if (hits != null) {
			total[0] = hits.length();
			retValue = new ArrayList<HitDocument>(rowsToReturn);
			for (int i = offset; (i < offset + rowsToReturn) && (i < hits.length()); i++) {
				retValue.add(new HitDocument(dc, hits.doc(i), hits.score(i), hits.id(i)));
			}
		}
		return retValue;
	}

	protected static List<HitDocument> collectHits(DatasetConfiguration paramDatasetConfiguration,
			TopDocs paramTopDocs, Searcher paramSearcher) throws CorruptIndexException, IOException {
		ArrayList localArrayList = null;
		if (paramTopDocs != null) {
			localArrayList = new ArrayList(paramTopDocs.scoreDocs.length);
			for (int i = 0; i < paramTopDocs.scoreDocs.length; i++) {
				Document doc = paramSearcher.doc(paramTopDocs.scoreDocs[i].doc);
				localArrayList.add(new HitDocument(paramDatasetConfiguration, doc, paramTopDocs.scoreDocs[i].score,
						paramTopDocs.scoreDocs[i].doc));
			}
		}
		return localArrayList;
	}

	protected static Query getAccessFilterQuery(DatasetConfiguration dc, Query query, HttpServletRequest request,
			ActionMessages errors) {
		if (dc.getIsSecure()) {
			String securityColumnName = (String) dc.getSecureColumnName();
			try {
				String securityValue = (String) request.getUserPrincipal().getName();
				TermQuery secQuery = new TermQuery(new Term(securityColumnName, securityValue));
				return AdvancedQueryAnalysis.appendQuery(query, secQuery);
			} catch (Throwable e) {
				e.printStackTrace();
				if (errors.size() <= 0) {
					errors.add("error", new ActionMessage("action.search.security.error"));
				}
				return null;
			}
		}
		return query;
	}

	/*
	 * If indexName=... is null, do nothing If indexName is _ALL_, process for
	 * all indexes If indexName has comma inside, split the indexNames and
	 * process them If not anything above, process this single indexName
	 */
	public static String[] getIndexNames(String indexName) {
		if (U.isEmpty(indexName))
			return null;
		if ("_ALL_".equalsIgnoreCase(indexName)) {
			try {
				ArrayList<DatasetConfiguration> dcs = ServerConfiguration.getDatasetConfigurations(false);
				String[] indexNames = new String[dcs.size()];
				for (int i = 0; i < indexNames.length; i++) {
					indexNames[i] = dcs.get(i).getName();
				}
				return indexNames;
			} catch (ConfigurationException e) {
				e.printStackTrace();
			}
			return null;
		}
		if (indexName.indexOf(",") > 0) {
			return indexName.split(",");
		}
		return new String[] { indexName };
	}

	public static IndexReaderSearcher getIndexReaderSearcher(String[] indexNames) throws Exception {
		if (indexNames == null)
			return null;
		ArrayList<IndexReaderSearcher> irss = new ArrayList<IndexReaderSearcher>();
		for (String indexName : indexNames) {
			SearcherProvider sp = SearcherManager.getSearcherProvider(indexName);
			if (sp != null) {
				IndexReaderSearcher irs = sp.getIndexReaderSearcher();
				if (irs != null) {
					irss.add(irs);
				}
			}
		}
		IndexReaderSearcher irs = IndexReaderSearcher.getIndexReaderSearcher(irss,
				BufferIndexManager.getIndex(indexNames[0], false));
		return irs;
	}

	public static void closeIndexReaderSearcher(IndexReaderSearcher irs) {
		try {
			irs.release();
		} catch (Throwable e) {
			logger.warn("Exception Occurred", e);
			e.printStackTrace();
		}
	}

	public static class SearchContext {
		public boolean debug;

		public IndexReaderSearcher irs;
		/*
		 * This is actually only used for displaying purpose
		 */
		public DatasetConfiguration dc;
		public Template template;
		public ActionForward af;

		// this one holds the actual template name that'll be used
		public String actualTemplateName;
		public String actualIndexName;
		public String actualFileName;

		/*
		 * derived from "indexName" parameter, this is acutally been used to get
		 * the list of index searchers
		 */
		public String[] indexNames;
		/*
		 * same as "indexName" parameter, which may have several index names.
		 * It's only used for displaying messages, and to pass to the result
		 * rendering
		 */
		public String indexName;
		/*
		 * same as "templateName" parameter. It's only used to pass to the
		 * result rendering
		 */
		public String templateName;
	}

	public static void log(String q, String lq, HttpServletRequest request, String indexName, String templateName,
			long visitTime, long searchingTime, long renderTime, int returnedDoc) {
		String ipaddress = request.getRemoteAddr();
		if (request.getHeader("HTTP_X_FORWARDED_FOR") != null) {
			ipaddress = request.getHeader("HTTP_X_FORWARDED_FOR");
		} else if (request.getHeader("X-FORWARDED-FOR") != null) {
			ipaddress = request.getHeader("X-FORWARDED-FOR");
		}
		if (!U.isEmpty(q)) {
			QueryLogger.log(request.getRemoteUser(), ipaddress, HTMLEntities.encode(q), indexName, templateName,
					visitTime, searchingTime, renderTime, returnedDoc);
		} else if (!U.isEmpty(lq)) {
			QueryLogger.log(request.getRemoteUser(), ipaddress, HTMLEntities.encode(lq), indexName, templateName,
					visitTime, searchingTime, renderTime, returnedDoc);
		}
	}

	public static int getBooleanOperator(HttpServletRequest request) {
		int booleanOperator = 0;
		String operator = request.getParameter("booleanOperator");
		if (!U.isEmpty(operator)) {
			if ("and".equalsIgnoreCase(operator)) {
				booleanOperator = QueryTranslator.AND;
			} else {
				booleanOperator = QueryTranslator.OR;
			}
		}
		return booleanOperator;
	}
}
