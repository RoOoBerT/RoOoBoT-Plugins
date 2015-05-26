package fr.rooobert.energy.rooobot.plugins;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.Logger;

import com.google.code.chatterbotapi.ChatterBot;
import com.google.code.chatterbotapi.ChatterBotFactory;
import com.google.code.chatterbotapi.ChatterBotSession;
import com.google.code.chatterbotapi.ChatterBotType;

import fr.rooobert.energy.rooobot.Plugin;
import fr.rooobert.energy.rooobot.event.IrcMessageEvent;
import fr.rooobert.energy.rooobot.event.IrcPrivateMessageEvent;
import fr.rooobert.energy.rooobot.listeners.IrcMessageListener;
import fr.rooobert.energy.rooobot.listeners.IrcPrivateMessageListener;

public class CleverPlugin extends Plugin implements IrcMessageListener, IrcPrivateMessageListener {
	// --- Constants
	private final static Logger logger = Logger.getLogger(CleverPlugin.class);
	
	// --- Attributes
	private final ChatterBotFactory factory = new ChatterBotFactory();
	private ChatterBot bot = null;
	private ChatterBotSession botSession = null;
	
	private final ChatterBotType type;
	private final String seed;
	private final String botName;
	private boolean answerpm;
	
	private final Map<String, ChatterBotSession> sessions = new HashMap<>();
	
	// --- Methods
	public CleverPlugin(String name, Properties props) {
		super(name, props);
		
		ChatterBotType type = ChatterBotType.valueOf(props.getProperty("clever.type", "CLEVERBOT"));
		if (type == null) {
			type = ChatterBotType.CLEVERBOT;
		}
		this.type = type;
		this.seed = props.getProperty("clever.seed", null);
		this.answerpm = Boolean.parseBoolean(props.getProperty("clever.answerpm", "true"));
		this.botName = props.getProperty("clever.name", "CleverBot");
	}
	
	@Override
	public void onEnable() throws Exception {
		super.onEnable();
		
		if (this.seed != null && !this.seed.trim().isEmpty()) {
			this.bot = this.factory.create(this.type, this.seed);
		} else {
			this.bot = this.factory.create(this.type);
		}
		this.botSession = this.bot.createSession();
		
		super.addMessageListener(null, null, this);
		super.addPrivateMessageListener(null, this);
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		
		super.removeMessageListener(this);
		super.removePrivateMessageListener(this);
		
		this.sessions.clear();
		this.botSession = null;
	}
	
	@Override
	public synchronized void onMessage(final IrcMessageEvent event) {
		if (event.getMessage().toLowerCase().contains(super.getNick().toLowerCase())) {
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					String response = null;
					synchronized (CleverPlugin.this.botSession) {
						response = getResponse(CleverPlugin.this.botSession, event.getMessage());
					}
					synchronized (CleverPlugin.this) {
						CleverPlugin.super.ircSendMessage(event.getChannel(), response);
					}
				}
			};
			
			Thread thread = new Thread(runnable);
			thread.start();
			
			// Consume this event so no other plugin will receive it
			event.consume();
		}
	}
	
	@Override
	public synchronized void onCommand(String channel, String sender, String login, String hostname, String message) {
	}
	
	@Override
	public void onPrivateMessage(IrcPrivateMessageEvent event) {
		// Check if the bot should respond to private messages
		if (this.answerpm) {
			final String username = event.getUser();
			final String message = event.getMessage();
			
			// Get an existing session or create it
			ChatterBotSession session = this.sessions.get(username);
			if (session == null) {
				session = this.bot.createSession();
				this.sessions.put(username, session);
			}
			
			// TODO Those sessions opened should be (periodically ?) closed/cleaned up (minor memory leak)
			
			// Launch parallel processing
			final ChatterBotSession session2 = session;
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					String response = null;
					synchronized (session2) {
						response = getResponse(session2, message);
					}
					synchronized (CleverPlugin.this) {
						CleverPlugin.super.ircSendMessage(username, response);
					}
				}
			};
			
			Thread thread = new Thread(runnable);
			thread.start();
			
			// Mark the event as consumed so no other plugin(s) receive it
			event.consume();
		}
	}
	
	/** Get a clever bot response
	 * @param session
	 * @param message */
	public String getResponse(ChatterBotSession session, String message) {
		// Replace bot's IRC nickname by CleverBot name
		message = message.replace(super.getNick(), this.botName);
		
		// FIXME This could result in answers like :
		// [17:37:57] <MoOoRice> Comment tu vas RoOoBoT ?
		// [17:37:58] <RoOoBoT> Je ne m'appelles pas Clever ;).
		
		// 
		String response = null;
		logger.trace("CleverBot message : " + message);
		try {
			response = session.think(message);
			// Decode HTML
			if (response != null) {
				response = StringEscapeUtils.unescapeHtml4(response);
			}
		} catch (Exception e) {
			logger.info("Exception while CleverBot thinking : " + e.getMessage(), e);
			response = "Je suis terriblement navré. J'ai planté en réfléchissant : " + e.getMessage();
		}
		logger.trace("CleverBot response : " + response);
		
		return response;
	}
}
