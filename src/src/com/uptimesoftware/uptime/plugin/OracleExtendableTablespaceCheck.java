package com.uptimesoftware.uptime.plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import oracle.jdbc.pool.OracleDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.PluginWrapper;

import com.uptimesoftware.uptime.plugin.api.Extension;
import com.uptimesoftware.uptime.plugin.api.Plugin;
import com.uptimesoftware.uptime.plugin.api.PluginMonitor;
import com.uptimesoftware.uptime.plugin.monitor.MonitorState;
import com.uptimesoftware.uptime.plugin.monitor.Parameters;
import com.uptimesoftware.uptime.plugin.monitor.PluginMonitorVariable;

/**
 * Oracle Extendable Tablespace Check
 * 
 * @author uptime software
 */
public class OracleExtendableTablespaceCheck extends Plugin {

	/**
	 * Constructor - a plugin wrapper.
	 * 
	 * @param wrapper
	 */
	public OracleExtendableTablespaceCheck(PluginWrapper wrapper) {
		super(wrapper);
	}

	/**
	 * A nested static class which has to extend PluginMonitor.
	 * 
	 * Functions that require implementation :
	 * 1) The monitor function will implement the main functionality and should set the monitor's state and result
	 * message prior to completion.
	 * 2) The setParameters function will accept a Parameters object containing the values filled into the monitor's
	 * configuration page in Up.time.
	 */
	@Extension
	public static class UptimeOracleExtendableTablespaceCheck extends PluginMonitor {
		// Logger object.
		private static final Logger LOGGER = LoggerFactory.getLogger(UptimeOracleExtendableTablespaceCheck.class);

		// Constants
		private final static int TIMEOUT_SECONDS = 60;
		private final static String THIN_DRIVER = "thin";
		// The long SQL query to execute.
		private final static String THE_SQL_QUERY = "select nvl(b.tablespace_name, nvl(a.tablespace_name,'UNKOWN'))"
				+ " Tablespace_Name, kbytes_alloc Available_KB, kbytes_alloc-nvl(kbytes_free,0) Used_KB,"
				+ " nvl(kbytes_free,0) Free_KB,to_char(((kbytes_alloc-nvl(kbytes_free,0)) / kbytes_alloc)*100,999.99)||'%'"
				+ " PCT_used FROM (select sum(bytes)/1024 Kbytes_free,tablespace_name FROM sys.dba_free_space group by tablespace_name)"
				+ " a,(select sum(bytes)/1024 Kbytes_alloc, tablespace_name from sys.dba_data_files group by tablespace_name"
				+ " UNION ALL SELECT sum(bytes)/1024 Kbytes_alloc, tablespace_name"
				+ " FROM sys.dba_temp_files group by tablespace_name )b where a.tablespace_name (+) = b.tablespace_name"
				+ " ORDER BY Tablespace_Name";

		// See definition in .xml file for plugin. Each plugin has different number of input/output parameters.
		// [Input]
		String hostname;
		int port;
		String username;
		String password;
		String sid;

		/**
		 * The setParameters function will accept a Parameters object containing the values filled into the monitor's
		 * configuration page in Up.time.
		 * 
		 * @param params
		 *            Parameters object which contains inputs.
		 */
		@Override
		public void setParameters(Parameters params) {
			LOGGER.debug("Step 1 : Setting parameters.");
			// [Input]
			hostname = params.getString("hostname");
			port = params.getInt("port");
			username = params.getString("username");
			password = params.getString("password");
			sid = params.getString("sid");
		}

