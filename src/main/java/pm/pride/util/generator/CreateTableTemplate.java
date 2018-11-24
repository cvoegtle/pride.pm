/*******************************************************************************
 * Copyright (c) 2001-2018 The PriDE team
 * All rights reserved. This toolkit and the accompanying materials 
 * are made available under the terms of the GNU Lesser General Public
 * License (LGPL) which accompanies this distribution, and is available
 * at http://pride.sourceforge.net/LGPL.html
 * 
 * Contributors:
 *     Jan Lessner, S&N AG
 *     Matthias Bartels, arvato direct services
 *******************************************************************************/
package pm.pride.util.generator;

/**
 * Generator class to generate entity types from database tables
 *
 * @author <a href="mailto:matthias.bertelsmann@bertelsmann.de">Matthias Bartels</a>
 */
import java.lang.reflect.Field;
import java.sql.*;
import java.util.*;

import pm.pride.RecordDescriptor;


public class CreateTableTemplate {

	// Types of generation work products
	public static final String HYBRID = "-h";
	public static final String BEAN = "-b";
	
	protected String dbDriver;
	protected String dbName;
	protected String user;
	protected String passwd;
	protected String className;
	protected String baseClassName;
	protected String generationType;
	protected String[] tableNames;
	protected ClassLoader classLoader;
	protected List<TableColumn> flatTableColumns;
	protected TableDescription[] tableDesc;
	protected List<TableColumn> flatTableColumnList;

	public CreateTableTemplate(String dbDriver,String dbName,
							   String user, String passwd,
							   String[] tableNames, String className,
							   String generationType, String baseClassName)
		throws SQLException {
		this(dbDriver, dbName, user, passwd, tableNames, className, generationType, baseClassName, ClassLoader.getSystemClassLoader());
	}
	
	public CreateTableTemplate(String dbDriver,String dbName,
							   String user, String passwd,
							   String[] tableNames, String className,
							   String generationType, String baseClassName, ClassLoader classLoader)
		throws SQLException {
		this.dbDriver = dbDriver;
		this.dbName = dbName;
		this.user = user;
		this.passwd = passwd;
		this.tableNames = tableNames;
		this.className = className;
		this.baseClassName = baseClassName;
		this.generationType = generationType;
		this.classLoader = classLoader;
	}

	public RecordDescriptor extractRecordDescriptor(String className) throws SQLException {
		try {
			Class<?> c = Class.forName(className);
			Field f = c.getDeclaredField("red");
			f.setAccessible(true);
			return (RecordDescriptor)f.get(null);
		}
		catch(ClassNotFoundException cnfx) {
			throw new SQLException("Base class " + className + " not found.");
		}
		catch(NoSuchFieldException nsfx) {
			throw new SQLException("Base class " + className + " not suitable. Must have a static member of type RecordDescriptor");
		}
		catch(IllegalAccessException iax) {
			throw new SQLException("RecordDescriptor in base class " + className + " not accessible");
		}
	}
	
	/** Retrieve the fields being mapped by a specified class.
	 * Retrieval is performed by reflection, assuming that the
	 * class contains a static {@link RecordDescriptor} field "red"
	 * which the field names can be retrieved from by
	 * {@link RecordDescriptor#getFieldNames}. If you override the
	 * template method {@link #writeRecordDescriptor} to create
	 * record descriptor definitions differently, make sure to also
	 * adapt this function accordingly.<br>
	 * ATTENTION: This function tries to override standard security restrictions
	 * to access the protected record descriptor member. This may cause
	 * security exceptions.
	 * 
	 * @param className The name of the class to retrieve the mapped fields from
	 * @return The mapped fields
	 */
	public Set<String> extractMappedFields(String className) throws SQLException {
		HashSet<String> fields = new HashSet<>();
		if (className != null) {
			RecordDescriptor desc = extractRecordDescriptor(className);
			String fieldsStr = desc.getFieldNames(null);
			StringTokenizer tokenizer = new StringTokenizer(fieldsStr, ",");
			while (tokenizer.hasMoreTokens())
				fields.add(tokenizer.nextToken());
		}
		return fields;
	}

	/** Retrieve the value type mapped by a specified class.
	 * Retrieval is performed by reflection, assuming that the
	 * class contains a static {@link RecordDescriptor} field "red"
	 * which the value type name can be retrieved from by
	 * {@link RecordDescriptor#getObjectType}.
	 * 
	 * @param className The name of the class to retrieve the value type from
	 * @return The name of the value type
	 */
	public String extractBeanClass(String className) throws SQLException {
		RecordDescriptor desc = extractRecordDescriptor(className);
		return desc.getObjectType().getName();
	}
		
