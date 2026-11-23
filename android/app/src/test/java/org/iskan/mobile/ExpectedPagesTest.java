package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class ExpectedPagesTest {
 @Test public void acceptsWheelRangeIncludingLimits(){assertEquals(1,ExpectedPages.parse("1"));assertEquals(100,ExpectedPages.parse("100"));assertEquals(27,ExpectedPages.parse(" 27 "));}
 @Test public void rejectsInvalidTypedInput(){for(String value:new String[]{"", "0", "101", "999999999", "-1", "1.5", "five"}){try{ExpectedPages.parse(value);fail(value);}catch(IllegalArgumentException expected){}}}
}
