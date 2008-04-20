package cadbis.proxy;

import java.util.HashMap;
import java.util.List;

import cadbis.CADBiSThread;
import cadbis.proxy.bl.Protocol;
import cadbis.proxy.bl.UrlLogProtocol;
import cadbis.proxy.db.ProtocolDAO;
import cadbis.proxy.db.UrlLogProtocolDAO;

public class Aggregator extends CADBiSThread {
	private UrlLogProtocolDAO urllogDAO = new UrlLogProtocolDAO();	
	private static Aggregator instance = null;
	private static Object dLock = new Object();
	
	public Aggregator() {
		
	}

	public static Aggregator getInstance()
	{
		if(instance == null)
			instance = new Aggregator();
		return instance;
	}	
	
	protected void PopularityRefresh()
	{
		
	}
	
	protected void Aggregate()
	{
		List<UrlLogProtocol> logs = urllogDAO.getItemsByQuery("select unique_id,url,SUM(length) as length, date,COUNT(*) as 'count',user,ip from url_log group by unique_id,url order by unique_id,date,url,length");
		HashMap<String, Protocol> protocols = new HashMap<String, Protocol>();
		String format = Configurator.getInstance().getProperty("urllog_format");
		ProtocolDAO protoDAO = new ProtocolDAO();
		
		for(int i=0;i<logs.size();++i)
		{
			String data= format;
			data = data.replace("{DATE}",logs.get(i).getDate().toString());
			data = data.replace("{URL}",logs.get(i).getUrl().toString());
			data = data.replace("{COUNT}",logs.get(i).getCount().toString());
			data = data.replace("{LENGTH}",logs.get(i).getLength().toString());
			data = data.replace("{IP}",logs.get(i).getIp().toString());
			if(!protocols.containsKey(logs.get(i).getUnique_id()))
			{
				Protocol row = new Protocol(0,
						logs.get(i).getUnique_id().toString(),
						data,
						((Long)logs.get(i).getLength()));
				protocols.put(logs.get(i).getUnique_id().toString(), row);
			}
			else
				protocols.get(logs.get(i).getUnique_id().toString()).appendData(data);
				
		}
		
		for(String key : protocols.keySet())
		{
			if(protoDAO.getCountByQuery("select count(*) as count from `protocols` where unique_id = '"+protocols.get(key).getUnique_id()+"'")>0)
				urllogDAO.execSql(String.format("update `protocols` set data=CONCAT(data,'%s'), length=length+%d", 
						protocols.get(key).getData(),
						protocols.get(key).getLength()));			
			else
				urllogDAO.execSql(String.format("insert into `protocols`(unique_id,data,length) values" +
					" ('%s','%s', %d)", 
					protocols.get(key).getUnique_id(),
					protocols.get(key).getData(),
					protocols.get(key).getLength()));
		}
		
		urllogDAO.execSql("delete from `url_log`");
	}
	
	@SuppressWarnings("static-access")
	@Override
	public void run() {
		setName("Aggregator");
		synchronized (dLock) {
			while(true)
			{
				try{					
					Integer aPeriod = Integer.valueOf(Configurator.getInstance().getProperty("aggregator_period"));
					Thread.currentThread().sleep(aPeriod);
				}
				catch (InterruptedException e) {
					logger.error("Aggregator thread terminated! " +e.getMessage());
				}
				catch (NumberFormatException e) {
					logger.error("Configuration error! " + e.getMessage());
				}
				logger.info("Waking up, aggregating.");				
				Aggregate();
			}	
		}
	}
}
