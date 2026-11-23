# Implementation validation — 2026-10-14 (Asia/Taipei)

This is a functioning local application, installed in the project `.venv`, with a real processing/export path. No document processing or save/print success is simulated. This report separates implemented software checks from acceptance checks requiring the user's devices and physical papers.

## Checked on this laptop

- Windows x64, Python 3.11.0; Intel Core i5-13450HX, about 23.7 GiB RAM, Intel UHD Graphics and NVIDIA RTX 4050 Laptop GPU. Processing uses CPU; GPU support/throughput was not tested.
- Fully pinned dependency environment: `pip check` passed. RapidOCR's bundled PP-OCRv4 detection/recognition and mobile orientation-classification models initialize offline. No PaddlePaddle GPU installation is required.
- **15 integration tests passed**. The suite includes a real OCR/searchable-PDF case; other bounded state/export cases use deterministic OCR fixtures to focus on failure/revision/transaction behavior.
- Two synthetic documents were uploaded through the real browser UI, processed, given watermark/page-number settings, approved, and saved as a multipage PDF. The interface displayed the actual laptop destination and saved state. Desktop and 390 × 844 responsive screenshots were inspected. This verifies responsive layout, not Android camera hardware.
- A generated angled/shadowed page with faint text, a colored stamp, signature-like marks, a table, highlight and Traditional Chinese was processed and rendered. The colored/faint content remains visible. The PDF text-selection overlay was visually checked for alignment against the visible printed text. Preview bytes also match a render of the saved PDF in the integration test.
- Synthetic fixture timing: **0.756 seconds** for quality/geometry/cleanup and **3.547 seconds** for initial OCR (about 4.3 seconds total). This is one fixture, including initial OCR load, not a throughput guarantee.
- An HTTPS server was started with the generated CA/server certificate. A client using the CA as its trusted root successfully verified both loopback and laptop LAN-address TLS connections. The LAN API refused unauthenticated document access, accepted the correct pairing code, set a secure cookie, and withheld laptop-only folder privileges. This does not establish Android certificate trust or firewall accessibility from a second device.
- QA browser scans, their log, and generated output are moved to ignored `tmp/qa/` after testing, leaving a clean default working store. Temporary test servers are stopped. Certificate files are generated for the laptop Wi-Fi IP; trust installation is left to the user.

## Acceptance coverage

| Brief criteria | Software status / evidence | Remaining check |
|---|---|---|
| 1. Secure Android rear camera | HTTPS, QR, pairing, environment-facing camera request, actual exposed device enumeration, still capture | Trust CA on actual Android; grant permission and verify rear camera |
| 2–4. Permission/switch/upload/guide/feedback | Denial/unavailable-camera handling; upload worked in browser; paper/custom ratios; real measured quality with temporal smoothing | Android denial/recovery and switching; calibrate feedback against actual phone captures; no validated glare detector |
| 5–7. Single/Multiple/collection/retakes/order | Integration-tested Single save flow, multipage targeted replacement, correction, removal/reorder, expected-count mismatch and upload retries; multipage browser flow saved | Actual phone Single and Multiple runs |
| 8–9. Cleanup and failed-page recovery | Perspective, skew, bounded lighting correction, appearance; synthetic visual inspection; targeted failed-page retry test | Representative real shadows/angled/faint pages; curved-page unwarping unavailable, flatten paper |
| 10–11. Preview/export/searchable alignment | Shared PDF composition; watermark/numbers; exact preview-render equality; actual OCR and text selection overlay inspected | Inspect PDFs from actual pages in preferred reader; handwriting/vertical/complex layouts may be inaccurate |
| 12–13. Editable metadata/storage/save reliability | Approved editable fields, uncertain date candidates, laptop folder checks, no-overwrite collision tests, failed-export/retry and publish-before-log crash test | Native Windows folder dialog usability; restrictive/non-NTFS/network storage behavior |
| 14. Printing | Approved PDF preview, explicit phone-to-laptop ready state, print-only log test; no claim of physical success | Open PDF viewer/print dialog and physically print; cancel/failure checks |
| 15–16. Strict logs and content restraint | Log table and returned entries exactly title/page_count/scanned_date; no duplicates; diagnostic codes and sanitized validation; no document telemetry | Verify endpoint/security settings on the intended private network |
| 17. Recovery/stale work | Persisted captures/order/date; restart re-OCR; revision checks discard old jobs; saved scans remain reviewable after restart | Disconnect/reconnect and sleep/wake with actual phone; long real-document jobs |
| 18. Installation/connection/operation docs | README, install/start/HTTPS scripts, in-app Help, pinned lock, retention and shutdown policy | User installs/trusts local CA and allows private-network firewall rule |

## Explicit limitations

Full acceptance cannot be claimed until actual Android, representative physical-paper, and printer checks pass. No Android phone or printer was available to this chat for those checks. Guaranteed per-lens selection is a browser limitation. There is no curved-page unwarping, automatic page orientation, validated glare detection, or handwriting-specific OCR. Manual rotation/cropping and photo upload remain working fallbacks. OCR and quality thresholds require actual-document inspection; no unreadable text is fabricated. A print request confirms preparation/opening only, never completed physical printing.

PyMuPDF licensing is AGPL-3.0/commercial; see README before redistributing this application. HTTPS trust, private-network firewall configuration, and Windows/Android device settings are not silently changed by installation.

The reproducible script and integration suite remain in `scripts/verify_output.py` and `tests/test_workflow.py`. QA artifacts are in ignored `tmp/qa/`: export preview, text-alignment overlay, synthetic source/capture, sample PDFs, and browser screenshots.
