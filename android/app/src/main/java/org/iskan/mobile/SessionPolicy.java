package org.iskan.mobile;
final class SessionPolicy {
 static boolean completed(boolean exported,boolean receipt){return exported||receipt;}
 static boolean offerResume(boolean completed,int pages){return !completed&&pages>0;}
}
