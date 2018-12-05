package basic;
/*******************************************************************************
 * Copyright (c) 2001-2005 The PriDE team and MATHEMA Software GmbH
 * All rights reserved. This toolkit and the accompanying materials 
 * are made available under the terms of the GNU Lesser General Public
 * License (LGPL) which accompanies this distribution, and is available
 * at http://pride.sourceforge.net/LGPL.html
 * 
 * Contributors:
 *     Jan Lessner, MATHEMA Software GmbH - JUnit test suite
 *******************************************************************************/
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import pm.pride.DatabaseFactory;
import pm.pride.SQL;
import org.junit.Ignore;
import org.junit.Test;

import pm.pride.ResultIterator;
import pm.pride.WhereCondition;

import static pm.pride.WhereCondition.Direction.*;
import static pm.pride.WhereCondition.Operator.*;

/**
 * @author less02
 *
 * Class to test select behavior using {@link WhereCondition}
 */
public class PrideWhereConditionTest extends AbstractPrideTest {

	private static int COUNT = 100;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		generateCustomer(COUNT);
	}
	
	@Test
	public void testEqualsExpression() throws Exception {
		WhereCondition expression = new WhereCondition().
				and("firstName", "First").
				and("lastName", "Customer");
		checkOrderByResult(expression, 1, 1);
	}
	
	@Test
	public void testEqualsWithNull() throws Exception {
		WhereCondition expression = new WhereCondition().
				and("firstName", null).
				and("lastName", "Customer");
		assertNull(new Customer().query(expression));
	}

	@Test
	public void testEqualsWithRaw() throws Exception {
		WhereCondition expression = new WhereCondition().and("firstName", SQL.raw("'First'"));
		checkOrderByResult(expression, 1, 1);
	}

	@Test
	public void testUnequalsWithNull() throws Exception {
		WhereCondition expression = new WhereCondition()
				.and("firstName", UNEQUAL, null);
		assertNotNull(new Customer().query(expression));
	}
	
	@Test
	public void testEqualsWithNullSkipped() throws Exception {
		WhereCondition expression = new WhereCondition().
				andNotNull("firstName", null).
				and("lastName", "Customer");
		checkOrderByResult(expression, 1, COUNT);
	}
	
	@Test
	public void testEqualDates() throws SQLException {
		WhereCondition whereCondition = new WhereCondition()
				.and("hiredate", firstCustomersHiredate);
		checkOrderByResult(whereCondition, 1, 1);
	}
	
	@Test
	public void testEqualDatesMillisecondsCorrectlyIgnored() throws SQLException {
		Date firstCustomersHiredatePlus1Millisecond = new Date(firstCustomersHiredate.getTime() + 1);
		WhereCondition whereCondition = new WhereCondition()
				.and("hiredate", firstCustomersHiredatePlus1Millisecond);
		checkOrderByResult(whereCondition, 1, 1);
	}
	
	@Test
	public void testBind() throws Exception {
		WhereCondition expression = new WhereCondition().withBind().
				and("firstName", "First").
				and("lastName", "Customer");
		checkOrderByResult(expression, 1, 1);
	}

	@Test
	public void testBindPlainSQLOutput() {
		WhereCondition expression = new WhereCondition().withBind().
				and("firstName", "First").
				and("lastName", "Customer");

		assertEquals("( firstName = First AND lastName = Customer ) ", expression.toSQLIgnoreBindings(null));
	}
	
	@Test
	public void testOrderByAsc() throws Exception {
		WhereCondition expression = new WhereCondition().orderBy("id");
		checkOrderByResult(expression, 1, COUNT);
	}
	
	@Test
	public void testWhereAndOrderBy() throws Exception {
		WhereCondition expression = new WhereCondition("id in (3,5)").orderBy("id", ASC);
		checkOrderByResult(expression, 3, 5);
	}

	@Test
	public void testOrderByDesc() throws Exception {
		WhereCondition expression = new WhereCondition().orderBy("id", DESC);
		checkOrderByResult(expression, COUNT, 1);
	}
	
	@Test
	public void testMultipleOrderByDesc() throws Exception {
		WhereCondition expression = new WhereCondition("firstName >= 'First' AND firstName <= 'Last'").
				orderBy("firstName", DESC).orderBy("id", DESC);
		Customer c = new Customer();
		ResultIterator ri = c.query(expression);
		String lastFirstName = "ZZZ";
		int lastId = 99999;
		if (ri != null){
		    do {
		    	String firstName = c.getFirstName();
		    	int id = c.getId();
		    	if (firstName.equals(lastFirstName)) {
		    		assertTrue(id < lastId);
		    	} else {
		    		lastFirstName = firstName;
		    	}
		    	lastId = id;
		    	assertTrue(firstName.compareTo(lastFirstName) < 1);
		    } while(ri.next());
		}
	}

	@Test
	public void testSubcondition() throws Exception {
		WhereCondition expression = new WhereCondition().
				and("lastName", "Customer").and().
					or("firstName", "First").
					or("firstName", "Last").
					bracketClose().
				and("active", null);
		System.out.println(expression.toString());
		Customer[] result = new Customer().query(expression).toArray(Customer.class);
		assertEquals(2, result.length);
	}

	@Test
	public void testCompleteStatementIsNull() throws SQLException {
		WhereCondition expression = new WhereCondition()
				.and("firstname", "First")
				.and((String)null);
		assertEquals(1, new Customer().query(expression).toArray(Customer.class).length);
	}

	@Test
	public void testIndividualFormatter() throws SQLException {
		SQL.Formatter autowildcard = new SQL.Formatter() {
			@Override public String formatValue(Object rawValue) {
				String value = DatabaseFactory.getDatabase().formatValue(rawValue);
				return value.toString().replace('*', '%');
			}
			
			@Override public Object formatPreparedValue(Object rawValue) {
				return null;
			}
			
			@Override public String formatOperator(String operator, Object rawValue) {
				if (operator.equals(WhereCondition.Operator.EQUAL) &&
						rawValue.toString().contains("*"))
					return WhereCondition.Operator.LIKE;
				return operator;
			}
		};
		
		WhereCondition expression = new WhereCondition(autowildcard).and("lastname", "C*").and()
				.and("firstname", "*").bracketClose(); // Ensure delegation of formatter to sub conditions
		assertEquals("( lastname LIKE 'C%' AND ( firstname LIKE '%' ) ) ", expression.toString());
		assertEquals(2, new Customer().query(expression).toArray(Customer.class).length);
	}

	//TODO implement fluent interface for nested selects
	@Test
	@Ignore
	public void testWhereWithInnerSelect() throws SQLException {
		WhereCondition expression = new WhereCondition()
				.and("firstname", IN, "SELECT FIRSTNAME FROM CUSTOMER WHERE FIRSTNAME='First'" );

		assertEquals("( firstname IN (SELECT FIRSTNAME FROM CUSTOMER WHERE FIRSTNAME='First') ) ", expression.toString());
		assertEquals(1, new Customer().query(expression).toArray(Customer.class).length);
	}

	private void checkOrderByResult(WhereCondition expression, int firstId, int lastId) throws SQLException {
		Customer c = new Customer();
		ResultIterator ri = c.query(expression);
		Customer[] array = (Customer[]) ri.toArray(COUNT);
    	assertEquals(firstId, array[0].getId());
    	assertEquals(lastId, array[array.length - 1].getId());
	}

	@Test
	public void testOrAsFirstStatement() {
		WhereCondition expression = new WhereCondition()
				.or("X", 1)
				.or("X", 2)
				.or("X", 3);

		assertEquals("( X = 1 OR X = 2 OR X = 3 ) ", expression.toString());
	}

	@Test
	public void testInOperatorsWithBindVariables() throws SQLException {
		WhereCondition expression = new WhereCondition()
				.withBind()
				.and("firstname", IN, "First", "SECOND", "THIRD")
				.and("lastname", LIKE, "C%");

		assertEquals("( firstname IN ( ?, ?, ? ) AND lastname LIKE ? ) ", expression.toString());
		assertEquals(1, new Customer().query(expression).toArray(Customer.class).length);
	}

	@Test
	public void testPlainSQLStringFromBindingWhereCondition() {
		WhereCondition expression = new WhereCondition()
				.withBind()
				.and("firstname", IN, "First", "SECOND", "THIRD")
				.and("lastname", LIKE, "C%")
				.and("active", true);

		assertEquals("( firstname IN ( First, SECOND, THIRD ) AND lastname LIKE C% AND active = true ) ", expression.toSQLIgnoreBindings(null));
	}

	@Test
	public void testNestedWhereConditionWithBindings() throws SQLException {
		List<String> names = Arrays.asList("Customer", "Test", "Other");

		WhereCondition whereCondition = new WhereCondition().withBind().and("firstname", "First");
		WhereCondition secondCondition = new WhereCondition().withBind();
		for (String name : names) {
			secondCondition.or("lastname", name);
		}
		whereCondition.and(secondCondition);

		assertEquals("( firstname = ? AND ( lastname = ? OR lastname = ? OR lastname = ? ) ) ", whereCondition.toString());
		assertEquals(1, new Customer().query(whereCondition).toArray(Customer.class).length);
	}

}