package org.iskan.mobile;
/** Bounded content sizes: extra height stays outside cards, short screens shrink the welcome first. */
final class HomeLayoutSizes {
 static int[] fit(int available){int welcome=118,modes=136,id=100;int deficit=Math.max(0,welcome+modes+id-available);int cut=Math.min(deficit,54);welcome-=cut;deficit-=cut;cut=Math.min(deficit,32);modes-=cut;deficit-=cut;cut=Math.min(deficit,20);id-=cut;deficit-=cut;if(deficit>0){float factor=Math.max(0,available)/(float)(welcome+modes+id);welcome=Math.round(welcome*factor);modes=Math.round(modes*factor);id=Math.max(0,available-welcome-modes);}return new int[]{welcome,modes,id};}
}
