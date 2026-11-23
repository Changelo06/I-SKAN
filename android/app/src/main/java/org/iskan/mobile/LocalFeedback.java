package org.iskan.mobile;
final class LocalFeedback {
 static final String OK="Sige";
 static final String HOLD="pahimuyong",FINISHING="manog tapos na po",CONVERTING="gina gisa pa…",DARK="dulom man oi",BRIGHT="ay kasanag!",GOOD="perpekt!",DONE="tapos na po",WAITING="bakal danay ice";
 private LocalFeedback(){}
 static String camera(String status){
  switch(status){case "Hold still":return HOLD;case "Too dark":return DARK;case "Too bright":return BRIGHT;case "Ready":return GOOD;case "Looking for document":return "Looking for document";default:return status;}
 }
 static boolean overexposed(double brightness,double clippedFraction,double contrast){return brightness>248&&clippedFraction>.85&&contrast<12;}
}
