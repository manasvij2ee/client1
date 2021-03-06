package net.javacoding.xsearch;

import static com.fdt.common.SystemConstants.NOTIFY_ADMIN;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import net.javacoding.xsearch.config.DatasetConfiguration;
import net.javacoding.xsearch.config.ServerConfiguration;
import net.javacoding.xsearch.core.AffectedDirectoryGroup;
import net.javacoding.xsearch.core.BeforeAfterOperation;
import net.javacoding.xsearch.core.IndexerContext;
import net.javacoding.xsearch.core.IndexerContextFactory;
import net.javacoding.xsearch.core.exception.ConfigurationException;
import net.javacoding.xsearch.foundation.WebserverStatic;
import net.javacoding.xsearch.search.SpellCheckManager;
import net.javacoding.xsearch.search.searcher.SearcherManager;
import net.javacoding.xsearch.search.synonym.SynonymManager;
import net.javacoding.xsearch.status.IndexStatus;
import net.javacoding.xsearch.utility.FileUtil;
import net.javacoding.xsearch.utility.HttpUtil;
import net.javacoding.xsearch.utility.LogUtil;
import net.javacoding.xsearch.utility.SchedulerTool;
import net.javacoding.xsearch.utility.ScriptingUtil;
import net.javacoding.xsearch.utility.U;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fdt.sdl.styledesigner.util.PageStyleUtil;

/**
 * <code>IndexManager</code> class based on configuration retrieve the documents from data sources and save the content to ContentWriter
 */
public class IndexManager {
	
    private static Logger logger   = LoggerFactory.getLogger(IndexManager.class.getName());

    private DatasetConfiguration dc       = null;

    private String[]             commands = null;
    
    boolean needToRefresh                 = false;

    private IndexerContext ic;

    private void init(String config_file, String indexName, String[] commands) throws IOException, ConfigurationException {
        // logger.debug("index name :"+indexName);
        // logger.debug("command :"+command);
        logger.info("Index Name:" + indexName);
        Properties p = loadProductProperties();
        logger.debug("init...");
        if(config_file!=null) {
            if(config_file.startsWith("\"")&&config_file.endsWith("\"")) {
                config_file = config_file.substring(1,config_file.length()-1);
            }
        }
        logger.debug("config file:"+config_file);
        ServerConfiguration.setServerConfigFile(config_file);
        this.dc = ServerConfiguration.getDatasetConfiguration(indexName);
        try {
            ic = IndexerContextFactory.createContext(dc);
        } catch (Exception e) {
            logger.error(NOTIFY_ADMIN, "!! Failed to load index configuration:" + indexName +" ++++++++++++");
        }
        this.commands = commands;
        IndexStatus.clearError(dc.getName());
        logger.debug("start...");
        LogUtil.setLog(dc);
    }
    public static Properties loadProductProperties() {
        Properties properties = new Properties();
        try {
            InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream ("net/javacoding/xsearch/product.properties");
            if(in!=null) {
                properties.load(in);
            }
        } catch (IOException e) {
        }
        return properties;
    }
    public void setIndexerContext(IndexerContext ic) {
        this.ic = ic;
    }
    public void setDatasetConfiguration(DatasetConfiguration dc) {
        this.dc = dc;
    }
    public void setCommands(String[] commands) {
        this.commands = commands;
    }

    public static void main(String args[]) {
    	long startTime = System.currentTimeMillis();
        IndexManager im = new IndexManager();
        try {
            Thread.currentThread().setPriority((Thread.MIN_PRIORITY));// +2));//min
            // is 1,
            // norm
            // is 5,
            // //+Thread.NORM_PRIORITY)/2);
            im.parseArgs(args);
            im.start();
        } catch (Exception e) {
            System.err.println(e);
            usage();
        } finally {
            im.finish(startTime);
        }
    }

    /*
     * Parse command line arguments Code inspired by http://www.ecs.umass.edu/ece/wireless/people/emmanuel/java/java/cmdLineArgs/parsing.html
     */
    private void parseArgs(String[] args) {
        String config = null;
        String index = null;
        String[] commands = null;
        String arg = null;
        int i = 0;
        while (i < args.length && args[i].startsWith("-")) {
            arg = args[i++];
            if (i == args.length) usage();
            if (arg.equals("-config")) {
                config = args[i++];
            } else if (arg.equals("-index")) {
                index = args[i++];
            }
        }
        if (i == args.length)
            usage();
        else {
            commands = new String[args.length - i];
            System.arraycopy(args, i, commands, 0, args.length - i);
        }

        try {
            init(config, index, commands);
        } catch (IOException ioe) {
            logger.error(NOTIFY_ADMIN, "Failed to access log file:" + ioe);
            usage();
        } catch (ConfigurationException ce) {
            logger.error(NOTIFY_ADMIN, "Failed to access the configuration or index you specified:" + ce);
            usage();
        }
    }

    public static void usage() {
        System.out.println("Usage: java -config configFile -index indexName [incrementalIndexing|fullIndexing|reCreateIndex|mergeIndexes|clean|optimize|reIndexing|stopIndexing|prune]");
        System.exit(1);
    }

