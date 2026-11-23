# I-SKAN

**Native Android app:** the approved mobile workflow is implemented in [android/](android/README.md). Build/install instructions and device-testing limitations are documented there. It runs on the phone without this laptop server or certificates. The sections below describe the original laptop/browser application.

A private document scanner hosted on a Windows laptop, with a shared Android capture and laptop review interface. Documents are processed on the laptop with OpenCV and offline RapidOCR (PaddleOCR models through ONNX Runtime). Nothing in the scanning workflow calls a cloud OCR or language-model service.

## Start here

The application and its Python environment are installed in this folder. For laptop-only use:

```powershell
.\start.ps1 -Local
```

Open **http://localhost:8443** in Chrome or Edge. Upload photos, process, correct, approve, and save. Use a regular browser for the PDF viewer/printing if the in-app browser lacks a PDF print toolbar.

For a fresh installation, use **64-bit Python 3.11** and run:

```powershell
.\install.ps1
```

If PowerShell blocks scripts, run them for this invocation with `powershell -ExecutionPolicy Bypass -File .\install.ps1` (or the corresponding start/setup script); there is no need to change the machine-wide execution policy. Installation needs internet access to PyPI. Dependencies are fully pinned in `requirements-lock.txt`; OCR model weights ship inside the pinned RapidOCR package. The core app works offline once installation passes its OCR self-check. Python 3.11 on Windows x64 is the tested configuration; newer Python releases are not verified with these pins.

## Secure Android connection

1. Connect the laptop and Android phone to the same **private** Wi-Fi. Keep the laptop awake while capturing or processing. Guest networks can block device-to-device traffic.
2. Run `ipconfig` and note the laptop's Wi-Fi IPv4 address, for example `192.168.1.25`. A DHCP reservation on your router helps keep it stable.
3. Generate a private local certificate authority and a certificate for that address:

   ```powershell
   .\setup-https.ps1 -Ip 192.168.1.25
   ```

4. Double-click `certs\root-ca.cer` on Windows. Install Certificate → **Current User** → Place all certificates in the following store → **Trusted Root Certification Authorities**. Verify it is your I-SKAN certificate. The app does not install it automatically.
5. Transfer only `certs\root-ca.cer` to Android (USB is a practical option). In Android settings search for **Install a certificate** or **CA certificate**. Typical path: Security & privacy → More security settings → Encryption & credentials → Install a certificate → CA certificate. Manufacturer/version menus vary; a screen lock may be required. Review Android's trust notice. This CA lets the device trust certificates issued by the laptop; protect `root-key.pem`, never copy private keys to the phone, and remove the I-SKAN CA when no longer needed.
6. Run `.\start.ps1`. The laptop interface shows a phone connection URL, QR code, and an eight-character pairing code. Scan the QR code in Chrome on Android and enter the code. Cookies retain access for 24 hours of inactivity; restarting the app requires pairing again. The local loopback interface pairs automatically.
7. Confirm Chrome opens the address **without a certificate warning**, start a scan, and allow **Camera**. A localhost exception on Windows does not secure a LAN address on Android. Bypassing a certificate warning is not the setup procedure. Confirm the page is a secure context and try the rear camera, switching, denial/recovery, and a real test capture. Android/browser trust and camera compatibility remain device checks.
8. If Windows Firewall blocks the connection, allow inbound **TCP 8443 on the Private profile only**. This is a user-controlled Windows setting; the installer does not change firewall rules. Do not create router port forwarding or expose I-SKAN to the internet.

The server certificate expires after 397 days; regenerate it with the setup script. The root lasts ten years and is reused when generating a certificate for a new Wi-Fi IP. If the root changes, trust the new root on both devices and remove the old one. Certificates and keys are in ignored `certs/`; keep the project folder accessible only to trusted laptop users.

If a phone cannot trust a local CA, **Upload photos** still provides a supported capture path. You can explicitly run `.\start.ps1 -HttpLan` for photo uploads on a private Wi-Fi network. That fallback is unencrypted and does not enable live Android browser camera access. Prefer trusted HTTPS for document capture. `-Local` binds only to the laptop; the default secure launcher binds to the LAN and requires certificate setup. `-Port 8444` selects another unprivileged port; adjust the firewall accordingly.