		/**
		 * The monitor function will implement the main functionality and should set the monitor's state and result
		 * message prior to completion.
		 */
		@Override
		public void monitor() {
			LOGGER.debug("Step 2 : Connect to the database with the given parameters.");
			Connection connection = getRemoteConnection(THIN_DRIVER, hostname, port, sid, username, password,
					TIMEOUT_SECONDS);

			LOGGER.debug("Error handling 1 : If connecting fails, change monitor state to CRIT and set an error message.");
			if (connection == null) {
				setStateAndMessage(MonitorState.CRIT, "Could not connect to database, check monitor settings.");
				// connection is null. Plugin should stop here.
				return;
			}

			LOGGER.debug("Step 3 : Create a PreparedStatement for sending parameterized SQL statements to the database.");
			PreparedStatement preparedStatement = prepareStatement(connection, THE_SQL_QUERY);

			LOGGER.debug("Error handling 2 : If creating statement fails, set monitor state CRIT and set an error message.");
			if (preparedStatement == null) {
				setStateAndMessage(MonitorState.CRIT, "Could not get prepared statement, check connection object.");
				// preparedStatement is null. Plugin should stop here.
				return;
			}

			LOGGER.debug("Step 4 : Preparing statement was successful. Execute the prepared statement and get result set.");
			ResultSet resultSet = getResultSet(preparedStatement);

			LOGGER.debug("Error handling 3 : If getting result set fails, set monitor state CRIT and set an error message.");
			// Although executeQuery() never returns null according to JDBC API, just making sure.
			if (resultSet == null) {
				setStateAndMessage(MonitorState.CRIT, "Could not get result set, check preparedStatement object.");
				// resultSet is null. Plugin should stop here.
				return;
			}

			LOGGER.debug("Step 5 : Getting a result set was successful. Extract result from the result set and set output.");
			HashMap<String, LinkedList<String>> output = extractFromResultSet(resultSet);

			LOGGER.debug("Step 6 : Split the result by one or more space(s) and add them to output");
			// Need to get rid of '%' char in the end of percent String.
			String storePercent = "";
			for (Entry<String, LinkedList<String>> entry : output.entrySet()) {
				if (entry.getValue().size() != 4) {
					setStateAndMessage(MonitorState.CRIT, "Wrong number of items in " + entry.getKey());
					return;
				}
				// Creating Ranged-Type PluginMonitorVariable.
				PluginMonitorVariable availableSpace = new PluginMonitorVariable();
				availableSpace.setName("available");
				availableSpace.setObjectName(entry.getKey() + ".available");
				availableSpace.setValue(entry.getValue().removeFirst());
				addVariable(availableSpace);
				PluginMonitorVariable usedSpace = new PluginMonitorVariable();
				usedSpace.setName("used");
				usedSpace.setObjectName(entry.getKey() + ".used");
				usedSpace.setValue(entry.getValue().removeFirst());
				addVariable(usedSpace);
				PluginMonitorVariable freeSpace = new PluginMonitorVariable();
				freeSpace.setName("free");
				freeSpace.setObjectName(entry.getKey() + ".free");
				freeSpace.setValue(entry.getValue().removeFirst());
				addVariable(freeSpace);
				PluginMonitorVariable percentSpace = new PluginMonitorVariable();
				storePercent = entry.getValue().removeFirst();
				percentSpace.setName("percent");
				percentSpace.setObjectName(entry.getKey() + ".percent");
				// Delete % sign in the end of percentSpace value.
				percentSpace.setValue(storePercent.substring(0, storePercent.length() - 1));
				addVariable(percentSpace);
			}

			LOGGER.debug("Step 7 : close all (connection, preparedStatement, resultSet).");
			closeAll(connection, preparedStatement, resultSet);

			LOGGER.debug("Step 8 : Everything ran okay. Set monitor state to OK");
			setStateAndMessage(MonitorState.OK, "Monitor successfully ran.");

		}

		/**
		 * Private helper function to get database connection.
		 * 
		 * @param driverType
		 *            DB Type. (thin, oci, kprb)
		 * @param hostname
		 *            Name of the host.
		 * @param port
		 *            Port number.
		 * @param sid
		 *            Service ID
		 * @param username
		 *            Name of user.
		 * @param password
		 *            Password
		 * @param timeout
		 *            Timeout in seconds
		 * @return Database connection object.
		 */
		private Connection getRemoteConnection(String driverType, String hostname, int port, String sid,
				String username, String password, int timeout) {
			Connection connection = null;
			try {
				OracleDataSource dataSource = new OracleDataSource();
				dataSource.setDriverType(driverType);
				dataSource.setServerName(hostname);
				dataSource.setPortNumber(port);
				dataSource.setServiceName(sid);
				dataSource.setUser(username);
				dataSource.setPassword(password);
				dataSource.setLoginTimeout(timeout);
				// Get connection with the database.
				connection = dataSource.getConnection();
				LOGGER.debug("Make sure connection is still open before moving on.");
				if (connection.isClosed()) {
					setStateAndMessage(MonitorState.CRIT, "Connection is closed.");
				}
			} catch (SQLException e) {
				LOGGER.error("Error getting remote connection.", e);
			}
			return connection;
		}

