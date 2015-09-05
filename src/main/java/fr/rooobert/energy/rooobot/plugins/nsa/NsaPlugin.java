package fr.rooobert.energy.rooobot.plugins.nsa;

import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;

import org.apache.log4j.Logger;

import fr.rooobert.energy.rooobot.Plugin;
import fr.rooobert.energy.rooobot.event.IrcMessageEvent;
import fr.rooobert.energy.rooobot.listeners.IrcMessageListener;
import fr.rooobert.energy.rooobot.plugins.nsa.NsaPluginDatabase.Message;

/** This bot plugin will spy everybody everywhere ! */
public class NsaPlugin extends Plugin implements IrcMessageListener {
	// --- Constants
	private final static Logger logger = Logger.getLogger(NsaPlugin.class);
	
	// --- Attributes
	private NsaPluginDatabase database = null;
	
	// --- Methods
	public NsaPlugin(String name, Properties props) {
		super(name, props);
	}
	
	@Override
	public void onEnable() throws Exception {
		super.onEnable();
		
		// Spy EVERYBODY, EVERWHERE ! 
		super.addMessageListener(null, null, this);
		
		// Connect to the database
		synchronized (this) {
			try {
				this.database = new NsaPluginDatabase(super.getConnection());
			} catch (Exception e) {
				throw e;
			}
		}
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		super.removeMessageListener(this);
		synchronized (this) {
			if (this.database != null) {
				this.database.close();
				this.database = null;
			}
		}
	}
	
	@Override
	public synchronized void onMessage(final IrcMessageEvent event) {
		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				synchronized (NsaPlugin.this) {
					try {
						NsaPlugin.this.database.saveMessage(new Message(event.getUser(), event.getDate(), event.getMessage()));
					} catch (SQLException e) {
						logger.error("Database SQL error : " + e.getMessage(), e);
					}
				}
			}
		};
		
		Thread thread = new Thread(runnable, "NsaTask");
		thread.start();
	}
	
	@Override
	public synchronized void onCommand(String channel, String sender, String login, String hostname, String message) {
		String cmd = "";
		String subject = null;
		
		Matcher matcher = COMMAND_ARGUMENT.matcher(message);
		if (matcher.find()) {
			cmd = matcher.group();
			subject = (matcher.find() ? matcher.group() : null);
		}
		
		switch (cmd) {
		case "info": // Info about one member
			this.doPrintInfo(channel, subject);
			break;
		case "help":
		default: // Print help
			this.doPrintHelp(channel);
			break;
		}
	}

	private void doPrintHelp(String channel) {
		// XXX Auto-generated method stub
		
	}

	private void doPrintInfo(String channel, String subject) {
		// XXX Auto-generated method stub
		
	}
}
