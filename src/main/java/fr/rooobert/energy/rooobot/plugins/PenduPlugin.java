package fr.rooobert.energy.rooobot.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import fr.rooobert.energy.rooobot.Plugin;
import fr.rooobert.energy.rooobot.Utilities;
import fr.rooobert.energy.rooobot.event.IrcMessageEvent;
import fr.rooobert.energy.rooobot.listeners.IrcMessageListener;

/** Plugin Pendu ! */
public class PenduPlugin extends Plugin implements IrcMessageListener {
	// --- Constants
	protected static final Logger logger = LogManager.getLogger(PenduPlugin.class);
	private static final Random RANDOM = new Random();
	
	private static final Pattern WORD = Pattern.compile("[a-zA-Z\\-]+");
	
	// --- Attributes
	private final String channel;
	private final List<String> wordList;
	private final boolean useDatabase;
	private final int initialLives;
	
	private String wordToGuess = null;
	private String displayedWord = null;
	private final Set<Character> lettersUsed = new HashSet<>();
	private int lives = 0;
	
	// --- Methods
	public PenduPlugin(String name, Properties props) throws Exception {
		super(name, props);
		
		this.initialLives = Utilities.getInt(props.getProperty("pendu.lives"), 8, 1, 24);
		this.channel = props.getProperty("pendu.channel", "Pendu");
		
		this.useDatabase = Boolean.parseBoolean(props.getProperty("pendu.useDatabase", "true"));
		File wordlist = new File(props.getProperty("pendu.wordlist", "pendu.txt"));
		try {
			this.wordList = Files.readAllLines(wordlist.toPath(), Charset.defaultCharset());
		} catch (IOException e) {
			throw new Exception("Cannot load pendu wordlist", e);
		}
		// Delete non-words
		Iterator<String> it = this.wordList.iterator();
		while (it.hasNext()) {
			String s = it.next();
			if (!WORD.matcher(s).matches()) {
				it.remove();
			}
		}
		
		// Ensure we have at least one word
		if (this.wordList.isEmpty()) {
			throw new Exception("Empty wordlist for pendu !");
		}
		
		logger.info("Pendu : Loaded " + this.wordList.size() + " words !");
		
		// Save scores to database, create schema if needed
		if (this.useDatabase) {
			Connection connection = super.getConnection();
			PenduPlugin.dbInit(connection);
		}
	}
	
	@Override
	public void onEnable() throws Exception {
		super.onEnable();
		
		super.addMessageListener(null, this.channel, this);
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		
		super.removeMessageListener(this);
	}
	
	@Override
	public void onMessage(IrcMessageEvent event) {
		String message = event.getMessage().trim().toUpperCase();
		String sender = event.getUser();
		String channel = event.getChannel();
		
		// Ensure the game is running
		if (this.wordToGuess != null) {
			// Check if only one word was said
			if (WORD.matcher(message).matches()) {
				// Check if the user guessed the right word
				if (message.equalsIgnoreCase(this.wordToGuess)) {
					this.ircSendMessage(channel, "Felicitations " + sender + " ! Le mot était " + this.wordToGuess);
					this.reset();
				} else if (message.length() == 1) {
					final char letter = message.charAt(0);
					
					// A letter was tried, check if it was not already tried
					if (!this.lettersUsed.contains(letter)) {
						this.lettersUsed.add(letter);
						
						// Check if the word actually contains this letter
						if (this.wordToGuess.indexOf(letter) >= 0) {
							//
							boolean finished = true;
							for (int i = 0; i != this.wordToGuess.length(); i++) {
								char c = this.wordToGuess.charAt(i);
								if (c == letter) {
									this.displayedWord =
											this.displayedWord.substring(0, 2 * i)
											+ letter
											+ this.displayedWord.substring(Math.min(2 * i + 1, this.displayedWord.length()));
								}
								// Check if there is at least one letter in the word not tried
								if (!this.lettersUsed.contains(c)) {
									finished = false;
								}
							}
							
							// Finished
							if (finished) {
								this.ircSendMessage(channel, "Felicitations " + sender + " ! Le mot était " + this.wordToGuess);
								this.reset();
							}
						} else {
							// Decrement lives remaining
							if (--this.lives <= 0) {
								// Defeat
								this.ircSendMessage(channel, "PENDU ! Le mot était " + this.wordToGuess);
								this.reset();
							}
						}
						this.doPrintStatus(channel);
					}
				}
			}
		}
	}
	
	@Override
	public void onCommand(String channel, String sender, String login, String hostname, String message) {
		String cmd = "";
		String options = null;
		
		Matcher matcher = COMMAND_ARGUMENT.matcher(message);
		if (matcher.find()) {
			cmd = matcher.group();
			options = (matcher.find() ? matcher.group() : null);
		}
		
		switch (cmd) {
		case "start": // Starts a new game
			this.doStart(channel);
			break;
		case "status": // Displays status of the game
			this.doPrintStatus(channel);
			break;
		case "scores": // Displays scores
			this.doPrintScores(channel);
			break;
		case "help": // Print help
		default:
			this.doPrintHelp(channel);
			break;
		}
	}
	
	/** Called when a user asks to start a new game
	 * @param channel */
	private void doStart(String channel) {
		if (this.wordToGuess == null) {
			super.ircJoinChannel(this.channel);
			
			// Guess a new word
			int n = RANDOM.nextInt(this.wordList.size());
			this.wordToGuess = this.wordList.get(n).trim().toUpperCase();
			this.displayedWord = this.wordToGuess.replaceAll(".", "_ ").trim();
			this.lettersUsed.clear();
			this.lives = this.initialLives;
			
			// TODO Devoiler 1ere et derniere lettre ? 
			
			this.ircSendMessage(channel, "Jeu du pendu : Nouveau mot a deviner ! Venez dans le canal " + this.channel);
			this.doPrintStatus(this.channel);
		} else {
			// Already guessing a word
		}
	}
	
	/** Called when a user asks status for the running game
	 * @param channel */
	private void doPrintStatus(String channel) {
		if (this.wordToGuess != null) {
			this.ircSendMessage(channel, "A deviner : " + this.displayedWord + " (" + this.wordToGuess.length()
					+ " lettres) | Vies : " + this.lives + " | Lettres : " + this.lettersUsed);
		} else {
			this.ircSendMessage(channel, "Pas de partie de pendu en cours.");
		}
	}

	/** Called when a user requests to print the high scores
	 * @param channel */
	private void doPrintScores(String channel) {
		Connection connection = super.getConnection();
	}
	
	/** Called when a user requests to print help for this plugin */
	private void doPrintHelp(String channel) {
		
	}

	/** Resets the status of the game */
	private void reset() {
		this.wordToGuess = null;
		this.displayedWord = null;
		this.lettersUsed.clear();
		this.lives = 0;
	}
	
	// Database methods
	public static void dbInit(Connection connection) throws Exception {
		{
			try (Statement stm = connection.createStatement()) {
				stm.execute("CREATE TABLE IF NOT EXIST ");
			} catch (SQLException e) {
				
			}
		}
	}
	
}
