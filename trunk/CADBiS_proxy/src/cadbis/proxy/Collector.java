package cadbis.proxy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


import cadbis.CADBiSDaemon;
import cadbis.bl.Action;
import cadbis.bl.CollectedData;
import cadbis.bl.UrlDenied;
import cadbis.db.ActionDAO;
import cadbis.db.DeniedUrlDAO;

public class Collector extends CADBiSDaemon{
	private static Collector instance = null;
	private List<Action> actions = null;	
	private HashMap<Integer, Action> actionsOfIps;
	private static Object wLock = null;
	
	private void createObjects()
	{
		actionsOfIps = new HashMap<Integer, Action>();
		actions = new ArrayList<Action>();
	}
	
	private Collector(){
		super("Collector",Integer.valueOf(ProxyConfigurator.getInstance().getProperty("collector_period")));
		createObjects();
		wLock = new Object();
	}

	public static Collector getInstance(){
		if(instance == null)
			instance = new Collector();
		return instance;
	}
	
	@Override
	protected void daemonize() {
		logger.info("waking up, flushing info...");				
		FlushCollected();
		logger.info("info flushed, refreshing sessions...");
		RefreshInfo();
		logger.info("sessions refreshed.");		
	}	
	
	//------------------------------------------------------
	/**
	 * Refresh sessions information without synchronization
	 */
	private void _refreshSessions()
	{
		createObjects();
		actions = getActiveSessions();
		if(actions!=null)
		for(int i=0;i<actions.size();++i){
		if(actions.get(i)!=null){
			actionsOfIps.put(actions.get(i).getIp().toString().hashCode(), actions.get(i));
			DeniedUrlDAO dao = new DeniedUrlDAO();
			actions.get(i).getDeniedUrls().clear();
			List<UrlDenied> durls = dao.getItemsByQuery("select * from url_denied where gid="+actions.get(i).getGid());
			if(durls!=null)
				for(int j=0;j<durls.size();++j){
					logger.debug("Read denied urls for '" + actions.get(i).getUser()+"'... " + durls.get(j).getUrl());
					actions.get(i).getDeniedUrls().add(durls.get(j));
				}
			}
		}
	}
	//------------------------------------------------------
	/**
	 * Get session by user IP
	 * @return
	 */	
	public Action getActionByUserIp(String ip)
	{
		return actionsOfIps.get(ip.hashCode());
	}
	//------------------------------------------------------
	/**
	 * Get all active sessions
	 * @return
	 */
	public List<Action> getActiveSessions()
	{	
		return new ActionDAO().getItemsByQuery("select * from actions where terminate_cause='Online'");
	}	
	//------------------------------------------------------
	/**
	 * Refresh info with synchronization
	 */
	public void RefreshInfo()
	{
		synchronized (wLock) 
		{
			_refreshSessions();
		}
	}
	//------------------------------------------------------
	/**
	 * Collect the information about packet
	 */
	public void Collect(String userIp, String hostUrl, long rcvdBytes, Date date, String hostIp, String content_type)
	{
		synchronized (wLock) {
			if(!actionsOfIps.containsKey(userIp.hashCode()))
			{
				logger.warn("User of ip='"+userIp+"' not found, refreshing sessions info.");
				_refreshSessions();		
			}					
			Action action = getActionByUserIp(userIp);			
			if(action!=null){
				action.getCollectedUrls().add(new CollectedData(hostUrl,rcvdBytes,date, hostIp,content_type));
				logger.debug("Collecting data... ip="+hostIp+", url="+hostUrl+", bytes="+rcvdBytes);
			}
		}
	}
	//------------------------------------------------------
	/**
	 * Collect the information about packet
	 */	
	public boolean CheckAccessToUrl(String userIp, String url)
	{
		Action action = getActionByUserIp(userIp);
		if(action!=null && action.getDeniedUrls()!=null)
		for(int i=0;i<action.getDeniedUrls().size();++i)
		{
			if(action.getDeniedUrls() != null && action.getDeniedUrls().get(i) != null)
			if(url.matches(action.getDeniedUrls().get(i).getUrl().toString()))
				return false;			
		}
		return true;
	}
	
	/**
	 * Log the denied access attempt
	 * @param userIp
	 * @param url
	 */
	public void AddDeniedAccessAttempt(String userIp, String url)
	{
		Action action = getActionByUserIp(userIp);
		ActionDAO actionDAO = new ActionDAO();
		actionDAO.execSql(String.format("insert into url_denied_log(url,unique_id,date) values('%s','%s',NOW())",url,action.getUnique_id()));
	}
	
	/**
	 * Flush all collected data
	 */
	public void FlushCollected()
	{		
		synchronized (wLock) {
		if(actions!=null)
			for(int i=0;i<actions.size();++i)
			{
				ActionDAO actionDAO = new ActionDAO();
				List<CollectedData> col = actions.get(i).getCollectedUrls();
				for(int j=0; j<col.size(); ++j)
				{
					logger.debug("Collected data for userIp="+actions.get(i).getIp()+"("+actions.get(i).getUser()+"), url="+col.get(j).url+", bytes="+col.get(j).bytes+", hostIp="+col.get(j).ip);
					actionDAO.execSql("insert into url_log values ('"+
							actions.get(i).getUnique_id() +"','"+
							actions.get(i).getUser() +"','"+
							col.get(j).url+"',"+
							col.get(j).bytes+",'"+
							new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(col.get(j).date)+"','"+
							col.get(j).ip+"','" +
							col.get(j).content_type+"')");
				}
				actions.get(i).getCollectedUrls().clear();
			}			
			createObjects();
		}
	}

	public static Object getWLock() {
		return wLock;
	}
	

	
	
}
