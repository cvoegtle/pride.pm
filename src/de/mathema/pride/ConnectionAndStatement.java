package de.mathema.pride;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import de.mathema.pride.util.PreparedStatementLogger;

class ConnectionAndStatement implements PreparedOperationI {
	final Database database;
	final Connection con;
	final Statement stmt;
	final String statementContent;
	private PreparedStatementLogger logger;
	
	ConnectionAndStatement(Database database, String statementContent, boolean prepared) throws SQLException {
		this.database = database;
        this.statementContent = statementContent;
        con = database.getConnection();
        if (prepared) {
        	stmt = con.prepareStatement(statementContent);
            this.logger = new PreparedStatementLogger(database, statementContent);
        }
        else {
            stmt = con.createStatement();
        	database.sqlLog(statementContent);
        }
        if (database.getStatementTimeout() != null)
            stmt.setQueryTimeout(database.getStatementTimeout());
	}

	ConnectionAndStatement(Database database, String statementContent, Object... params) throws SQLException, ReflectiveOperationException {
		this(database, statementContent, params.length > 0);
    	for (int i = 0; i < params.length; i++) {
			Method setter = PreparedStatementAccess.getAccessMethod(params[i].getClass());
			setBindParameter(setter, i+1, params[i]);
    	}
	}
	
	public int executeUpdate() throws SQLException {
		if (isPrepared()) {
			return getStatement().executeUpdate();
		}
		else {
			return stmt.executeUpdate(statementContent);
		}
	}

	public int executeUpdate(String[] autoFieldsForExec) throws SQLException {
		if (isPrepared()) {
			throw new UnsupportedOperationException();
		}
		else {
			return stmt.executeUpdate(statementContent, autoFieldsForExec);
		}
	}

	public ResultSet executeQuery() throws SQLException {
		if (isPrepared()) {
			return getStatement().executeQuery();
		}
		else {
			return stmt.executeQuery(statementContent);
		}
	}

	public boolean execute() throws SQLException {
		if (isPrepared()) {
			return getStatement().execute();
		}
		else {
			return stmt.execute(statementContent);
		}
	}

	private boolean isPrepared() { return (stmt instanceof PreparedStatement); }
	
	public void close() throws SQLException {
        if (stmt != null) stmt.close();
        if (con != null) database.releaseConnection(con);
	}

	public void closeAfterException(Exception x) throws SQLException {
    	if (x instanceof SQLException)
    		database.sqlLogError((SQLException)x);
    	close();
    	database.processSevereButSQLException(x);
	}
	
	@Override
	public Database getDatabase() { return database; }

	@Override
	public PreparedStatement getStatement() { return (PreparedStatement)stmt; }

	public void setBindParameter(Method setter, int parameterIndex, Object preparedValue)
		throws ReflectiveOperationException {
		logger.logBindingAndScroll(preparedValue, parameterIndex);
		setter.invoke(getStatement(), parameterIndex, preparedValue);
	}

	@Override
	public void setBindParameterNull(int parameterIndex, int columnType) throws SQLException {
		logger.logBindingAndScroll("NULL", parameterIndex);
		getStatement().setNull(parameterIndex, columnType);
	}
}
