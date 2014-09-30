package com.uptimesoftware.uptime.plugin.test;

import static org.junit.Assert.assertFalse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedList;

import org.junit.Test;

import com.uptimesoftware.uptime.plugin.OracleExtendableTablespaceCheck.UptimeOracleExtendableTablespaceCheck;

/**
 * Ignore SLF4J error messages. The Testcase class is for reducing manual testing time and to test client-side plugin
 * logic only.
 * 
 * @author Uptime Software
 */
public class OracleExtendableTablespaceCheckTest {

	// args example : "thin", "[your_hostname]", 1521, "ORCL", "[your_username]", "[your_pw]", 60
	private Object[] oracleDBParams = new Object[] { "thin", "lab-ora12c.css.local", 1521, "ORCL", "css16", "uptime",
			60 };

	private String sqlQuery = "select nvl(b.tablespace_name, nvl(a.tablespace_name,'UNKOWN'))"
			+ " Tablespace_Name, kbytes_alloc Available_KB, kbytes_alloc-nvl(kbytes_free,0) Used_KB,"
			+ " nvl(kbytes_free,0) Free_KB,to_char(((kbytes_alloc-nvl(kbytes_free,0)) / kbytes_alloc)*100,999.99)||'%'"
			+ " PCT_used FROM (select sum(bytes)/1024 Kbytes_free,tablespace_name FROM sys.dba_free_space group by tablespace_name)"
			+ " a,(select sum(bytes)/1024 Kbytes_alloc, tablespace_name from sys.dba_data_files group by tablespace_name"
			+ " UNION ALL SELECT sum(bytes)/1024 Kbytes_alloc, tablespace_name"
			+ " FROM sys.dba_temp_files group by tablespace_name )b where a.tablespace_name (+) = b.tablespace_name"
			+ " ORDER BY Tablespace_Name";

	@Test
	public void getRemoteConnectionTest() {
		assertFalse(invokeGetRemoteConnection() == null);
	}

	@Test
	public void prepareStatementTest() {
		assertFalse(invokePrepareStatement() == null);
	}

	@Test
	public void getResultSetTest() {
		assertFalse(invokeGetResultSet() == null);
	}

	@Test
	public void extractResultSetTest() {
		HashMap<String, LinkedList<String>> output = invokeExtractFromResultSet();
		LinkedList<String> values = new LinkedList<String>();
		for (String key : output.keySet()) {
			values = output.get(key);
			assertFalse(key == null || key.equals(""));
			assertFalse(values.size() != 4);
			for (String value : values) {
				// Last element contains "%" in the end of index.
				value = value.contains("%") ? value.substring(0, value.length() - 1) : value;
				// value should only contain digits or digits with dot.
				assertFalse(!value.matches("[0-9]*.?[0-9]*"));
			}
		}
	}

	/**
	 * Invoke private getRemoteConnection method by using Java Reflection.
	 * 
	 * @return Connection to Oracle DB.
	 */
	private Connection invokeGetRemoteConnection() {
		Connection connection = null;
		try {
			// getRemoteConnection(String driverType, String hostname, int port, String sid, String username, String
			// password, int timeout)
			Method method = UptimeOracleExtendableTablespaceCheck.class.getDeclaredMethod("getRemoteConnection",
					new Class[] { String.class, String.class, int.class, String.class, String.class, String.class,
							int.class });
			method.setAccessible(true);
			connection = (Connection) method.invoke(UptimeOracleExtendableTablespaceCheck.class.newInstance(),
					oracleDBParams);
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			System.err.println(e);
		}
		return connection;
	}

	/**
	 * Invoke private prepareStatement method by using Java Reflection.
	 * 
	 * @return PreparedStatement object.
	 */
	private PreparedStatement invokePrepareStatement() {
		PreparedStatement preparedStatement = null;
		try {
			Method method = UptimeOracleExtendableTablespaceCheck.class.getDeclaredMethod("prepareStatement",
					new Class[] { Connection.class, String.class });
			method.setAccessible(true);
			preparedStatement = (PreparedStatement) method.invoke(
					UptimeOracleExtendableTablespaceCheck.class.newInstance(), new Object[] {
							invokeGetRemoteConnection(), sqlQuery });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return preparedStatement;
	}

	/**
	 * Invoke private getResultSet method by using Java Reflection.
	 * 
	 * @return ResultSet object.
	 */
	private ResultSet invokeGetResultSet() {
		ResultSet resultSet = null;
		try {
			Method method = UptimeOracleExtendableTablespaceCheck.class.getDeclaredMethod("getResultSet",
					new Class[] { PreparedStatement.class });
			method.setAccessible(true);
			resultSet = (ResultSet) method.invoke(UptimeOracleExtendableTablespaceCheck.class.newInstance(),
					new Object[] { invokePrepareStatement() });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return resultSet;
	}

	/**
	 * Invoke private extractFromResultSet method by using Java Reflection.
	 * 
	 * @return String result.
	 */
	@SuppressWarnings("unchecked")
	private HashMap<String, LinkedList<String>> invokeExtractFromResultSet() {
		HashMap<String, LinkedList<String>> output = new HashMap<String, LinkedList<String>>();
		try {
			Method method = UptimeOracleExtendableTablespaceCheck.class.getDeclaredMethod("extractFromResultSet",
					new Class[] { ResultSet.class });
			method.setAccessible(true);
			// Not going to do ALL type cast check by hands. Just nope. Using @SuppressWarnings.
			output = (HashMap<String, LinkedList<String>>) method.invoke(
					UptimeOracleExtendableTablespaceCheck.class.newInstance(), new Object[] { invokeGetResultSet() });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			System.err.println(e);
		}
		return output;
	}
}
