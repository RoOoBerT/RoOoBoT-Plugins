package fr.rooobert.energy.rooobot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class HtmlUtilities {
	// --- Constants
	private static final Logger logger = LogManager.getLogger(HtmlUtilities.class);
	private static final Pattern PATTERN_TITLE = Pattern.compile("<title>([^<>]+)</title>");
	
	// --- Attributes
	
	// --- Methods
	/** Extract the title of a page from an HTML content
	 * @param content
	 * @param <code>true</code> if the result should be HTML unescaped
	 * @return The title of the page */
	public static String extractTitle(String content, boolean htmlUnescape) {
		String title = null;
		
		// Search for the title of the page
		Matcher matcher = PATTERN_TITLE.matcher(content.replace("\n", " ").replace("\r", ""));
		if (matcher.find()) {
			title = matcher.group(1);
			if (htmlUnescape) {
				title = StringEscapeUtils.unescapeHtml4(title);
			}
		}
		return title;
	}
}