	/** Creates the entity type by successivly calling the
	 * write... functions
	 */
	public String create() throws SQLException {
		Connection con = getDBConnection();
		StringBuffer buffer = new StringBuffer();
		if (con != null) {
			tableDesc = getTableDescription(con);
			flatTableColumnList = flattenTableColumns(tableDesc);
			writePackage(tableDesc, className, baseClassName, generationType, buffer);
			writeHeader(tableDesc, className, baseClassName, generationType, buffer);
			writeConstants(tableDesc, className, baseClassName, generationType, buffer);
			writeRecordDescriptor(tableDesc, className, baseClassName, generationType, buffer);
			writePrimaryKey(tableDesc, className, baseClassName, generationType, buffer);
			writeEntityReference(tableDesc, className, baseClassName, generationType, buffer);
			writeAttributes(tableDesc, className, baseClassName, generationType, buffer);
			writeGetMethods(tableDesc, className, baseClassName, generationType, buffer);
			writeSetMethods(tableDesc, className, baseClassName, generationType, buffer);
			writeFooter(tableDesc, className, baseClassName, generationType, buffer);
            con.close();
            con = null;
		} else {
			buffer.append("Aborting.... due to initialization problem");
		}
		return buffer.toString();
	}

	/** Opens up a database connection accoring to the specified
	 * database driver, database name, user, and password
	 */
	public Connection getDBConnection() {
		try {
			Driver driver = (Driver)Class.forName(dbDriver, true, this.classLoader).newInstance();
			DriverManager.registerDriver(driver);
			return DriverManager.getConnection(dbName, user, passwd);
		} catch (ClassNotFoundException cnfx) {
			System.out.println("Driver not found: " + dbDriver);
		} catch (InstantiationException ix) {
			System.out.println("Can't instantiate driver");
		} catch (IllegalAccessException ix) {
			System.out.println("Driver not accessible");
		} catch (SQLException e) {
			try {
				Driver driver = (Driver)Class.forName(dbDriver, true, this.classLoader).newInstance();		
				Properties props = new Properties();	
				props.put("user", user);
				props.put("password", passwd);
				return driver.connect(dbName, props);
			} catch (Exception ex) {
				System.out.println("Connection failed: " + e.getMessage());
			}
		}
		return null;
	}

	/** Generates a table description */
	public TableDescription[] getTableDescription(Connection con)
		throws SQLException {
		TableDescription[] tableDesc = new TableDescription[tableNames.length];
		for (int i = 0; i < tableNames.length; i++) {
			tableDesc[i] = new TableDescription(con, tableNames[i]);
		}
		return tableDesc;
	}

    /** Prints the package name to standard out */
    public void writePackage (TableDescription[] desc, String className, String baseClassName,
    						  String generationType, StringBuffer buffer) {
        buffer.append(getPackage(className) + "\n");
        buffer.append("\n");
    }

    /** Prints the author tag to standard out, using the current
     * database user and database name as an identificatio
     */
    public void writeAuthor (TableDescription[] desc, String className, String baseClassName,
    						 String generationType, StringBuffer buffer) {
    	if (System.getProperty("user.name") != null)
        	buffer.append(" * @author " + System.getProperty("user.name") + "\n");
        else
			buffer.append(" * @author " + user + "@" + dbName + "\n");
    }

	/** Determine the base class for the class to generate. When generating
	 * a hybrid type, the base class is {@link pm.pride.MappedObject},
	 * for adapters it is {@link pm.pride.ObjectAdapter} and for beans
	 * it is nothing. If a base class is passed, it is takes as is. In case of
	 * bean generation, the base class is assumed to specify the base adapter
	 * class and the baes bean class is extracted by reflection.
	 */
	protected String getBaseClassName(String baseClassName, String generationType)
		throws SQLException {
		if (baseClassName == null) {
			if (generationType.equals(HYBRID))
				return "MappedObject";
			else if (!generationType.equals(BEAN))
				return "ObjectAdapter";
			else
				return null;
		}
		else {
			if (generationType.equals(BEAN))
				return extractBeanClass(baseClassName);
			else
				return baseClassName;
		}
	}
	
