package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class OrientationMotionTest {
 @Test public void clockwiseContinuesThroughFullTurn(){assertEquals(450,OrientationMotion.target(360,0,90),0);}
 @Test public void reverseContinuesThroughNegativeFullTurn(){assertEquals(-450,OrientationMotion.target(-360,0,-90),0);}
 @Test public void halfTurnsPreserveRequestedDirection(){assertEquals(-180,OrientationMotion.target(0,0,-180),0);assertEquals(180,OrientationMotion.target(0,0,180),0);}
 @Test public void interruptedTurnRetargetsFromCurrentCycle(){assertEquals(180,OrientationMotion.target(35,90,90),0);assertEquals(0,OrientationMotion.target(35,90,-90),0);}
}
