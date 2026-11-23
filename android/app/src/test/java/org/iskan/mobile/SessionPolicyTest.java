package org.iskan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class SessionPolicyTest {
 @Test public void savedFlagOrReceiptCompletesSession(){assertTrue(SessionPolicy.completed(true,false));assertTrue(SessionPolicy.completed(false,true));assertTrue(SessionPolicy.completed(true,true));assertFalse(SessionPolicy.completed(false,false));}
 @Test public void resumeIsOnlyForNonemptyUnfinishedWork(){assertTrue(SessionPolicy.offerResume(false,1));assertTrue(SessionPolicy.offerResume(false,20));assertFalse(SessionPolicy.offerResume(true,1));assertFalse(SessionPolicy.offerResume(false,0));}
}
