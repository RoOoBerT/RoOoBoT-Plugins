package fr.rooobert.energy.rooobot.plugins;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import fr.rooobert.energy.rooobot.HtmlUtilities;
import fr.rooobert.energy.rooobot.HttpUtilities;
import fr.rooobert.energy.rooobot.Plugin;
import fr.rooobert.energy.rooobot.Utilities;
import fr.rooobert.energy.rooobot.event.IrcMessageEvent;
import fr.rooobert.energy.rooobot.listeners.IrcMessageListener;

public class GooglePlugin extends Plugin implements IrcMessageListener {
	// --- Types
	public static class SearchResult {
		// --- Attributes
		public final URL url;
		public final String title;
		public final String description;
		
		// --- Methods
		public SearchResult(URL url, String title, String description) {
			this.url = url;
			this.title = title;
			this.description = description;
		}
	}
	
	// --- Constants
	private static final Logger logger = LogManager.getLogger(GooglePlugin.class);
	
	// TODO BLACKLIST de HOST
	
	// --- Attributes
	private final long throttleTime;
	private final boolean async;
	private final List<String> blacklist = new ArrayList<>();
	
	private long dateLastQuery = System.currentTimeMillis();
	
	// --- Methods
	public GooglePlugin(String name, Properties props) {
		super(name, props);
		
		this.async = Boolean.parseBoolean(props.getProperty("search.async", "false"));
		this.throttleTime = Utilities.getInt(props.getProperty("search.throttleTime"), 10000);
		String blacklist = props.getProperty("search.blacklist", "");
		String words[] = blacklist.split("\\s+");
		for (String word : words) {
			this.blacklist.add(word.toLowerCase());
		}
	}
	
	@Override
	public void onEnable() throws Exception {
		super.onEnable();
		logger.debug("Asynchronous HTTP(S) queries : " + this.async);
		
		super.addMessageListener(null, null, this);
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		
		super.removeMessageListener(this);
	}
	
	@Override
	public void onMessage(final IrcMessageEvent event) {
		// Parse URL
		final URI uri = HttpUtilities.parseURI(event.getMessage());
		if (uri != null) {
			// Creating
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					String content = HttpUtilities.sendGetRequest(uri);
					
					// Display the title of the HTML document
					if (content != null) {
						String title = HtmlUtilities.extractTitle(content, true);
						if (title != null) {
							GooglePlugin.super.ircSendMessage(event.getChannel(), "Title : " + title);
						} else {
							logger.debug("No title found in HTML response from URL " + uri.toString());
						}
					}
				}
			};
			
			// Execute query (a/)synchronously
			if (this.async) {
				Thread thread = new Thread(runnable);
				thread.setName("HTTP(S) query : " + uri.toString());
				thread.start();
			} else {
				runnable.run();
			}
		}
	}
	
	@Override
	public void onCommand(String channel, String sender, String login, String hostname, String command) {
		this.doGoogleSearch(channel, sender, command);
	}
	
	/** Performs a search via Google and sends the first result in the <code>target</code> channel */
	private void doGoogleSearch(final String target, final String sender, final String query) {
		// Check allowed search
		String blacklist = this.isBlacklisted(query);
		if (blacklist != null) {
			GooglePlugin.this.ircSendMessage(target, "Tu peux te mettre cette recherche là où je pense " + sender + "...");
			return;
		}
		
		synchronized (this) {
			long time = System.currentTimeMillis();
			if (time - this.dateLastQuery > this.throttleTime) {
				// Work
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						final List<SearchResult> results = new ArrayList<>();
						if(GooglePlugin.googleSearch(results, query, 0)) {
							if (!results.isEmpty()) {
								SearchResult result = results.get(0);
								
								GooglePlugin.this.ircSendMessage(target, "Google : " + result.title + " => " + result.url.toExternalForm());
							}
						}
					}
				};
				
				// Execute job
				if (this.async) {
					Thread thread = new Thread(runnable);
					thread.setName("Google Search : " + query);
					thread.start();
				} else {
					runnable.run();
				}
				this.dateLastQuery = time;
			} else {
				//GooglePlugin.this.ircSendMessage(target, "Je ne veux pas faire de nouvelle recherche tout de suite, reessayez d'ici peu.");
			}
		}
	}
	
	/** Performs a Google query
	 * @param results Collection in which to store
	 * @param query
	 * @param start Index of the results
	 * @return boolean If query was made successfully (but there still may be no results) */
	private static boolean googleSearch(Collection<SearchResult> results, String query, int start) {
		boolean success = true;
		
		// FIXME Ensure variable query is correctly sanitized
		
		// Create URI
		//URI uri = HttpUtilities.parseURI("https://www.google.fr/search?&start=" + start + "&q=" + query);
		URI uri = null;
		try {
			uri = HttpUtilities.parseURI("https://ajax.googleapis.com/ajax/services/search/web?v=1.0&rsz=1&hl=fr&q=" + URLEncoder.encode(query, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			logger.warn("Unsupported charset", e);
		}
		if (uri != null) {
			// Request content
			String content = HttpUtilities.sendGetRequest(uri);
			if (content != null) {
				// Parse content as JSON
				try (JsonReader rdr = Json.createReader(new StringReader(content))) {
					JsonObject obj = rdr.readObject();
					JsonObject jsonResponseData = obj.getJsonObject("responseData");
					JsonArray jsonResults = jsonResponseData.getJsonArray("results");
					for (JsonObject jsonResult : jsonResults.getValuesAs(JsonObject.class)) {
						URL url = new URL(jsonResult.getString("unescapedUrl"));
						String title = jsonResult.getString("titleNoFormatting");
						String description = jsonResult.getString("content");
						SearchResult result = new SearchResult(url, title, description);
						
						results.add(result);
					}
					success = true;
				} catch (MalformedURLException e) {
					logger.error("Bad URL in Google search result : plugin may be outdated.", e);
				}
			}
		}
		
		return success;
	}
	
	/** Checks if a query contains blacklisted words
	 * @param query
	 * @return <code>null</code> or a blacklisted word */
	private String isBlacklisted(String query) {
		query = query.toLowerCase();
		for (String s : this.blacklist) {
			if (query.contains(s)) {
				return s;
			}
		}
		return null;
	}
}
