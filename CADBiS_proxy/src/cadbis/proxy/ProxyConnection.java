package cadbis.proxy;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cadbis.CADBiSThread;
import cadbis.bl.Action;
import cadbis.proxy.httpparser.RequestHttpParser;
import cadbis.proxy.httpparser.ResponseHttpParser;
import cadbis.utils.IOUtils;
import cadbis.utils.StringUtils;



class ProxyConnection extends CADBiSThread {

	 private Socket fromClient;
	 private String host;
	 private int port;
	 private long timeout;
	 private final Logger logger = LoggerFactory.getLogger(getClass());
	 private static final int MAX_BLOCK_SIZE = 4096;
	 private boolean trueproxy = false;
	 
	 ProxyConnection(Socket s, long timeout)
	 {
		 fromClient=s;
		 trueproxy = true;
		 this.timeout=timeout;
	 }
	 
	 ProxyConnection(Socket s, String fwdhost, int fwdport, long timeout) 
	 {
		  fromClient=s;
		  this.host = fwdhost;
		  this.port = fwdport;
		  this.timeout=timeout;
	 }

	 protected boolean isNeedToCheckContent(ResponseHttpParser ResponseParser)
	 {
		 boolean enabled = ProxyConfigurator.getInstance().getProperty("contentcheck").equals("enabled");
		 String ctype = ResponseParser.GetHeader("Content-Type");
		 return enabled && (ctype.indexOf("text/html")>=0 || ctype.indexOf("text/plain")>=0);
	 }
	 
	 protected void answerAccessDenied(List<byte[]> buffer, String UserIp, String HttpHost)
	 {
		 buffer.clear();
			String accDenied = ProxyConfigurator.getInstance().getFile_denied_access();
			accDenied = accDenied.replace("%U",HttpHost);
			accDenied = accDenied.replace("%T",new Date().toString());
			for(int i=0;i<accDenied.length();i+=MAX_BLOCK_SIZE)
			{
				int sAvail = 0;
				if(i+MAX_BLOCK_SIZE<accDenied.length())
					sAvail = MAX_BLOCK_SIZE;
				else
					sAvail = accDenied.length() - sAvail - 1;												
				buffer.add(accDenied.substring(i,sAvail).getBytes());
			}
			 /*******************************
			  * log the attempt in a separate thread
			  *******************************/
			final String fHttpHost = HttpHost;
			final String fUserIp = UserIp;
			 new CADBiSThread(){
					public void run()
					{	
						Collector.getInstance().AddDeniedAccessAttempt(fUserIp, fHttpHost);
						complete();
					}
			 }.start();		 
	 }
	 
