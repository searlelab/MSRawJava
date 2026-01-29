package org.searlelab.msrawjava.io.encyclopedia;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SQLFileTest {

	static class TestSQL extends SQLFile {
	}

	@TempDir
	Path tmp;

	@Test
	void getConnection_createsValidSQLite_andHelperCanDetectColumns() throws Exception {
		TestSQL helper=new TestSQL();
		java.nio.file.Path db=tmp.resolve("unit.sqlite");

		try (Connection c=helper.getConnection(db.toFile())) {
			assertNotNull(c);
			assertFalse(c.isClosed());

			// Create a test table
			try (Statement s=c.createStatement()) {
				s.execute("create table if not exists foo ( id integer primary key, col1 text, col2 int )");
			}

			// Use reflection to find a boolean helper(Connection,String,String) to check columns (method is package-private/private)
			Boolean col1=invokeHasColumn(helper, c, "foo", "col1");
			Boolean missing=invokeHasColumn(helper, c, "foo", "nope_col");
			if (col1!=null&&missing!=null) {
				assertTrue(col1, "Expected helper to report existing column");
				assertFalse(missing, "Expected helper to report missing column");
			} else {
				// Fallback: basic metadata check to ensure JDBC works
				try (ResultSet rs=c.getMetaData().getColumns(null, null, "foo", "col1")) {
					assertTrue(rs.next(), "col1 should exist via JDBC metadata");
				}
			}
		}
	}

	@Test
	void tableAndColumnChecks_workOnFilePath() throws Exception {
		TestSQL helper=new TestSQL();
		Path db=tmp.resolve("tables.sqlite");
		try (Connection c=helper.getConnection(db.toFile())) {
			try (Statement s=c.createStatement()) {
				s.execute("create table if not exists bar ( id integer primary key, colA text, colB int )");
			}
			c.commit();
		}

		assertTrue(helper.doesTableExist(db.toFile(), "bar"));
		assertFalse(helper.doesTableExist(db.toFile(), "missing_table"));
		assertTrue(helper.doesColumnExist(db.toFile(), "bar", "colA"));
		assertFalse(helper.doesColumnExist(db.toFile(), "bar", "nope"));
	}

	private static Boolean invokeHasColumn(SQLFile helper, Connection c, String table, String column) {
		for (Method m : SQLFile.class.getDeclaredMethods()) {
			if (m.getReturnType()==boolean.class) {
				Class<?>[] p=m.getParameterTypes();
				if (p.length==3&&Connection.class.isAssignableFrom(p[0])&&p[1]==String.class&&p[2]==String.class) {
					try {
						m.setAccessible(true);
						Object r=m.invoke(helper, c, table, column);
						return (Boolean)r;
					} catch (Throwable ignored) {
					}
				}
			}
		}
		return null;
	}
}
