package basic;

import org.junit.Test;

import pm.pride.DatabaseFactory;
import pm.pride.WhereCondition;

public class PrideUpdateWithWhereConditionTest extends AbstractPrideTest {
	
	@Override
	public void setUp() throws Exception {
		super.setUp();
		generateCustomer(1);
	}
	
	@Test
	public void testUpdateWithBind() throws Exception{
		Customer c = new Customer(1);
		assertEquals("First", c.getFirstName());
		assertEquals("Customer", c.getLastName());
		c.setFirstName("Inge");
		c.setLastName("Updated");

		c.update(new WhereCondition().bindvarsOn().and("id",1));
		DatabaseFactory.getDatabase().commit();

		Customer[] ca = (Customer[])c.queryByExample("lastName").toArray();
		assertEquals(ca.length, 1);
		assertEquals("Inge", ca[0].getFirstName());
		assertEquals("Updated", ca[0].getLastName());
	}
	
	@Test
	public void testUpdateWithoutBind() throws Exception{
		Customer c = new Customer(1);
		assertEquals("First", c.getFirstName());
		assertEquals("Customer", c.getLastName());
		c.setFirstName("Inge");
		c.setLastName("Updated");
		
		c.update(new WhereCondition().bindvarsOff().and("id",1));
		DatabaseFactory.getDatabase().commit();

		Customer[] ca = (Customer[])c.queryByExample("lastName").toArray();
		assertEquals(ca.length, 1);
		assertEquals("Inge", ca[0].getFirstName());
		assertEquals("Updated", ca[0].getLastName());
	}
}
