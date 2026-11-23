# Third-party components

The app bundles these third-party components. Full relevant license texts are also packaged under assets/licenses/ (the font license also appears under assets/fonts/).

| Component | Version | License / source |
|---|---|---|
| AndroidX Activity/Core/ExifInterface and CameraX | Pinned in app/build.gradle; CameraX 1.5.1 | Apache-2.0 — https://developer.android.com/jetpack/androidx/releases/camera |
| Material Components for Android | 1.12.0 | Apache-2.0 — https://github.com/material-components/material-components-android |
| OpenCV | 4.12.0 | Apache-2.0 — https://github.com/opencv/opencv |
| ONNX Runtime Android | 1.23.1 | MIT — https://github.com/microsoft/onnxruntime |
| PP-OCRv4 detection/recognition and mobile orientation classifier, converted and bundled by RapidOCR | Models from rapidocr-onnxruntime 1.4.4 | Apache-2.0 — https://github.com/RapidAI/RapidOCR and https://github.com/PaddlePaddle/PaddleOCR |
| PDFBox Android / Apache PDFBox and FontBox | 2.0.27.0 | Apache-2.0 — https://github.com/TomRoush/PdfBox-Android |
| Noto Sans SC | Regular instance of Noto CJK variable font, generated at weight 400 | SIL OFL 1.1 — https://github.com/notofonts/noto-cjk |

The OCR weights and generated regular font are included as build assets for offline operation. Noto remains under the OFL. The Python desktop app's dependency licensing is documented separately in the root README; this Android implementation does not embed PyMuPDF.

