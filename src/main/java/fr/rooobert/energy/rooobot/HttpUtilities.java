package fr.rooobert.energy.rooobot;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import fr.rooobert.energy.rooobot.plugins.GooglePlugin;

public class HttpUtilities {
	// --- Constants
	private static final Logger logger = LogManager.getLogger(GooglePlugin.class);
	
	// --- Attributes
	
	// --- Methods
	/** Performs an HTTP(S) GET request and returns the HTML response. 
	 * @param uri
	 * @return HTML content or <code>null</code> in case of error */
	public static String sendGetRequest(URI uri) {
		String content = null;
		
		// Create get request
		HttpGet get = new HttpGet(uri);
		get.addHeader("DNT", "1");
		
		// Send request
		HttpClient httpClient = HttpClients.createMinimal();
		HttpResponse response = null;
		try {
			response = httpClient.execute(get);
			logger.debug("Query " + uri.toString() + " => " + response.getStatusLine());
			
			// Check status code
			final int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode == 200) {
				// Read resulting HTML body
				HttpEntity entity = response.getEntity();
				content = EntityUtils.toString(entity);
			} else {
				logger.warn("Get request failed with status code =" + statusCode);
			}
		} catch (IOException e) {
			logger.error("Error while performing HTTP(S) request : " + e.getMessage(), e);
		}
		
		return content;
	}
	
	/** Parse a URI from an input text
	 * @param text
	 * @return URI or <code>null</code> if no URI could be parsed */
	public static URI parseURI(String text) {
		URI uri = null;
		try {
			URL url = new URL(text);
			uri = url.toURI();
		} catch (MalformedURLException | URISyntaxException e) {
			//logger.trace("Invalid URL from : " + text + " : " + e.getMessage());
		}
		return uri;
	}
}
