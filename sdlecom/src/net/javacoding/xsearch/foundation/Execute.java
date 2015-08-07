package net.javacoding.xsearch.foundation;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.tools.ant.types.CommandlineJava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Execute {
	
	private final Logger logger = LoggerFactory.getLogger(Execute.class);

    private String[]  command     = null;
    
    String  environment = null;
    
    File workingDirectory = null;

    public Execute(String[] cmd, String env, File workingDirectory) {
        this.command = cmd;
        this.environment = env;
        this.workingDirectory = workingDirectory;
    }

    public void start() {
    	//Command to be executed which is nothing but "C:\Program Files\Java\jre6\bin\java.exe"
        CommandLine commandLine = new CommandLine((new CommandlineJava()).toString());

        //Adding its arguments
        for(String c : command) {
       		commandLine.addArgument(c);
        }

        Map<String, String> environmentMap = new HashMap<String, String>();
        environmentMap.put("CLASSPATH", environment);
        
        //12 hours after that it will be killed
        ExecuteWatchdog watchDog = new ExecuteWatchdog(43200000L);
        
        //Result Handler for executing the process in a Asynch way
        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        
        //Using Std out for the output/error stream
        PumpStreamHandler streamHandler = new PumpStreamHandler();
        
        //This is used to end the process when the JVM exits
        ShutdownHookProcessDestroyer processDestroyer = new ShutdownHookProcessDestroyer();        
        
        DefaultExecutor executor = new DefaultExecutor();
        try {
            executor.setStreamHandler(streamHandler); //Sets the stream handler
            executor.setWatchdog(watchDog);
        	executor.setWorkingDirectory(workingDirectory);
        	
        	//Executing the command
			executor.execute(commandLine, environmentMap, resultHandler);
			
			//Anything after this will be executed only when the command completes the execution
			resultHandler.waitFor();
		} catch (ExecuteException executeException) {
			logger.error("ExecuteException Occured", executeException);
		} catch (IOException iOException) {
			logger.error("IOException Occured", iOException);
		} catch (InterruptedException interruptedException) {
			logger.error("InterruptedException Occured", interruptedException);
		} finally {
			try {
				streamHandler.stop();
			} catch (IOException e) {
				logger.error("StreamHandler Exception Occured when closing Stream Handler", e);
			}
			watchDog.stop();
			executor.setProcessDestroyer(processDestroyer);
		}
    }

    public static void main(String[] args) {
    	ProcessBuilder pb = new ProcessBuilder("myCommand", "myArg1", "myArg2");
    	Map<String, String> envMap = pb.environment();
    	for (Map.Entry<String, String> entry : envMap.entrySet()) {
    	    String key = entry.getKey();
    	    String value = entry.getValue();
    	    
    	    System.out.println("key: " + key);
    	    System.out.println("value: " + value);
    	    
    	}
    }
}
