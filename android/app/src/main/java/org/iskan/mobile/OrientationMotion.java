package org.iskan.mobile;
final class OrientationMotion {
 static float target(float displayed,int previous,int delta){return previous+360*Math.round((displayed-previous)/360f)+delta;}
}
