package fr.rooobert.energy.rooobot.plugins;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.pircbotx.User;

import com.google.common.collect.ImmutableSortedSet;

import fr.rooobert.energy.rooobot.Plugin;

public class ScanPlugin extends Plugin {
	// --- Constants
	
	// --- Attributes
	
	// --- Methods
	public ScanPlugin(String name, Properties props) {
		super(name, props);
	}
	
	@Override
	public void onEnable() throws Exception {
		super.onEnable();
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
	}
	
	@Override
	public void onCommand(String channel, String sender, String login, String hostname, String message) {
		super.ircSendMessage(channel, "Recherche de clones...");
		
		// Check all users' hostmasks
		Map<String, List<String>> map = new HashMap<>();
		ImmutableSortedSet<User> users = super.ircGetUsers(channel);
		for (User user : users) {
			String host = user.getHostmask();
			String nick = user.getNick();
			
			List<String> hostUsers = map.get(host);
			if (hostUsers == null) {
				hostUsers = new LinkedList<>();
				map.put(host, hostUsers);
			}
			hostUsers.add(nick);
		}
		
		// Check the results
		StringBuffer sbOutput = new StringBuffer();
		for (Entry<String, List<String>> e : map.entrySet()) {
			String host = e.getKey();
			List<String> hostUsers = e.getValue();
			
			// Output a message for each duplicated hostmask
			sbOutput.setLength(0);
			sbOutput.append("* ");
			sbOutput.append(host);
			sbOutput.append(" -> ");
			
			if (hostUsers.size() > 1) {
				boolean first = true;
				for (String username : hostUsers) {
					if (!first) {
						sbOutput.append(" | ");
					} else {
						first = false;
					}
					sbOutput.append(username);
				}
				super.ircSendMessage(channel, sbOutput.toString());
			}
		}
		
		// Memory cleanup
		map.clear();
		sbOutput.setLength(0);
		sbOutput = null;
	}
}
