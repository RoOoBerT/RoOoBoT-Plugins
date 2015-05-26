package fr.rooobert.energy.rooobot.plugins;

import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.pircbotx.User;

import com.google.common.collect.ImmutableSortedSet;

import fr.rooobert.energy.rooobot.Plugin;
import fr.rooobert.energy.rooobot.event.IrcMessageEvent;
import fr.rooobert.energy.rooobot.listeners.IrcMessageListener;

/** Plugin Pan !*/
public class PanPlugin extends Plugin implements IrcMessageListener {
	// --- Constants
	protected static final Logger logger = LogManager.getLogger(PanPlugin.class);
	private static final Random RANDOM = new Random();
	
	// --- Attributes
	private final String text;
	protected final int countdown;
	protected final int maxdelay;
	protected final int mindelay;
	protected final int acceptTimeout;
	protected final int shootTimeout;
	
	protected final Object lockDuel = new Object();
	protected PanThread threadDuel = null;
	
	// --- Methods
	public PanPlugin(String name, Properties props) {
		super(name, props);
		
		this.text = props.getProperty("pan.text", "PAN").toUpperCase();
		this.countdown = (int) getInt(props.getProperty("pan.countdown"), 3);
		this.maxdelay = getInt(props.getProperty("pan.maxdelay"), 1500);
		this.mindelay = getInt(props.getProperty("pan.mindelay"), 3000);
		this.acceptTimeout = getInt(props.getProperty("pan.acceptTimeout"), 60000);
		this.shootTimeout = getInt(props.getProperty("pan.shootTimeout"), 10000);
	}
	
	@Override
	public void onEnable() throws Exception {
		super.onEnable();
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		
		super.removeMessageListener(this);
	}
	
	@Override
	public void onMessage(IrcMessageEvent event) {
		String message = event.getMessage();
		String sender = event.getUser();
		synchronized (this.lockDuel) {
			if (this.threadDuel != null) {
				if (message.toUpperCase().startsWith("OUI")) {
					this.threadDuel.accept(sender);
				} else if (message.toUpperCase().startsWith(this.text)) {
					this.threadDuel.shoot(sender);
				}
			}
		}
	}
	
	@Override
	public void onCommand(String channel, String sender, String login, String hostname, String message) {
		String target = null;
		
		// Parse command and player
		Matcher matcher = Plugin.COMMAND_ARGUMENT.matcher(message);
		if (matcher.find()) {
			target = matcher.group();
		}
		if (target != null && !target.trim().isEmpty()) {
			target = target.trim();
			if (!target.equalsIgnoreCase(sender)) {
				synchronized (this.lockDuel) {
					if (this.threadDuel == null) {
						// Check username
						ImmutableSortedSet<User> users = this.ircGetUsers(channel);
						for (User user : users) {
							String username = user.getNick();
							// FIXME BUG avec les prefixes ?
							/*if (!user.getPrefix().isEmpty()) {
								username = username.replaceFirst(user.getPrefix(), "");
							}*/
							if (username.equalsIgnoreCase(target)) {
								this.threadDuel = new PanThread(channel, new String[]{sender, target});
							}
						}
						
						// Start
						if (this.threadDuel != null) {
							logger.info("Starting Pan : " + sender + " VS " + target);
							
							this.threadDuel.start();
							this.threadDuel.accept(sender);
						} else {
							this.ircSendMessage(channel, "Désolé " + sender + ", personne ne s'appelle " + target + " ici...");
						}
					} else {
						this.ircSendMessage(channel, "Patiente un peu " + sender + ", un duel est déjà en cours.");
					}
				}
			} else {
				this.ircSendMessage(channel, "Tu ne peux pas te défier toi-même... Boulet de " + sender + "!");
			}
		} else {
			this.ircSendMessage(channel, "Qui veux-tu défier " + sender + " ? => !" + this.getName() + " <Nom>");
		}
	}
	
	/***/
	public static int getInt(String str, int defaultValue) {
		int l = defaultValue;
		if (str != null) {
			l = Integer.parseInt(str);
		}
		return l;
	}
	
	//
	protected class PanThread extends Thread {
		// --- Constants
		
		// --- Attributes
		private final String channel;
		private final String nicks[];
		
		private final Object objectAccepted = new Object();
		private final boolean accepted[];
		
		private final Object objectShoot = new Object();
		private String shooter = null;
		
		// --- Methods
		public PanThread(String channel, String nicks[]) {
			if (nicks == null || nicks.length < 2) {
				throw new RuntimeException("Pan needs at least 2 users !");
			}
			this.channel = channel;
			this.nicks = nicks;
			this.accepted = new boolean[nicks.length];
			for (int i = 0; i != this.accepted.length; i++) {
				this.accepted[i] = false;
			}
		}
		