	 /**
	  * This is a common proxy lifecycle method
	  */
	 public void run() 
	 {
		 InputStream clientIn = null;
		 OutputStream clientOut = null;
		 InputStream serverIn = null;
		 OutputStream serverOut = null;	 
		 Socket toServer = null;
		 
		 // defining the streams
		 try
		 {			 
			 clientIn = fromClient.getInputStream();
			 clientOut = new BufferedOutputStream(fromClient.getOutputStream());
		 }
		 catch(IOException e)
		 {
			 logger.error(e.getMessage());
		 }

		 
		 // lifetime members
		 long startTime = new Date().getTime();
		 long endTime = new Date().getTime();
		 boolean isReadWrite = true;
		 boolean isAccessDenied = false;
		 int ErrorsCount = 0;
		 String HttpHost = "";
		 int HttpPort = 80;
		 String HostIp = "";
		 StringBuffer fullResponseBuffer = new StringBuffer();
		 String UserIp;		 
		 boolean NeedToCheckContent = false;
		 boolean isFullAnswer = false;

		 int max_resp_size = Integer.parseInt(ProxyConfigurator.getInstance().getProperty("max_response_buffer_size"));
		 fullResponseBuffer = new StringBuffer();
		 // current user IP
		 UserIp = fromClient.getInetAddress().getHostAddress();
		 final int WaitRWPeriod = Integer.parseInt(ProxyConfigurator.getInstance().getProperty("waitrwtime"));
		 final int MaxErrorsCount = Integer.parseInt(ProxyConfigurator.getInstance().getProperty("maxerrorscount"));
		 Integer cid = null;
		 boolean NeedToRecognizeCategory = false;
		 final ResponseHttpParser firstResponsePacketParser = new ResponseHttpParser();
		 while(endTime - startTime < timeout && ErrorsCount<MaxErrorsCount && !isFullAnswer) 
		 {
			 //logger.debug("new packet processing iteration, timeout: " + (endTime-startTime) +" ms");
			 final RequestHttpParser RequestParser= new RequestHttpParser();	
			 final ResponseHttpParser ResponseParser= new ResponseHttpParser(); 
			 
			 
			 isReadWrite = false;			 
			 String cRcvdData = new String("");
			 int RcvdAmount = 0;
			 List<byte[]> buffer = new ArrayList<byte[]>();		
	 
			 
			 /*******************************
			  * Recieving data client->proxy
			  *******************************/			 
			 try{
				buffer.clear();
				IOUtils.readStreamAsArray(clientIn, buffer);
				if(buffer.size()>0)
					logger.debug("read from clientIn completed " + buffer.size()+" blocks read");	
			 }
			 catch(IOException e)
			 {
				 ErrorsCount++;
				 logger.error("Recieving data client->proxy error: "+e.getMessage());
			 }
			 
			 
			 /*******************************
			  * Parsing request
			  *******************************/	
			 if(buffer.size()>0)
			 {
				cRcvdData = new String(StringUtils.getChars(buffer.get(0)));
				startTime = new Date().getTime();
				isReadWrite = true;
				if(!RequestParser.isRequestParsed()){
					RequestParser.ClearHeaders();
					RequestParser.ParseRequestHeaders(cRcvdData);
				}
				RequestParser.setEncodingAcceptable(false);
				buffer.set(0, RequestParser.GetFixedPacket(buffer.get(0)));
				HttpHost = RequestParser.getHttpHost();
				HttpPort = RequestParser.getHttpPort();		
			 }

			 
			 
			 /*******************************
			  * Connecting proxy->squid
			  *******************************/
			 if(toServer == null && buffer.size()>0)
			 {				 
				 HostIp = "";
				 String hostTo=this.host;
				 int portTo=this.port;
				 if(trueproxy)
				 {
					 hostTo = HttpHost;
					 portTo = HttpPort;
					 logger.debug("True proxying enabled: client->"+hostTo+":"+portTo);
				 }
				 try
				 {
					 logger.debug("Opening connection to server " + hostTo + ":" + portTo);
					 toServer = new Socket(hostTo,portTo);
					 if(trueproxy){
						 HostIp = toServer.getInetAddress().getHostAddress();						 
					 }
					 serverIn = toServer.getInputStream();
					 serverOut = new BufferedOutputStream(toServer.getOutputStream());
				 }
				 catch(UnknownHostException e)
				 {
					 ErrorsCount++;
					 logger.error("Connecting proxy->squid("+hostTo+":"+portTo+") error: unknown host: " + e.getMessage());
				 }		 
				 catch(IOException e)
				 {
					 ErrorsCount++;
					 logger.error("Connecting proxy->squid("+hostTo+":"+portTo+") error: "+e.getMessage());
				 }
			 }
			 
			 
			 
			/*******************************
			 * Sending data proxy->squid
			 ******************************/
			 try{
				if(toServer!=null && buffer.size()>0)
				{
					// check if url is denied
					isAccessDenied = !Collector.getInstance().CheckAccessToUrl(UserIp,HttpHost);
					if(!isAccessDenied)
					{
						//cRcvdData = RequestParser.GetFixedFullHeader();
						logger.debug("writing to serverOut "+buffer.size()+" blocks...");
						IOUtils.writeArrayToStream(serverOut, buffer);
						logger.debug("write to serverOut completed...");
						startTime = new Date().getTime();
						isReadWrite = true;
					}
				}
				
			 }
			 catch(IOException e)
			 {
				 ErrorsCount++;
				 logger.error("Sending data proxy->squid("+toServer.getInetAddress().getHostName()+":"+toServer.getPort()+") error: " + e.getMessage());
			 }			 
			 
			 
			 
			 /*******************************
			  * Receiving data squid->proxy
			  *******************************/	 
			 try
			 {
				buffer.clear();
				if(!isAccessDenied && toServer!=null)
					RcvdAmount = IOUtils.readStreamAsArray(serverIn, buffer);
				else if(toServer!=null)
				{
					// indicating that we have finished the answer
					isFullAnswer = true;
					// filling the buffer with the denied access content
					answerAccessDenied(buffer,UserIp,HttpHost);
				}
			 }
			 catch(IOException e)
			 {
				 ErrorsCount++;
				 logger.error("Receiving data  squid("+toServer.getInetAddress().getHostName()+":"+toServer.getPort()+")->proxy error: " + e.getMessage());
			 }
								
			 
			 /**************************************
			  * Parse the response headers
			  *************************************/
			 if(buffer.size()>0 && !firstResponsePacketParser.isResponseParsed())
			 {
				 firstResponsePacketParser.ParseResponseHeaders(new String(StringUtils.getChars(buffer.get(0))));				 
				 logger.info("first.content-type="+firstResponsePacketParser.GetHeader("Content-Type"));
				 NeedToCheckContent = isNeedToCheckContent(firstResponsePacketParser);
			 }
			 /***************************************
			  * Analyzing the response to define the access
			  * to category
			  * TODO: The place for content analyze
			  * analyze only text/html content
			  ***************************************/
			 if(NeedToCheckContent)
			 {
				 if(!NeedToRecognizeCategory)
					 cid = Categorizer.getInstance().getCategoryForUrl(HttpHost);
				 // category not recognized, trying to recognize it
				 if(cid == null)
				 {	
					 NeedToRecognizeCategory = true;
					 /**
					  * filling the full response buffer only if 
					  * the category is not recognized...
					  * copying only allowed size of memory
					  */
					 if(fullResponseBuffer.length() < max_resp_size)
						 for(int i=0;i<buffer.size();++i)
							 fullResponseBuffer.append(StringUtils.getChars(buffer.get(i)));
					 
					 /**
					  * we must do it only if the content 
					  * is already fully received
					  * guessing that it is happened... 
					  */							 
					 if(endTime - startTime > timeout/4 || fullResponseBuffer.length() >=  max_resp_size)
					 {
						 final StringBuffer fResponseBuffer = fullResponseBuffer;
						 final String fHttpHost = HttpHost;
						 new CategoryRecognizer(firstResponsePacketParser, fResponseBuffer, fHttpHost).start();								 
						 NeedToCheckContent = false;
					 }
				 }
				 else
				 {
					 NeedToCheckContent = false;
					 Action act = Collector.getInstance().getActionByUserIp(UserIp);
					 if(act != null)
					 {
						boolean access = Categorizer.getInstance().checkAccessToCategory(act.getGid(), cid);
						logger.info("Checking access of "+act.getUser()+" to cid="+cid+" = "+ access +"...");
						if(!access)
						{
							// indicating that we have finished the answer
							isFullAnswer = true;
							// answer the denied access
							answerAccessDenied(buffer,UserIp,HttpHost);
						}
					 }
				 }
				 
			 }			 
			 
			 
			/*******************************
			 * Sending proxy->client
			 *******************************/	
			 try
			 {
				 // if we have read smthg
				 if(buffer.size()>0 && toServer!=null)
				 {					 
					 ResponseParser.ParseResponseHeaders(new String(StringUtils.getChars(buffer.get(0))));
					 logger.debug("Response.type = "+ResponseParser.GetHeader("Content-Type"));
					 logger.debug("buffer, blocks count = " + buffer.size());					 
					 isReadWrite = true;
					 // indicate the last rw time
					 startTime = new Date().getTime();				 
					 for(int i=0;i<buffer.size();++i)
					 {
						 logger.debug("block["+i+"].size=" + buffer.get(i).length);
						 clientOut.write(buffer.get(i));
						 clientOut.flush();
					 }
					 buffer.clear();
					 /*******************************
					  * Sending the data to collector in a separate thread
					  *******************************/	
					 String fHttpHost = HttpHost;
					 String ContentType = ResponseParser.GetHeader("Content-Type");
					 new PreCollector(fHttpHost,RcvdAmount,UserIp,ContentType,HostIp)
					 	.start();
				 }
			 }
			 catch(IOException e)
			 {
				 ErrorsCount++;
				 logger.error("Sending proxy->client("+fromClient.getInetAddress().getHostName()+":"+fromClient.getPort()+") error: " + e.getMessage());
			 }
			

			 
			 try
			 {
				 if(!isReadWrite) 
				 {
					 endTime = new Date().getTime();
					 Thread.sleep(WaitRWPeriod);
				 }				 
			 }
			 catch(InterruptedException e)
			 {
				 ErrorsCount++;
				 logger.error("Processing error: " + e.getMessage());
			 }
		 }
			 
		 // closing connections
		 try 
		 {
			 if(clientIn!=null)
				 clientIn.close();
			 if(clientOut!=null)
				 clientOut.close();
			 if(fromClient!=null)
				 fromClient.close();
			 if(serverIn!=null)
				 serverIn.close();
			 if(serverOut!=null)
				 serverOut.close();
			 if(toServer!=null)
				 toServer.close();
			 logger.debug("Connections closed successfully...");
		 }
		 catch(Exception e) 
		 {
			 logger.error("Warning! Connections close error!");
			 e.printStackTrace(System.err);
		 }
		 finally
		 {			 
			 // complete our thread
			 complete();
		 }
	}
	 
	 @Override
	protected void finalize() throws Throwable {
		logger.debug("ProxyConnection thread garbage collected");
		super.finalize();
		
	}
}