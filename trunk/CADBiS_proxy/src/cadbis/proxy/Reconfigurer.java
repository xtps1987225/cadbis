package cadbis.proxy;

import java.io.IOException;

import cadbis.CADBiSDaemon;

public class Reconfigurer extends CADBiSDaemon{	
	private static Reconfigurer instance = null;
	protected Reconfigurer() {
		super("Reconfigurer",Integer.valueOf(Configurator.getInstance().getProperty("reconf_period")));
	}

	public static Reconfigurer getInstance(){
		if(instance == null)
			instance = new Reconfigurer();
		return (Reconfigurer)instance;
	}	
	
	@Override
	protected void daemonize() {
		try{
			Configurator.getInstance().reloadData();
			logger.info("Reconfigurer: Config reloaded.");
		}
		catch (IOException e) {
			logger.error("Failed to load config: " + e.getMessage());
		}		
	}
}
