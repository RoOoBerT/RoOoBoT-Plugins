package fr.rooobert.energy.rooobot.plugins;

import java.util.Properties;

import fr.rooobert.energy.rooobot.Plugin;

public class TestPlugin extends Plugin {
	// --- Constants
	
	// --- Attributes
	
	// --- Methods
	public TestPlugin(String name, Properties props) {
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
		super.ircSendMessage(channel, "Test réussi " + sender);
	}
}
