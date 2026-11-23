package org.iskan.mobile;
final class PipelineProblem {
 private PipelineProblem(){}
 static String explain(Throwable error,String step,int page){
  String where=(step.equals("Building PDF")?"All pages · ":"Page "+(page+1)+" · ")+step+"\n\n";
  if(error instanceof OutOfMemoryError)return where+"The phone ran low on memory. Close other apps and retry, or import a smaller image. Captures are kept.";
  String code=error.getMessage()==null?"":error.getMessage();
  if(code.equals("CROP_TOO_SMALL")||code.equals("INVALID_CROP"))return where+"The crop area is too small or its corners cross. Choose Original to reset, then adjust the four corners.";
  if(code.equals("PDF_TEXT_LAYER_MISSING"))return where+"The PDF text layer could not be verified. Retry text recognition, or export the preserved images without selectable text.";
  if(code.equals("INVALID_IMAGE")||error instanceof java.io.FileNotFoundException)return where+"This page image cannot be opened. Import it again as JPG/PNG, or retake just this page.";
  if(code.equals("IMAGE_WRITE")||code.equals("IMAGE_ENCODE")||code.equals("PDF_COMMIT_FAILED"))return where+"The phone could not write the processed file. Free some storage, then retry. Captures are kept.";
  if(step.equals("Reading text"))return where+"Text recognition could not complete on this image. Retry, or export without selectable text. Screenshots and photos can still be kept as images.";
  if(step.equals("Building PDF"))return where+"PDF assembly failed. Retry, or try image-only export. The original captures are kept.";
  return where+"This image could not finish this step. Try Original color or reset its crop, then retry. You can reimport just this page.";
 }
}
