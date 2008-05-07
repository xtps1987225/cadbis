package cadbis.logger;

import org.apache.log4j.spi.LoggingEvent;

public class ConsoleAppender extends org.apache.log4j.ConsoleAppender {
	@Override
	public synchronized void doAppend(LoggingEvent event) {
		String message = "";
//		message += new SimpleDateFormat("HH:mm:ss").format(new Date());
//		message += " ["+event.getLoggerName()+"]";
//		message += "\t ["+event.getThreadName()+"]";
		message += Thread.activeCount()+"thr/"+((Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024)+"/"+(Runtime.getRuntime().totalMemory()/1024)+"/"+(Runtime.getRuntime().maxMemory()/1024)+"Kb";
//		message += "\t " + event.getMessage();
		
		super.doAppend(event);
		System.out.println(message);
	}
}