    /** Prints the class header to standard out */
    public void writeHeader (TableDescription[] desc, String className, String baseClassName,
    						 String generationType, StringBuffer buffer)
    	throws SQLException {
		if (!generationType.equals(BEAN)) {
            buffer.append("import java.sql.SQLException;" + "\n");
            buffer.append("import pm.pride.*;" + "\n");
            buffer.append("\n");
		}
        buffer.append("/**" + "\n");
        writeAuthor(desc, className, baseClassName, generationType, buffer);
        buffer.append(" */" + "\n");
        buffer.append("public class " + getClassName(className) + " ");
        String actualBaseClassName = getBaseClassName(baseClassName, generationType);
        if (actualBaseClassName != null)
			buffer.append("extends " + actualBaseClassName + " ");
		if (baseClassName == null && (generationType.equals(BEAN) || generationType.equals(HYBRID)))
			buffer.append("implements Cloneable, java.io.Serializable ");
		buffer.append("{\n");
    }

    public String toColumnConstant(TableColumn column) {
    	return "COL_" + column.getNameUpper();
    }
    
    public void writeConstants (TableDescription[] desc, String className,
			   String baseClassName, String generationType, StringBuffer buffer)
		throws SQLException {
		if (generationType.equals(BEAN))
			return;
		buffer.append("    public static final String TABLE = \"" + getTableName(tableNames) + "\";\n");
		Set<String> baseClassFields = extractMappedFields(baseClassName);
        for (TableColumn tableColumn: flatTableColumnList) {
			if (baseClassFields.contains(tableColumn.getName()))
				continue;
			buffer.append("    public static final String " + toColumnConstant(tableColumn) + " = \"");
			String qualifiedColumnName = (desc.length > 1) ?
            	tableColumn.getTableName() + "." + tableColumn.getName() :
            	tableColumn.getName();
			buffer.append(qualifiedColumnName + "\";\n");
        }
        buffer.append("\n");
    }
    
    /** Prints the record descriptor and its access function to standard out */
    public void writeRecordDescriptor (TableDescription[] desc, String className,
    								   String baseClassName, String generationType, StringBuffer buffer)
    	throws SQLException {
		if (generationType.equals(BEAN))
			return;

		Set<String> baseClassFields = extractMappedFields(baseClassName);
		buffer.append("    protected static final RecordDescriptor red = new RecordDescriptor" + "\n");
		buffer.append("        (");
		buffer.append(getClassName(generationType.equals(HYBRID) ? className : generationType));
		buffer.append(".class, \"" + getTableName(tableNames) + "\", ");
		buffer.append(baseClassName != null ? baseClassName + ".red" : "null");
		buffer.append(", new String[][] {" + "\n");
		
		for (TableColumn tableColumn: flatTableColumnList) {
            if (baseClassFields.remove(tableColumn.getName()))
            	continue;
            StringBuffer sb = new StringBuffer();
            sb.append("            { ");
            sb.append(toColumnConstant(tableColumn));
            sb.append(",   \"get");
            sb.append(tableColumn.getNameCamelCaseFirstUp());
            sb.append("\",   \"set");
            sb.append(tableColumn.getNameCamelCaseFirstUp());
            sb.append("\" },");
            buffer.append(sb.toString() + "\n");
			
		}
		
        buffer.append("        });" + "\n\n");
        buffer.append("    protected RecordDescriptor getDescriptor() { return red; }" + "\n");
        buffer.append("\n");
        if (!baseClassFields.isEmpty()) {
        	throw new SQLException("Base class " + baseClassName + " maps field " +
        						   baseClassFields.toArray()[0] + " which is not a member of derived class");
        }
    }

	public void writeEntityReference(TableDescription[] desc, String className, String baseClassName,
								     String generationType, StringBuffer buffer) {
		if (generationType.equals(BEAN) || generationType.equals(HYBRID))
			return;

		if (baseClassName == null) {
			buffer.append("    private " + getClassName(generationType) + " entity;\n");
			buffer.append("    protected Object getEntity() { return entity; }\n");
		}
		buffer.append("    " + getClassName(className) + "(" +
					  getClassName(generationType) + " entity) { ");
		if (baseClassName == null)
			buffer.append("this.entity = entity;");
		else
			buffer.append("super(entity);");
		buffer.append(" }\n\n");
	}

	private boolean hasPrimaryKey(TableDescription tdesc) {	
		for(TableColumn current: tdesc.getList()) {
			if (current.isPrimaryKeyField())
				return true;
		}
		return false;
	}
	
