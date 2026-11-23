package org.iskan.mobile;
final class ExpectedPages {
 static int parse(String value){if(value==null||!value.trim().matches("[0-9]{1,3}"))throw new IllegalArgumentException();int count=Integer.parseInt(value.trim());if(count<1||count>100)throw new IllegalArgumentException();return count;}
}
