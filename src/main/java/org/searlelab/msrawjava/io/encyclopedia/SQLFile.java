package org.searlelab.msrawjava.io.encyclopedia;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.searlelab.msrawjava.Logger;

public abstract class SQLFile {
	public static final String VERSION_STRING="version";

	public Connection getConnection(File f) throws IOException {
		Connection c=null;
		try {
			Class.forName("org.sqlite.JDBC");
			c=DriverManager.getConnection("jdbc:sqlite:"+f.getAbsolutePath());
			c.setAutoCommit(false);
			return c;
		} catch (Exception e) {
			Logger.errorLine(e.getClass().getName()+": "+e.getMessage());
			throw new IOException("Error reading database file: "+f.getAbsolutePath(), e);
		}
	}

	public final boolean doesTableExist(File file,  String table) throws IOException, SQLException {
		Connection connection = getConnection(file);
		try {
			return doesTableExist(connection, table);
		} finally {
			connection.close();
		}
	}

	public final boolean doesTableExist(Connection c, String table) throws IOException, SQLException {
		Statement s=c.createStatement();
		try {
			ResultSet rs=s.executeQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '"+table+"'");
			while (rs.next()) {
				int count=rs.getInt(1);
				return count>0;
			}
			return false;
		} finally {
			s.close();
		}
	}

	public final boolean doesColumnExist(File f, String table, String column) throws IOException, SQLException {
		Connection c=getConnection(f);
		try {
			return doesColumnExist(c, table, column);
		} finally {
			c.close();
		}
	}

	/**
	 *
	 * @param c : an open connection. n.b. this method will not close the connection
	 * @param table
	 * @param column
	 * @return whether the column exists in said table
	 * @throws SQLException
	 */
	protected final boolean doesColumnExist(Connection c, String table, String column) throws SQLException {
		Statement s=c.createStatement();
		try {
			ResultSet rs=s.executeQuery("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = '"+table+"'");
			while (rs.next()) {
				String statement=rs.getString(1);
				if (statement.toLowerCase().indexOf(" "+column.toLowerCase()+" ")>=0) {
					return true;
				}
				if (statement.toLowerCase().indexOf("\n"+column.toLowerCase()+" ")>=0) {
					return true;
				}
			}
			return false;
		} finally {
			s.close();
		}
	}
}