    /** Prints the primary key definition and its access function to standard out */
    public void writePrimaryKey (TableDescription[] desc, String className, String baseClassName,
    							 String generationType, StringBuffer buffer) {
		if (generationType.equals(BEAN))
			return;

        if (desc.length == 1) {
            if (hasPrimaryKey(desc[0])) {
                buffer.append("    private static String[] keyFields = new String[] {");
                for(TableColumn current: desc[0].getList()) {
					if (current.isPrimaryKeyField())
                    	buffer.append(" \"" + current.getName() + "\",");
            	}
            	buffer.deleteCharAt(buffer.length()-1);
                buffer.append(" };" + "\n");
                buffer.append("    public String[] getKeyFields() { return keyFields; }" + "\n");
            }
            buffer.append("\n");
        }
    }

    /** Prints the data members to standard out */
    public void writeAttributes (TableDescription[] desc, String className, String baseClassName,
    							 String generationType, StringBuffer buffer)
    	throws SQLException {
		if (!generationType.equals(BEAN) && !generationType.equals(HYBRID))
			return;

		Set<String> baseClassFields = extractMappedFields(baseClassName);

		for (TableColumn tableColumn: flatTableColumnList) {
			if (baseClassFields.contains(tableColumn.getName()))
				continue;
            buffer.append("    private " + tableColumn.getType() + " " + tableColumn.getNameCamelCaseFirstLow() + ";" + "\n");
			
		}
		
        buffer.append("\n");
    }

    /** Prints the geter methods for all members to standard out */
    public void writeGetMethods (TableDescription[] desc, String className, String baseClassName,
    							 String generationType, StringBuffer buffer)
    	throws SQLException {
		if (!generationType.equals(BEAN) && !generationType.equals(HYBRID))
			return;

		Set<String> baseClassFields = extractMappedFields(baseClassName);

		buffer.append("    // Read access functions" + "\n");
		for (TableColumn tableColumn: flatTableColumnList) {
            buffer.append("    public " + tableColumn.getType()  + " get"
                    + tableColumn.getNameCamelCaseFirstUp() + "()   { return " + tableColumn.getNameCamelCaseFirstLow() + "; }" + "\n");
			
		}
        buffer.append("\n");
    }

    /** Prints the seter methods for all members to standard out */
    public void writeSetMethods (TableDescription[] desc, String className, String baseClassName,
    							 String generationType, StringBuffer buffer)
    	throws SQLException {
		if (!generationType.equals(BEAN) && !generationType.equals(HYBRID))
			return;

		buffer.append("    // Write access functions" + "\n");
		for (TableColumn tableColumn: flatTableColumnList) {
            buffer.append("    public void set" + tableColumn.getNameCamelCaseFirstUp() + "(" + tableColumn.getType()
                    + " " + tableColumn.getNameCamelCaseFirstLow() + ") { this." + tableColumn.getNameCamelCaseFirstLow() + " = " + tableColumn.getNameCamelCaseFirstLow() +
                    "; }" + "\n");
			
		}
        buffer.append("\n");
    }

	public void writeToString(TableDescription[] desc, String className, String generationType, StringBuffer buffer) {
		if (!generationType.equals(BEAN) && !generationType.equals(HYBRID))
			return;
		if (baseClassName != null)
			return;

        buffer.append("    public String toString() { return super.toString(); }\n" + "\n");
    }

	public void writeClone(TableDescription[] desc, String className, String baseClassName,
						   String generationType, StringBuffer buffer) {
		if (!generationType.equals(BEAN) && !generationType.equals(HYBRID))
			return;
		if (baseClassName != null)
			return;

        buffer.append("    public Object clone() {\n" +
                      "        try { return super.clone(); }\n" +
                      "        catch(CloneNotSupportedException cnsx) { return null; }\n" +
                      "    }\n" + "\n");
    }

	/** Prints a reconstructor to restore an existing records from its primary key fields */
	public void writeReconstructor(TableDescription desc, String className,
								   String baseClassName, StringBuffer buffer) {
        if (!desc.hasPrimaryKey())
            return;
		buffer.append("\n    // Reconstructor\n");
		buffer.append("    public " + getClassName(className) + "(");
		for (TableColumn current: desc.getList()) {
			if (current.isPrimaryKeyField())
                buffer.append((current.getType() != null? current.getType(): "Object") + " " + current.getName() + ", ");
		}
        buffer.delete(buffer.lastIndexOf(","), buffer.length());	
		buffer.append(") throws SQLException {\n");
		if (baseClassName != null)
			buffer.append("        super(");
		for (TableColumn current: desc.getList()) {
			if (current.isPrimaryKeyField()) {
				if (baseClassName == null)
					buffer.append("        set" + current.getNameCamelCaseFirstUp() + "(" + current.getName() + ");\n");
				else
					buffer.append(current.getName() + ", ");
			}
		}
		if (baseClassName != null) {
			buffer.delete(buffer.lastIndexOf(","), buffer.length());
			buffer.append(");\n");
		}
		buffer.append("        find();\n    }\n");	
	}
	