Camera context and still capture behavior follow [MDN getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia) and [ImageCapture.takePhoto](https://developer.mozilla.org/en-US/docs/Web/API/ImageCapture/takePhoto). Only cameras actually exposed by the browser are shown, with rear-facing camera requested initially. No microphone permission is requested. Still capture uses the maximum advertised photo dimensions when available; otherwise the UI identifies the video-resolution fallback. A photo upload can provide a higher-resolution native-camera image.

## Workflow

- **Prepare:** Single makes one page/document. Multiple asks for the expected page count. Select paper, orientation, appearance, or a custom size in mm/inches. Custom dimensions are validated (20–2000 mm) and must leave room for margins.
- **Capture and review:** Each received photo appears in the original grid. Crop with four corner handles, rotate, retake in place, remove, or move pages earlier/later. Received captures survive a connection loss. Before processing a Multiple document, add missing pages or explicitly update the expected count. Upload retries reuse a capture key and do not append duplicate pages. A pending upload stays in browser memory for retry until the tab closes; photos not yet received by the server are not recovered after reload.
- **Process:** Only the explicit Process action begins full cleanup/OCR. The bounded, single-worker queue reports actual stages. A failed page retains its original and can be retried. Retakes/corrections invalidate only that page's processing; Process then processes unfinished pages, keeping ready pages. Changing appearance invalidates all affected pages; changing layout does not rerun OCR. Page revisions prevent older worker results from replacing a newer capture.
- **Verify:** The final preview is rendered from the export composition. Compare it with the original, check marks and text, approve an editable title, choose any document date deliberately, review the filename, and choose the laptop destination. Date candidates are suggestions; no detected date is automatically accepted as the document date. User-edited fields in an open tab survive incoming page/suggestion updates and need approval again. Approval checks the session revision. The title suggestion is a candidate large heading, not a generated summary.
- **Save:** Searchable PDF is the default. Image-only PDF and JPG/PNG page sets are available in Scan settings. PDF dimensions, margins, watermark, and numbering match preview. Fit preserves aspect ratio and does not clip; Preserve keeps the crop proportions and anchors size to the selected paper width. Raster exports render the same composition at 240 dpi, capped at 6000 pixels along the longest edge to bound memory. Filename characters/reserved Windows names are validated, and collisions require choosing another name; nothing is silently overwritten.
- **Print:** Print-only does not save or create a log entry. A phone Print request puts the document in a Ready to print on laptop state. The laptop opens the approved PDF in a new tab; use the viewer's Print control or Ctrl+P for printer, copies, range, scaling, duplex, and paper. The app deliberately reports preview/preparation only; it cannot verify physical printing or printer cancellation/failure. Save and Print saves first and then prepares the same layout. Allow the local app's popup if the browser blocks print preview.

To select storage, use **Browse laptop…** during verification, or enter an existing absolute folder path in the laptop interface. The server checks writability and remembers approved folders. A phone can choose an approved laptop folder; it cannot silently add arbitrary filesystem destinations. Default output is `output/`. Export paths are shown only where needed in the working interface; they are absent from diagnostics and minimal scan logs.

## Live feedback and processing limits

Live low-resolution frames are measured on the laptop without being saved. Feedback uses mean brightness, Laplacian sharpness, detected page coverage/corners, and inter-frame motion. It smooths a condition over two checks and prioritizes movement/darkness/blur. Starting thresholds: brightness <65, sharpness <28 at standardized width, motion >9 intensity levels, page area <35%, and corners within 1.2% of an edge. These are engineering starting points, checked on synthetic fixtures, **not calibrated against a representative real-phone corpus**. “Ready” cannot certify readability. Auto capture waits for stable measured quality and uses a cooldown plus scene-change/fingerprint checks to reduce duplicate capture; manual capture is always available.

Conservative page detection requires contrast between paper and background so tables/boxes are less likely to become the crop. If uncertain, the full image is kept for manual cropping. Processing performs perspective correction, restrained skew correction with an expanded canvas, bounded illumination correction, and the selected appearance. Color is safest for stamps, highlights, photographs, and faint marks. Black-and-white thresholding is an explicit option and can discard faint content; inspect it. There is **no generative reconstruction**.

Curved-page UVDoc unwarping, guaranteed lens-level Android access, automated page orientation, and a validated glare detector are not included. Flatten curved pages for capture and correct orientation manually. The phone may not expose every physical lens. OCR is best suited to printed Chinese/English; handwriting, vertical text, complex layouts, and stamps need inspection. The recognizer can miss text or spaces. Searchable PDF keeps visible pixels and places invisible recognized text using the processed OCR quadrilaterals; OCR confidence below 0.5 is omitted. CJK uses a standard CID text font; actual document pixels remain embedded, while selection/search behavior can vary with PDF readers.

## Operational data and strict logs

`data/operational.sqlite` contains session/page state, remembered settings, suggested/approved metadata, revisions, and internal save receipts. Captures and cleaned pages live under `data/<session-id>/`. OCR text/coordinates are kept **only in RAM**, with no OCR sidecars or extracted summaries written by default. After a restart, an unfinished processed draft returns to Captured and must be processed again; original captures, order, settings, and first-capture scan date survive. Approved saved PDF output is still printable after restart.

The separate `data/scan_logs.sqlite` table has **exactly**:

```json
{"title":"Meeting Minutes","page_count":8,"scanned_date":"2026-10-14"}
```

One entry is inserted only after the output has been published successfully. The date is assigned at the first successful capture in **Asia/Taipei** and kept through retakes/retries. Counts are computed from those rows. Print-only, repeated save requests, captures, failures, and previewing create no new rows. Session save receipts remain internal; an attached-database SQLite transaction commits receipt/log insertion together. Publication uses a fully written file and a no-overwrite hard link, or an atomic Windows rename for image sets. A saved output checksum permits reconciliation after a crash between publication and log commit, without treating an unrelated colliding file as a saved scan. On filesystems without hard-link support, PDF save fails safely; choose a local NTFS output folder. Filesystem loss/hardware corruption is outside this crash-recovery guarantee.

Access logs, document telemetry, request-body logging, tracebacks, and document-content console logging are disabled in the launcher. API errors return non-content codes; validation errors do not echo submitted fields. There is no analytics integration. Browser network tools necessarily see document API requests in the authorized local session; avoid sharing those diagnostics with document data included.

Originals are kept until saving succeeds. By default, originals are removed after save; enabling retention keeps them for up to **30 days**. Temporary drafts and saved working previews expire after **7 days**; retained saved working copies expire after 30 days. Expiration cleanup runs on app startup and hourly while the worker is idle. The UI allows earlier Discard/Remove working copy. Exported output and minimal log entries remain. Saved scans are immutable; start a new session for another document. Back up your exported files separately. Shut down with Ctrl+C, then close the terminal. Do not delete working data while the server runs.

## Verification

```powershell
.\.venv\Scripts\python.exe -m pytest -q tests
.\.venv\Scripts\python.exe scripts\verify_output.py
```

The synthetic QA script writes reproducible originals, an angled/shadowed capture, rendered export, text-alignment overlay, and timing results under ignored `tmp/qa/`. Integration tests use temporary folders and test-only state, not the application's scan log. They cover real OCR/searchable output, preview equality, ordering/retake revisions, upload retry identity, page-count mismatch, save collision/failure/retry, crash recovery, minimal log schema, print-only behavior, and PDF/JPG/PNG output.

See [VALIDATION.md](VALIDATION.md) for measured results, completed criteria, and outstanding hardware checks. Automated tests and synthetic images do not substitute for Android and physical-paper verification.

## Dependencies and licenses

This implementation intentionally uses the lighter pinned RapidOCR 1.4.4 CPU pipeline rather than the heavier PP-OCRv5/UVDoc stack. [RapidOCR](https://github.com/RapidAI/RapidOCR) converts PaddleOCR models to ONNX; Chinese and English are its default languages. The installed wheel bundles PP-OCRv4 detection/recognition and an orientation classifier, so model setup is part of installation. RapidOCR and upstream PaddleOCR are Apache-2.0; ONNX Runtime is MIT, OpenCV is Apache-2.0, Pillow uses the HPND license, and ReportLab uses BSD. **PyMuPDF uses AGPL-3.0 or a commercial license**; this local personal app is not a promise that a proprietary redistribution can ignore that license. Review upstream terms before distribution. Package metadata and bundled model/license files were checked during implementation; model suitability still requires your actual-document QA. [RapidOCR pinned package](https://pypi.org/project/rapidocr-onnxruntime/1.4.4/) · [PaddleOCR](https://www.paddleocr.ai/main/en/index.html) · [OpenCV thresholding](https://docs.opencv.org/4.x/d7/d4d/tutorial_py_thresholding.html).
