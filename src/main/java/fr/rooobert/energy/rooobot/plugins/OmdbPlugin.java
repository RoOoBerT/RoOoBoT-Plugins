package fr.rooobert.energy.rooobot.plugins;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import fr.rooobert.energy.rooobot.HttpUtilities;
import fr.rooobert.energy.rooobot.Plugin;
import fr.rooobert.energy.rooobot.Utilities;

public class OmdbPlugin extends Plugin {
	// --- Types
	/** OMDB Search Result */
	public static class SearchResult {
		// --- Attributes
		public final String title;
		public final int year;
		public final String director;
		public final String genre;
		public final String plot;
		
		// --- Methods
		protected SearchResult(String title, int year, String director,
				String genre, String plot) {
			super();
			this.title = title;
			this.year = year;
			this.director = director;
			this.genre = genre;
			this.plot = plot;
		}

		@Override
		public String toString() {
			return title + " (" + year
					+ ") - " + director + " - " + genre + " : "
					+ plot;
		}
	}
	
	// --- Constantes
	private static final Logger logger = LogManager.getLogger(OmdbPlugin.class);
	private static final Pattern PATTERN_YEAR = Pattern.compile(Pattern.quote("year:") + "([1-2][0-9]{3})");
	
	// --- Attributs
	private long dateLastQuery;
	private long throttleTime;

	// --- Methodes
	public OmdbPlugin(String name, Properties props) {
		super(name, props);
		
		this.throttleTime = Utilities.getInt(props.getProperty("search.throttleTime"), 10000);
	}
	
	@Override
	public void onCommand(String channel, String sender, String login, String hostname, String message) {
		Integer year = null;
		String search = null;
		
		Matcher matcher = PATTERN_YEAR.matcher(message);
		if (matcher.find()) {
			year = Integer.valueOf(matcher.group(1));
			search = message.substring(matcher.end()).trim();
		} else {
			search = message;
		}
		
		if (!search.trim().isEmpty()) {
			this.doMovieSearch(channel, search, year);
		} else {
			this.doPrintHelp(channel);
		}
	}

	private void doPrintHelp(String channel) {
		// TODO Auto-generated method stub
		
	}
	
	/** Performs a search via Omdb and outputs the first result in the <code>target</code> channel
	 * @param target Channel
	 * @param title
	 * @param year Year of production of the movie (or <code>null</code>) */
	private void doMovieSearch(final String target, final String title, final Integer year) {
		
		synchronized (this) {
			long time = System.currentTimeMillis();
			if (time - this.dateLastQuery > this.throttleTime) {
				// Work
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
						final List<SearchResult> results = new ArrayList<>();
						if(OmdbPlugin.movieSearch(results, title, year)) {
							if (!results.isEmpty()) {
								SearchResult result = results.get(0);
								
								OmdbPlugin.this.ircSendMessage(target, "OMDB : " + result.toString());
							} else {
								OmdbPlugin.this.ircSendMessage(target, "OMDB : Pas de resultats pour cette recherche");
							}
						}
					}
				};
				
				// Execute job
				Thread thread = new Thread(runnable);
				thread.setName("OMDB Search : " + title);
				thread.start();
				this.dateLastQuery = time;
			} else {
				//GooglePlugin.this.ircSendMessage(target, "Je ne veux pas faire de nouvelle recherche tout de suite, reessayez d'ici peu.");
			}
		}
	}
	
	/** Performs an OMDB query
	 * @param results Collection in which to store results
	 * @param query
	 * @param start Index of the results
	 * @return boolean If query was made successfully (but there still may be no results) */
	protected static boolean movieSearch(Collection<SearchResult> results, String query, Integer year) {
		boolean success = true;
		
		// Create URI
		URI uri = null;
		try {
			uri = HttpUtilities.parseURI("http://www.omdbapi.com/?v=1&plot=short&r=json"
					+ (year == null ? "" : "&y=" + year.intValue())
					+ "&t=" + URLEncoder.encode(query, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			logger.warn("Unsupported charset", e);
		}
		if (uri != null) {
			// Parse content as JSON
			try (JsonReader rdr = HttpUtilities.sendGetRequestJson(uri)) {
				JsonObject obj = rdr.readObject();
				
				if (obj != null && obj.containsKey("Title")) {
					// Retrieve search result data
					String title = obj.getString("Title", "");
					int y = Utilities.getInt(obj.getString("Year", "0"), 0);
					String director = obj.getString("Director", "");
					String genre = obj.getString("Genre", "");
					String plot = obj.getString("Plot", "");
					
					// Add the result to the list
					SearchResult result = new SearchResult(title, y, director, genre, plot);
					results.add(result);
				}
				
				success = true;
			}
		}
		
		return success;
	}
}