    /** Prints the footer to standard out, i.e. a reconstructor, a default constructor,
     * a toString method, a clone method, and the closing bracket
     */
	public void writeFooter(TableDescription[] desc, String className, String baseClassName,
							String generationType, StringBuffer buffer) {
		if (desc.length == 1 && generationType.equals(HYBRID)) {
			if (desc[0].getList().size() > 0)
				writeReconstructor(desc[0], className, baseClassName, buffer);
			buffer.append("\n");
			buffer.append("    public " + getClassName(className) + "() {}\n\n");
		}
		
		writeToString(desc, className, generationType, buffer);
		writeClone(desc, className, baseClassName, generationType, buffer);
		
		buffer.append("}" + "\n");
		buffer.append("\n" + "\n");
	}

	/**
	 * @return ID-Typ der Tabelle
	 * @param Tabellenbeschreibung
	 */
	protected String getTableIdType(final TableDescription tabCols) {
		String idType;

		Vector<TableColumn> tableList = tabCols.getList();
		if (tableList.size() > 0) {
			TableColumn current = (TableColumn) tableList.get(0);
			idType = current.getType();
		}
		else
			idType = "String";

		return idType;
	}


	protected String getPackage(String className) {
		String pack = "";
		if (className.indexOf(".") != -1)
			pack = "package " + className.substring(0, className.lastIndexOf(".")) + ";";
		return pack;
	}

    protected String getClassName(String className) {
        if (className.indexOf(".") != -1)
            return className.substring(className.lastIndexOf(".") + 1);
        else
            return className;
	}

	protected String getTableName(String[] tables) {
		StringBuffer buf = new StringBuffer(tables[0]);
		for (int i = 1; i < tables.length; i++) {
			buf.append(",");
			buf.append(tables[i]);
		}
		return buf.toString();
	}

	protected List<TableColumn> flattenTableColumns(TableDescription[] tableDescriptions) {
		List<String> usedColumnNames = new LinkedList<>();
		List<String> notUniqueColumnNames = new LinkedList<>();
		List<TableColumn> flatTableColumnList = new ArrayList<>();
		
		for (TableDescription tableDescription: tableDescriptions) {
			for (TableColumn tableColumn: tableDescription.getList()) {
				flatTableColumnList.add(tableColumn);
				if (usedColumnNames.contains(tableColumn.getName())) {
					notUniqueColumnNames.add(tableColumn.getName());
				}
				else {
					usedColumnNames.add(tableColumn.getName());
				}
			}
		}
		for (TableColumn tableColumn: flatTableColumnList) {
			if (notUniqueColumnNames.contains(tableColumn.getName())) {
				tableColumn.makeUnique();
			}
		}
		return flatTableColumnList;
	}

	/**
	 * put your documentation comment here
	 * @param args
	 * @exception SQLException
	 */
	public static void main (String[] args) throws SQLException {
		if (args.length < 5) {
			System.out.println("Usage: CreateTableTemplate dbdriver dbname user passwd tablename [class] [beanclass | -b | -h] [baseclass]");
			System.exit(0);
		}
		String dbDriver = args[0];
		String dbName = args[1];
		String user = args[2];
		String passwd = args[3];
		String tableName = args[4];
		String className = null;
		String baseClassName = null;
		String generationType = HYBRID;
		String[] tableNames = null;
		if (tableName.indexOf(",") != -1) {
			StringTokenizer st = new StringTokenizer(tableName, ",");
			tableNames = new String[st.countTokens()];
			for (int i = 0; i < tableNames.length; i++) {
				tableNames[i] = st.nextToken();
			}
		}
		else {
			tableNames = new String[] {
				tableName
			};
		}
		if (args.length > 5)
			className = args[5];
		else {
			for (int i = 0; i < tableNames.length; i++) {
				if (className == null) {
					className = new String("");
				}
				className += tableNames[i];
			}
			className = className.substring(0,1).toUpperCase()+className.substring(1);
		}
		if (args.length > 6) {
			if (args[6].equals(BEAN))
				generationType = BEAN;
			else if (!args[6].equals(HYBRID))
				generationType = args[6];
		}
		if (args.length > 7)
			baseClassName = args[7];
		System.out.println(new CreateTableTemplate(dbDriver, dbName, user, passwd, tableNames,
				className, generationType, baseClassName).create());
	}

}