package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeLayoutSizesTest {
 @Test public void tallScreensDoNotStretchCards(){assertArrayEquals(new int[]{118,136,100},HomeLayoutSizes.fit(650));}
 @Test public void shortScreenKeepsControlsWithinBudget(){for(int budget:new int[]{180,240,280,320,354}){int[] sizes=HomeLayoutSizes.fit(budget);assertTrue(sizes[0]+sizes[1]+sizes[2]<=budget);assertTrue(sizes[1]<=136);assertTrue(sizes[2]<=100);}}
 @Test public void welcomeShrinksBeforeModeCards(){assertArrayEquals(new int[]{84,136,100},HomeLayoutSizes.fit(320));}
}