		/** Signals that a user has accepted the fight
		 * @param nick */
		public void accept(String nick) {
			// Update player flag
			boolean start = true;
			for (int i = 0; i != this.nicks.length; i++) {
				String n = this.nicks[i];
				if (n.equalsIgnoreCase(nick)) {
					this.accepted[i] = true;
				} else if (!this.accepted[i]) {
					start = false;
				}
			}
			
			// Notify this thread if it should start
			if (start) {
				synchronized (this.objectAccepted) {
					this.objectAccepted.notify();
				}
			}
		}
		
		/** Signals that a user has shot for the current
		 * @param nick */
		public void shoot(String nick) {
			// FIXME Race condition ???
			// Si deux threads sont en attente sur cette ligne synchronized simultanement ? (possible ?)
			synchronized (this.objectShoot) {
				if (this.shooter == null) {
					// Ensure the nickname is found
					for (String n : this.nicks) {
						if (n.equalsIgnoreCase(nick)) {
							this.shooter = nick;
							
							// Notify the thread waiting for someone to shoot
							this.objectShoot.notify();
						}
					}
				}
			}
		}
		
		public void run() {
			// Register 2 message listeners
			for (String nick : this.nicks) {
				PanPlugin.super.addMessageListener(nick, null, PanPlugin.this);
			}
			
			// Send message
			PanPlugin.this.ircSendMessage(this.channel, "Hey " + this.nicks[1] + " ! " + this.nicks[0] + " te défie en duel ! Vas-tu le relever ? (oui/non)");
			
			// Wait for all players to accept
			long time = System.currentTimeMillis();
			synchronized (this.objectAccepted) {
				try {
					this.objectAccepted.wait(PanPlugin.this.acceptTimeout);
				} catch (InterruptedException e) {
					logger.error("This cannot happen", e);
				}
			}
			
			// Check if accepted
			if (System.currentTimeMillis() - time < PanPlugin.this.acceptTimeout) {
				PanPlugin.this.ircSendMessage(this.channel, "Hey " + this.nicks[0] + " & " + this.nicks[1]
						+ " ! Je vais compter à rebours jusqu'à 0. Le premier qui tire après écoulement du compte à rebours gagne ! (Ecrire \"" + PanPlugin.this.text +"\").");
				PanPlugin.this.ircSendMessage(this.channel, "Attention ! Si tu tires AVANT, ton honneur est sali et on t'enterre vivant !");
				
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					logger.error("This cannot happen", e);
				}
				
				PanPlugin.this.ircSendMessage(this.channel, "Duel à l'ancienne ! " + this.nicks[0] + " VS " + this.nicks[1]);
				
				// Attente d'un tireur
				synchronized (this.objectShoot) {
					int i = PanPlugin.this.countdown;
					while (i != 0 && this.shooter == null) {
						//PanPlugin.this.ircSendMessage(this.channel, "");
						
						long delay = mindelay + RANDOM.nextInt(maxdelay - mindelay);
						try {
							this.objectShoot.wait(delay);
						} catch (InterruptedException e) {
							logger.error("This cannot happen", e);
						}
						
						PanPlugin.this.ircSendMessage(this.channel, String.valueOf(i));
						
						i--;
					}
					
					// Check if someone shooted too early
					if (this.shooter == null) {
						// Wait for someone to shoot
						PanPlugin.this.ircSendMessage(this.channel, "Tirez !");
						try {
							this.objectShoot.wait(shootTimeout);
						} catch (InterruptedException e) {
							logger.error("This cannot happen", e);
						}
						
						// 
						if (this.shooter != null) {
							String victim = (this.shooter.equalsIgnoreCase(nicks[0]) ? nicks[1] : nicks[0]);
							
							PanPlugin.this.ircSendMessage(this.channel, "BOOM ! R.I.P " + victim);
							PanPlugin.this.ircSendMessage(this.channel, "Bravo " + this.shooter + " ! Je pense que tout le monde a compris qui était le patron ici !");
						} else {
							PanPlugin.this.ircSendMessage(this.channel, "Bon... On dirait que " + this.nicks[0] + " & " + this.nicks[1] + " sont déjà morts de vieillesse.");
						}
					} else {
						PanPlugin.this.ircSendMessage(this.channel, "Honte à toi " + shooter + " ! Tu as tiré avant l'heure ! Goudron et les plumes !");
					}
				}
			} else {
				PanPlugin.this.ircSendMessage(this.channel, "Trouillard " + this.nicks[1] + " !");
			}
			
			// Remove message listeners
			PanPlugin.super.removeMessageListener(PanPlugin.this);
			
			// Duel finished
			synchronized (PanPlugin.this.lockDuel) {
				PanPlugin.this.threadDuel = null;
			}
		}
	}
}
