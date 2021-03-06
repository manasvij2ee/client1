package net.javacoding.xsearch.core.impl;

import net.javacoding.xsearch.config.DatasetConfiguration;
import net.javacoding.xsearch.connection.ConnectionProvider;
import net.javacoding.xsearch.connection.ConnectionProviderFactory;
import net.javacoding.xsearch.core.AffectedDirectoryGroup;
import net.javacoding.xsearch.core.DirectorySizeChecker;
import net.javacoding.xsearch.core.IndexerContext;
import net.javacoding.xsearch.core.PeriodTable;
import net.javacoding.xsearch.core.exception.DataSourceException;
import net.javacoding.xsearch.core.task.Scheduler;
import net.javacoding.xsearch.core.task.SchedulerFactory;
import net.javacoding.xsearch.core.task.dispatch.FetcherPoolDispatchTask;
import net.javacoding.xsearch.core.task.dispatch.WriterPoolDispatchTask;
import net.javacoding.xsearch.core.threading.WorkerThreadPool;
import net.javacoding.xsearch.indexer.IndexWriterProvider;
import net.javacoding.xsearch.indexer.IndexWriterProviderFactory;
import net.javacoding.xsearch.status.IndexStatus;
import net.javacoding.xsearch.utility.SchedulerTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * 
 */
public class IndexerContextImpl extends IndexerContext {
    protected Logger               logger               = LoggerFactory.getLogger(this.getClass().getName());

    protected WorkerThreadPool     fetcherPool;
    protected WorkerThreadPool     writerPool;
    protected ConnectionProvider   cp;
    protected IndexWriterProvider  iwp;
    protected Scheduler            scheduler;
    protected FetcherPoolDispatchTask fetcherPoolDispatchTask;
    protected WriterPoolDispatchTask  writerPoolDispatchTask;
    protected PeriodTable          periodTable;

    protected volatile boolean     isStopping           = false;
    private DirectorySizeChecker   directorySizeChecker = null;

    public IndexerContextImpl(DatasetConfiguration dc) throws java.io.IOException, DataSourceException {
    	super(dc);
        if (this.dc == null) {
            throw new DataSourceException();
        }
        directorySizeChecker = new DirectorySizeChecker(dc);
    }
    
    public void initConnections() throws java.io.IOException, DataSourceException{
        if(dc.getDataSourceType()==DatasetConfiguration.DATASOURCE_TYPE_DATABASE) {
            this.cp = ConnectionProviderFactory.newConnectionProvider(this);
        }
    }

    public void initAll(AffectedDirectoryGroup adg) throws java.io.IOException, DataSourceException{
        this.affectedDirectoryGroup = adg;
        this.periodTable = IndexStatus.createPeriodTableIfNeeded(dc);
        logger.info("Existing Period Table: " + this.periodTable);
        if(this.periodTable==null){
            this.periodTable = new PeriodTable();
        }
        this.scheduler = new SchedulerFactory().createScheduler(this);

        if(dc.getDataSourceType()==DatasetConfiguration.DATASOURCE_TYPE_DATABASE) {
            logger.info("Starting " + dc.getFetcherThreadsCount() + " fetcher workers...");
            this.fetcherPool = new WorkerThreadPool("Fetchers", "Fetcher", dc.getFetcherThreadsCount());
        }else {
            logger.info("Starting " + 1 + " fetcher worker...");
            this.fetcherPool = new WorkerThreadPool("Fetchers", "Fetcher", 1);
        }
        this.iwp = IndexWriterProviderFactory.newIndexWriterProvider(this);

        logger.info("Starting " + dc.getWriterThreadsCount() + " writer workers...");
        this.writerPool = new WorkerThreadPool("Writers", "Writer", dc.getWriterThreadsCount());
        this.fetcherPoolDispatchTask = new FetcherPoolDispatchTask(this.fetcherPool, this.scheduler);
        this.writerPoolDispatchTask = new WriterPoolDispatchTask(this.writerPool, this.scheduler);

    }

    public void stopRetrieving() {
        setStopping();
        if(this.periodTable!=null&&this.getAffectedDirectoryGroup()!=null){
            this.periodTable.merge();
            this.periodTable.save(IndexStatus.getPeriodTableStoreFile(this.getAffectedDirectoryGroup().getNewDirectory()));
            logger.info("Period Table: " + this.periodTable);
        }
        // release resources
        try {
            if(this.cp!=null) {
                logger.info("Releasing connection pool...");
                this.cp.close();
                this.cp = null;
            }
        } catch (DataSourceException dse) {
            logger.warn("Error releasing connection pool:", dse);
        }
        if(this.fetcherPool!=null) {
            logger.info("Stopping fetchers...");
            this.fetcherPool.stopAll();
            this.fetcherPool = null;
        }
        if(this.writerPool!=null) {
            logger.info("Stopping writers...");
            this.writerPool.stopAll();
            this.writerPool = null;
        }
        if(this.scheduler!=null) {
            logger.info("Shutting down schedulers...");
            this.scheduler.shutdown();
            this.scheduler = null;
        }
    }

    public void stopAll() {
        stopRetrieving();
        try {
            if(this.iwp!=null) {
                logger.info("Releasing index writer pool...");
                this.iwp.close();
                this.iwp = null;
            }
        } catch (DataSourceException dse) {
            logger.warn("Error releasing index writer pool:", dse);
        }

        logger.info("Indexing Context closed!");
    }


    public ConnectionProvider getConnectionProvider() {
        return this.cp;
    }

    public IndexWriterProvider getIndexWriterProvider() {
        return this.iwp;
    }

    public Scheduler getScheduler() {
        return this.scheduler;
    }

    public WorkerThreadPool getFetcherPool() {
        return this.fetcherPool;
    }

    public WorkerThreadPool getWriterPool() {
        return this.writerPool;
    }

    public FetcherPoolDispatchTask getFetcherPoolDispatchTask() {
        return this.fetcherPoolDispatchTask;
    }

    public WriterPoolDispatchTask getWriterPoolDispatchTask() {
        return this.writerPoolDispatchTask;
    }

    public PeriodTable getPeriodTable() {
        if(this.periodTable==null) {
            this.periodTable = new PeriodTable();
        }
        return this.periodTable;
    }

    public boolean isStopping() {
        if (isStopping) return isStopping;
        isStopping = !SchedulerTool.isRunning(this);
        if (isStopping) {
            logger.warn("Running flag file not found. Process terminating...");
        }
        /** commented as a part of license check **/
        /*if (directorySizeChecker.isDirectorySizeOverLimit()) {
            isStopping = true;
        }*/
        if (isStopping) {
            setStopping();
        }
        return isStopping;
    }

    public void setStopping() {
        isStopping = true;
        SchedulerTool.stop(this.dc);
        // notifyAllThreads();
    }

    private boolean _isRecreate = false;
    public void setIsRecreate(boolean b) {
        directorySizeChecker.setIsRecreate(b);
        _isRecreate = b;
    }
    public boolean getIsRecreate() {
        return _isRecreate;
    }

    private boolean _isIncrementalSql = false;
    public boolean getIsIncrementalSql() {
        return _isIncrementalSql;
    }
    public void setIsIncrementalSql(boolean b) {
        _isIncrementalSql = b;
    }

    public String toString(){
        return this.dc.getName();
    }

}