    public void finish(long startTime) {
        if(ic != null) {
            ic.stopAll();
        }
        logger.info("The Total Time Taken is :" + PageStyleUtil.convertMilliSecondsIntoHHmmSSsss(System.currentTimeMillis() - startTime));
        LogUtil.unsetLog(dc);
        logger.debug("finished.");//note: can not delete this. This line would trigger the email to be sent.
    }
    
    public void start() {
        boolean running = true;
        int i = 0;
        // stopIndexing should be the first commands
        if (commands.length>=1 && "stopIndexing".equals(commands[0])) {
            SchedulerTool.stop(dc);
            i++;
        }else if (commands.length>=2 && "stopIndexing".equals(commands[1])) {
            SchedulerTool.stop(dc);
            i+=2;
        }

        SchedulerTool.start(dc);
        for (; running && i < commands.length; i++) {
            String command = commands[i];
            logger.info("The Indexing Action on Index :" + ic + ": is ==>" + command);
            try {
                if ("inMemory".equals(command)) {
                    //skip it, since it's only matter to scheduler
                } else if ("incrementalIndexing".equals(command) || "incrementalIndexingWithFastDeletion".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Start indexing with fast deletion")) {
                        DBIndexer cr = new DBIndexer(ic);
                        cr.setNeedDeletion(true);
                        cr.start();
                        SchedulerTool.start(dc);// for following tasks
                        updateIndex(dc, ic.getAffectedDirectoryGroup());
                    }
                } else if ("incrementalIndexingWithThoroughDeletion".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Start indexing with thorough deletion")) {
                        DBIndexer cr = new DBIndexer(ic);
                        cr.setNeedDeletion(true);
                        cr.setIsThoroughDelete(true);
                        cr.start();
                        SchedulerTool.start(dc);// for following tasks
                        updateIndex(dc, ic.getAffectedDirectoryGroup());
                    }
                } else if ("incrementalIndexingWithoutDeletion".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Start indexing without deletion")) {
                        DBIndexer cr = new DBIndexer(ic);
                        cr.start();
                        SchedulerTool.start(dc);// for following tasks
                        updateIndex(dc, ic.getAffectedDirectoryGroup());
                    }
                } else if ("fullIndexing".equals(command)) {//deprecated!!!
                    if (SchedulerTool.setStatus(ic, "Full indexing")) {
                        DBIndexer cr = new DBIndexer(ic);
                        cr.setFullIndexing(true);
                        cr.start();
                        SchedulerTool.start(dc);// for following tasks
                        updateIndex(dc, ic.getAffectedDirectoryGroup());
                    }
                } else if ("reCreateIndex".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Re-Creating Index")) {
                        DBIndexer cr = new DBIndexer(ic);
                        cr.setIsRecreate(true);
                        cr.setFullIndexing(true);
                        cr.start();
                        if(!cr.ic.isDataComplete)break;
                        SchedulerTool.start(dc);// for following tasks
                        updateIndex(dc, ic.getAffectedDirectoryGroup());
                    }
                } else if ("stopIndexing".equals(command)) {
                    SchedulerTool.stop(ic);
                } else if ("reIndexing".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Re-indexing")) {
                        AffectedDirectoryGroup adg = ReIndexing.start(dc);
                        if(adg!=null){
                            updateIndex(dc, adg);
                            this.ic.setAffectedDirectoryGroup(adg);
                        }
                    }
                } else if ("createPeriodTable".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Creating period table")) {
                        IndexStatus.createPeriodTableIfNeeded(dc);
                    }
                } else if ("deleteDuplicates".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Deleting duplicates")) {
                        AffectedDirectoryGroup adg = DeleteDuplicates.start(dc);
                        if(adg!=null){
                            updateIndex(dc, adg);
                            this.ic.setAffectedDirectoryGroup(adg);
                        }
                    }
                } else if ("mergeIndexes".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Merging indexes")) {
                        IndexMerger im = new IndexMerger(ic);
                        AffectedDirectoryGroup adg = im.mergeIndexes();
                        if(adg!=null){
                            updateIndex(dc, adg);
                            this.ic.setAffectedDirectoryGroup(adg);
                        }
                    }
                } else if ("mergeIndexesIfNeeded".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Merging indexing(if needed)")) {
                        IndexMerger im = new IndexMerger(ic);
                    	logger.info("Merging hours is " + dc.getMergeHours().isOK());
                    	logger.info("The Percentage to merge is " + im.needToMergeSubDirectories());
                        if (dc.getMergeHours().isOK()&&im.needToMergeSubDirectories()) {
                        	logger.info("Merging Indexes----");
                            AffectedDirectoryGroup adg = im.mergeIndexes();
                            if(adg!=null){
                                updateIndex(dc, adg);
                                this.ic.setAffectedDirectoryGroup(adg);
                            }
                        }
                    }
                } else if ("delete".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Removing index")) {
                        notifyServer("shutdownIndex",dc);
                        SearcherManager.destroy(dc.getName());
                        IndexPruner.emptyIndexData(dc);
                    }
                } else if ("unlockStoppedIndex".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Unlocking stopped index")) {
                        IndexStatus.unlockStoppedIndex(dc);
                    }
                } else if ("refreshIndex".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "refreshing index")) {
                        if (!notifyServer("refreshIndex",dc)) {
                            logger.debug("Failed to notify server to refresh searcher manager!");
                        }
                    }
                } else if ("buildDictionaryIfNeeded".equals(command)) {
                    //build the default dictionary
                    if (SchedulerTool.setStatus(ic, "building spell check dictionary")) {
                        SpellCheckManager.maybeBuildIndex(dc);
                        SpellCheckManager.buildDefaultIndex();
                    }
                } else if ("reBuildDictionary".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "Re-building Spell Check dictionary")) {
                        boolean ret = notifyServer("stopSpellCheckIndex",dc);
                        if(!ret) {
                            logger.error("Failed To Contact "+WebserverStatic.getLocalURL()+" to stop spell check index ");
                        }
                        SpellCheckManager.reBuildIndex(dc);
                        if(ret) {
                            notifyServer("startSpellCheckIndex",dc);
                        }
                    }
                } else if ("maybeBuildSynonyms".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "building synonyms")) {
                        SynonymManager.start(dc);
                    }
                } else if ("ping-a-url".equals(command)) {
                	if (!U.isEmpty(dc.getUrlToPing())){
                		try{
                			HttpUtil.send(dc.getUrlToPing(), true);
                		}catch(Exception e){
                			logger.error("Exception", e);
                		}
                	}
                } else if ("retrieveSubscription".equals(command)) {
                    if (SchedulerTool.setStatus(ic, "retrieving subscribed index")) {
                        IndexSubscriber is = new IndexSubscriber(ic);
                        AffectedDirectoryGroup adg = new AffectedDirectoryGroup();
                        is.retrieveMainTempIndex(IndexSubscriber.SUBSCRIBED_MAIN_DIRECTORY, IndexSubscriber.SUBSCRIBED_TEMP_DIRECTORY, new BeforeAfterOperation(){
                            public void after() {
                                logger.info("updated subscribed spelling check index");
                                SpellCheckManager.startSpellCheker(dc);
                            }
                            public void before() {
                                SpellCheckManager.stopSpellChecker(dc);
                            }});
                        is.retrieveIndex(IndexSubscriber.SUBSCRIBED_DICTIONARY_DIRECTORY, new BeforeAfterOperation(){
                            public void after() {
                                logger.info("updated subscribed spelling check index");
                                SpellCheckManager.startSpellCheker(dc);
                            }
                            public void before() {
                                SpellCheckManager.stopSpellChecker(dc);
                            }});
                        is.retrieveIndex(IndexSubscriber.SUBSCRIBED_PHRASE_DIRECTORY, new BeforeAfterOperation(){
                            public void after() {
                                logger.info("updated subscribed phrase suggest index");
                                SpellCheckManager.startPhraseSuggester(dc);
                            }
                            public void before() {
                                SpellCheckManager.stopPhraseSuggester(dc);
                            }});
                    }
                } else if ("debug".equals(command)) {
                    try {
                        Thread.sleep(5000);
                    }catch(Exception e) {}
                } else if (command!=null && command.trim().endsWith(".rb")){
                    ScriptingUtil s = new ScriptingUtil();
                    s.set("dc", dc);
                    s.setBaseDirectoryFile(new File("scripts"));
                    s.evalFile(command.trim());
                } else {
                    if (!U.isEmpty(command)) {
                        logger.error(NOTIFY_ADMIN, "unknown command:" + command);
                        usage();
                    }
                }
            } catch (Throwable e) {
                running = false;
                IndexStatus.setError(dc.getName(),e.toString());
                logger.error("When " + command + ":" + e);
                logger.error(U.getStatckTrace(e));
            }
        }
        SchedulerTool.stop(ic);
    }
    
    public static boolean notifyServer(String action, DatasetConfiguration dc, String... name_value_pair) {
        String url = WebserverStatic.getLocalURL() + action+".do?indexName=" + dc.getName();
        boolean isRefreshed = false;
        logger.info("Start Action " + action + "with URL ==>" + url);
        if(name_value_pair!=null) {
            StringBuilder sb = new StringBuilder(url);
            for(int i=0;i<name_value_pair.length-1; i+=2) {
                sb.append("&").append(name_value_pair[i]).append("=").append(name_value_pair[i+1]);
            }
            url = sb.toString();
        }
        isRefreshed = HttpUtil.send(url, true); 
        logger.info("End Action " + action + "with URL ==>" + url);
        return isRefreshed;
    }
    
    public static void updateIndex(DatasetConfiguration dc, AffectedDirectoryGroup adg) throws IOException{
        if(adg!=null){
            adg.setFinalReadyStatus();
            notifyServer("refreshIndex",dc);
            for(File dir : adg.getOldDirectories()){
                FileUtil.deleteAll(dir);
            }
        }
    }
}
