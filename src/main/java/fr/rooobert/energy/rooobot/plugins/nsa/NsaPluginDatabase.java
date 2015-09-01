package fr.rooobert.energy.rooobot.plugins.nsa;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class NsaPluginDatabase {
	// --- Constants
	private static Logger logger = LogManager.getLogger(NsaPluginDatabase.class);
	
	// --- Attributes
	private final Connection connection;
	
	// --- Methods
	public NsaPluginDatabase(Connection connection) throws Exception {
		this.connection = connection;
		
		try {
			this.init();
		} catch (Exception e) {
			throw new Exception("SQL exception : " + e.getMessage(), e);
		}
	}
	
	private void init() throws Exception {
		// Initialize tables
		try (Statement statement = this.connection.createStatement()) {
			//statement.setQueryTimeout(5);
			
			// === TABLE MESSAGE ===
			statement.executeUpdate("create table if not exists message (id integer, sender string, datetime integer, message string)");
			
			// = Contents =
			/*ResultSet rs = statement.executeQuery("select * from user");
			while(rs.next()) {
				User user = new User(rs.getString("name"));
				user.addScore(rs.getInt("score"));

				this.users.put(user.getName(), user);
			}*/
		} catch (SQLException e) {
			throw e;
		}
	}
	
	public synchronized void saveMessage(Message message) throws SQLException {
		try (PreparedStatement ps = this.connection.prepareStatement("insert into message (sender, datetime, message) values (?, ?, ?)")) {
			// === TABLE MESSAGE ===
			ps.setString(1, message.sender);
			ps.setLong(2, message.time.getTime());
			ps.setString(3, message.message);
			ps.executeUpdate();
			
			logger.debug("Saved message to database : " + message);
		} catch (SQLException e) {
			throw e;
		}
	}
	
	public void close() {
		// TODO save database
	}
	
	// --- Types
	public static class Message {
		public final String sender;
		public final Date time;
		public final String message;
		
		public Message(String sender, Date time, String message) {
			this.sender = sender;
			this.time = time;
			this.message = message;
		}
		
		@Override
		public String toString() {
			return "Message[sender=" + this.sender + ", date=" + time +"]";
		}
	}
}