		/**
		 * Private helper function to get PreparedStatement with given Connection and SQL query.
		 * 
		 * @param connection
		 *            Connection object of a database
		 * @param script
		 *            SQL query to run
		 * @return PreparedStatement object containing the pre-compiled SQL statement.
		 */
		private PreparedStatement prepareStatement(Connection connection, String sqlScript) {
			PreparedStatement preparedStatement = null;
			try {
				preparedStatement = connection.prepareStatement(sqlScript);
			} catch (SQLException e) {
				LOGGER.error("Error while creating PreparedStatement failed : ", e);
			}
			return preparedStatement;
		}

		/**
		 * Private helper function to execute prepared statement and get result set.
		 * 
		 * @param preparedStatement
		 *            PreparedStatement object containing the pre-compiled SQL statement
		 * @return Result set after executing prepared statement. (executeQuery() never returns null according to JDBC
		 *         API)
		 */
		private ResultSet getResultSet(PreparedStatement preparedStatement) {
			ResultSet resultSet = null;
			try {
				resultSet = preparedStatement.executeQuery();
			} catch (SQLException e) {
				LOGGER.error("Error while executing prepared statement : ", e);
			}
			return resultSet;
		}

		/**
		 * Private helper function to extract String result from the given ResultSet.
		 * 
		 * @param rs
		 *            ResultSet object
		 * @return Extracted sql query output in HashMap<String, LinkedList<String>>.
		 */
		private HashMap<String, LinkedList<String>> extractFromResultSet(ResultSet rs) {
			HashMap<String, LinkedList<String>> output = new HashMap<String, LinkedList<String>>();
			try {
				// An object that can be used to get information about the types and properties of the columns in a
				// ResultSet object.
				ResultSetMetaData meta = rs.getMetaData();
				int columnCount = meta.getColumnCount();
				while (rs.next()) {
					// Put each row in HashMap<String, LinkedList<String>>.
					getRowAsString(rs, columnCount, output);
				}
				if (output.isEmpty()) {
					LOGGER.warn("The result is empty.");
				}
			} catch (SQLException e) {
				LOGGER.error("Error while extracting results from the given ResultSet : ", e);
			}
			return output;
		}

		/**
		 * Private helper function to build HashMap<String, LinkedList<String>> output.
		 * 
		 * @param rs
		 *            ResultSet object
		 * @param columnCount
		 *            Number of column count in the ResultSet object.
		 * @return HashMap<String, LinkedList<String>> with sql query output.
		 * @throws SQLException
		 */
		private void getRowAsString(ResultSet rs, int columnCount, HashMap<String, LinkedList<String>> output)
				throws SQLException {
			// index 1 is table name, it should be the key.
			String key = rs.getString(1).trim();
			LinkedList<String> values = new LinkedList<String>();
			// 1-based index
			for (int i = 2; i <= columnCount; i++) {
				values.add(rs.getString(i).trim());
			}
			output.put(key, values);
		}

		/**
		 * Private helper function to close all if they're open.
		 * 
		 * @param connection
		 *            Connection object
		 * @param preparedStatement
		 *            PreparedStatement object
		 * @param resultSet
		 *            ResultSet object
		 */
		private void closeAll(Connection connection, PreparedStatement preparedStatement, ResultSet resultSet) {
			// There are better ways to do it. Fix it later.
			try {
				if (!resultSet.isClosed()) {
					resultSet.close();
				}
				if (!preparedStatement.isClosed()) {
					preparedStatement.close();
				}
				if (!connection.isClosed()) {
					connection.close();
				}
			} catch (SQLException e) {
				LOGGER.error("Error while closing all.", e);
			}
		}
	}
}