# I-SKAN for Android

Native Android implementation of the approved Home → Capture → Review → Process → Export design. The Android app processes documents and stores exports **on the phone**. It does not need the Python server, laptop Wi-Fi address, pairing code, certificate installation, or an internet connection. The existing laptop application remains separate.

## Install the APK

The phone build is copied to:

`output/android/I-SKAN-Android-arm64-v2.2.0.apk`

1. Transfer that APK to the phone, for example using USB.
2. Open it from the phone's Files/Downloads app and follow Android's installation prompt. If Android asks, permit installation for the app opening this APK.
3. Open **I-SKAN** and allow Camera when requested. Photo import remains available if camera permission is denied.

Requires **Android 10 or newer, arm64-v8a**. An x86_64 APK is also provided for development/emulators. These are debug-signed personal development builds, not Play Store releases. Keep the same signing key when producing updates; a differently signed APK cannot upgrade an existing installation.

## Version 2.2.0: curved angle dial and tailored vectors

Manual crop now uses a directly manipulated semicircular protractor with a fixed marker, 0.1-degree adjustment, bounds of ±45 degrees, cancellation restoring the previous angle, keyboard support and accessible range actions. Custom two-tone vector icons coordinate Home and review tools with the folded-document identity. Lighter borders, medium-weight button labels and spaced recent cards retain the flat Graphite/Sienna design and system theme.

## Version 2.1.0: poster identity implemented as native UI

The updated poster identity is now code-native: a folded sienna document with Graphite scanning corners and a peach sweep, centered Home branding, a solid sienna Scan ID action, peach Import File / Scan tiles and filled file-type badges. Colors remain flat; system light/dark mode is retained. Recent/history cards bind real filenames, dates and page counts. File-action menus anchor to the tapped button. Scanning, manual/Smart crop, orientation, cleaner settings, OCR/export and completed-session handling retain their existing behavior.

### Actual screenshot capture

`capture-android.ps1` builds/installs the separate **I-SKAN Preview** application (`org.iskan.mobile.preview`), then uses Android instrumentation and UiAutomation to capture the actual running windows. It does not replace production-app data. Sample documents are processed by the real scanner pipeline and one sample export is created in Downloads. Screen captures cover Home, Review, Crop, Cleaner, Processing when captured during work, Export, saved confirmation and History. An emulator also captures the native Camera screen with the emulated camera; physical-device camera capture is skipped by default.

```powershell
.\capture-android.ps1 -Serial emulator-5580
```

The script requires an already booted, authorized Android device. On an emulator it sets a 1080×1920 (9:16) display and light mode for consistent capture. Raw, unedited PNGs are pulled into a timestamped `output/screenshots/` folder with `CAPTURE-INFO.json`. This is actual Android-window capture, not image generation or a browser recreation.

Validation: 61 JVM tests pass; Android lint has zero errors. Real native screenshot and two-page PDF export flow passed on a connected Samsung Galaxy A54 in system dark mode. The focused curved-dial device regression passed for drag updates, final commit, bounds, cancellation and accessible progress. The remaining 23 functional device tests are compiled but were not run in this validation.

## Version 2.0.1: Export cleanup

Export now labels the name field **Filename**, omits the repeated image/preview/unreadable-text status panel, and uses translucent light-gray paging controls with dark chevrons and no heavy shadow. Processing-stage feedback and image-preserving export behavior remain intact. Build and 61 JVM tests pass; lint reports zero errors. Signature and zip alignment verified; device appearance awaits phone confirmation.

## Version 2.0.0: professional Graphite & Sienna interface

- **Flat identity:** solid Graphite, Burnt Sienna, peach and neutral surfaces; subtle shadows; standard system typography; no gradients, mascot or rotating speech bubble. The approved icon is interpreted as native vector artwork with a safe-zone adaptive launcher foreground. Light/dark mode follows the system. Startup has no artificial delay; version and Christian Cabahug credit appear under About.
- **Home:** Select document type, full-width Scan ID, smaller Import File / Scan actions side by side, and a compact Page layout row. Recent scans shows up to two rows that fit the available height. See all opens a separate Scan history page with Back. Home does not scroll. The UI adapts to available phone dimensions; 6.7 inches / 9:16 is a design reference rather than a forced physical display size.
- **History:** lists the actual saved filenames for new exports, newest first, with date/page count and an open-file action. List rows are reused while scrolling. Existing history rows are preserved; older entries without saved-file links direct users to Downloads. The content log retains exactly title, page_count and scanned_date. A version-2 migration adds a receipt-to-log link and separate operational output metadata (format, names, URIs and composed page count), without OCR content or telemetry.
- **Review:** add tile first, then thumbnails at the foot of the dark document workspace, above Original / Crop / Smart crop / Orientation / Flip. Inline editors remain. Tool selection uses pale peach. Camera flash remains yellow On / white Off; the separate Auto chip changes state when tapped.
- **Cleaner settings:** a dedicated page with one look dropdown, original/cleaned comparison, Paper / Tone / Detail sections and collapsed Protection controls. No duplicate preset buttons. Apply/Cancel stay fixed below scrolling controls. Preview work is debounced, obsolete results are discarded and the source is reused. Enlarging a comparison temporarily pauses preview replacement, and leaving the page releases owned images. Apply retains the original-based processing pipeline and invalidates stale output/OCR only when settings changed.
- **Processing:** professional English feedback, no brand logo in the processing workspace, a flat peach scanning line, short animated green-circle checks on actual stage completion, native peach progress rings while active and neutral waiting indicators. Failed stages remain red with recovery guidance. Reduced-motion settings skip the check animation and scanning motion. Crop/camera paths, matrices and draw buffers are reused to reduce allocation pressure.
- **Export:** Type and composed page count share one metadata row; the path/Share row remains compact. Saving shows a temporary nonblocking success popup rather than a permanent Saved header or footer note. Save becomes Exit. Exit clears the completed in-memory session and returns Home; Scan again starts a fresh camera document even if the previous source was gallery import. Sharing reuses the saved file.

### Session conditions

| Condition | Behavior |
| --- | --- |
| Unsaved work is interrupted | The journal remains; Home offers Resume draft. |
| Save succeeds | MediaStore files/history remain; only this session's active journal is cleared. |
| App reopens after completion | Home appears without restoring the completed scan as a draft. |
| Exit after saving | Home, with no active completed document. |
| Scan again | Fresh camera session. |
| Save fails | Current draft remains for retry. |
| An old completed object tries to clear a newer draft | Session-ID check protects the newer journal. |

Validation: **61 JVM tests passed**, Android lint **0 errors / 57 warnings**, arm64/x86_64 APKs built, arm64 signature and 16 KB zip alignment verified. **24 Android integration tests compiled but were not run**, including journal cleanup/output preservation, old-session/new-draft isolation, history schema migration and rendered stage colors. No emulator was started. Physical camera/gallery behavior, exact on-phone layout, keyboard handling and frame rate still require the user's acceptance pass. This release is a debug-signed personal build.

## Version 1.11.0: document looks and adjustable cleaner

Open **⋮ → Cleaner settings → Document B&W**, then **Preview current page** to compare Original/Cleaned and **Apply**. The settings include Paper whitening, Shadow cleanup, Faint text protection, Color protection, Digital sharpening, signed Brightness, Exposure (−2…+2 EV), signed Contrast and B&W ink retention. The look selector offers Document black & white, Soft grayscale and Clean color. Presets change local settings until Apply; Cancel leaves stored values unchanged. Reset restores neutral/default cleaner values while retaining the selected look.

B&W now combines a low-resolution illumination estimate with resolution-aware local Gaussian thresholds and an adjustable ink bias. It exports strict black/white pixels in lossless PDF/PNG; JPG can introduce compression tones. Sharpening uses bounded luminance detail, avoiding automatic changes to chroma; large exposure/contrast changes can still clip content. Original color bypasses all enhancements and remains the starting appearance for IDs. Inspect faint text, photographs and stamps before applying B&W. Saved exports stay unchanged; current unsaved document changes invalidate prepared images/OCR and reprocess on export.

Java image-buffer memory now scales with row width for whitening, sharpening and normalization. Neutral enhancements skip extra luminance work. The same native processing pipeline serves preview and export at their respective resolutions, so compare the final full-page output too.

59 local JVM tests passed; 21 device integration tests compiled, not executed. A portable Python/OpenCV synthetic reference produced strict binary pixels and retained its marked dark text/rules; it is not physical-device validation. See [CLEANER-RESEARCH.md](CLEANER-RESEARCH.md) for the implementation, primary sources, controls and limits; the reference comparison is in `output/cleaner-research/comparison.png`.

## Version 1.10.0: separate ID, gallery and camera actions

Home now offers **Scan ID**, **Import File** and **Open camera** as direct actions. The old Single/Multiple selector, expected-count control and separate Next are replaced. Scan ID retains the front/back workflow and print-layout settings. Import File opens AndroidX's image-only photo picker with multi-selection (system fallback depends on Android version/provider). It does not request camera permission.

Open camera creates a freeform multi-page document. After capturing a page, **+** in Review opens the camera for another page, while **Next** processes the pages already received without an expected-count warning. For imported documents, **+** and Retake return to the photo picker. Retake requests one replacement image. The existing maximum of 100 document pages, two ID sides, and import size limits remain. Scan again repeats the source type. Picker cancellation leaves current pages intact; cancelling an empty new import returns Home.

Source/freeform flags persist with the private draft. Existing drafts without these flags retain their expected-count behavior. Home retains Resume for an unsaved document and asks before replacing it with a new action. Image loading, caching and native processing remain on the existing worker paths.

Validation: 50 JVM tests passed; 18 Android integration tests compile, including persisted gallery/freeform source and adding beyond the initial expected count. Device tests were not executed. APK signature and zip alignment verified. Physical gallery/camera interaction and appearance await phone testing; no emulator was started.

## Version 1.9.2: save completion, chat bubble and typed page count

- Save becomes **Exit** after successful saving; Exit closes the activity. **Scan again** starts a fresh capture after saving and does not save a second copy. Before saving, Scan again offers the existing unsaved-document confirmation. Share reuses the saved file. Export format/settings are locked on the completed export screen. A completed export whose file becomes unavailable is not silently saved as a new copy.
- Bondi is on the left, with a yellow-toned outlined speech bubble on the right. Its left-facing tail points toward Bondi. Rotating factual tips remain.
- Expected pages retains the scroll wheel and adds **Type**. Typed input accepts whole numbers from 1 to 100; Save synchronizes the wheel/preferences and invalid input stays open for correction.

Validation: 45 local JVM tests passed, including typed-count range checks. Existing Android export integration coverage now also checks that a repeated save retains the exact same output URIs; these 17 device tests compiled but were not executed. APK signature and 16 KB zip alignment verified. Phone layout/keyboard testing remains outstanding.

## Version 1.9.1: ID card visibility fix

The ID card background button previously had elevation above its sibling label/icon overlay, hiding both. The button now has zero elevation and its enclosing card carries the subtle shadow. The label and icon retain their grouping and the button retains its ID scan action. Build and 43 JVM checks pass; appearance still awaits phone confirmation.

## Version 1.9.0: compact Home, distinct tools and lighter Review

- **Home:** bounded mode cards replace vertical stretching. Single/Multiple use larger, visually matched icons and unchanged labels; ID has a closely grouped label/icon. The welcome area places Bondi on the right and an automatically changing fact bubble on the left. Bubble updates stop when Home detaches. Card heights adapt to the actual available height, leaving extra space outside the cards. A thin divider separates Next. Home remains non-scrolling.
- **Review:** Original → Crop → Smart crop → Orientation → Flip. The active tool has a pale yellow selection surface, and Smart crop has independently yellow sparkles. Buttons use subtle low-elevation shadows with unclipped parent containers. Existing inline editors remain inline.
- **Crop:** Cancel restores the pre-edit selection; Save commits it. The coordinate fields and graduated angle ruler remain. Saving unchanged values retains prepared output.
- **Orientation:** separate −90°, +90°, −180° and +180° actions. Flip contains only Flip H/Flip V. The document animates directly in the view matrix; repeated taps retarget the current animation instead of queuing work. Disabled system animations apply the result immediately. Final image transforms retain the same crop/flip/rotation pipeline.
- **Processing:** native progress indicators mark active stages; green checks indicate success, red X marks indicate failure, and waiting stages stay neutral. Export assembly is a separate real stage. A failed required stage keeps its red status and recovery explanation visible until Review/Retry; optional OCR failure still permits an image-preserving export.
- **Export:** format selection lives under **⋮ → Export setup**. The selectable-text summary and copy-text button are removed from the main page; recognized PDF text remains. The path has a darker neutral surface, and centered Save / Save & scan again actions have larger side margins.
- **Performance:** Review source decoding and thumbnail decoding now run on the worker executor. Thumbnails use a bounded 4 MiB cache; detected geometry is reused for unchanged original files. Superseded review, process and export-preview bitmaps are released. Crop rendering uses hardware acceleration instead of forcing a software layer. Existing processed-page/OCR reuse and duplicate-operation guards remain. Source/export resolution limits and document cleanup rules are unchanged.

Validation: 43 JVM tests pass, including bounded Home sizing and clockwise/reverse animation wraparound. Android integration tests compile but have not run. Actual frame rate, appearance, memory behavior on large batches, crop Cancel/Save and physical-device quality still require the user's phone acceptance pass; no emulator was started.

## Version 1.8.0: compact controls and inline editing

- **Home:** Bondi returns above larger mode icons and smaller labels. The page uses a fixed, weighted layout without scrolling; the remembered-settings hint is removed.
- **System appearance:** light/dark colors follow the phone setting. Camera and document workspaces remain dark.
- **Camera:** import, camera switch and flash are icon-only controls with accessibility labels. Flash is white when Off and yellow when On. The shutter has an outer white ring and inner disc.
- **Review:** tools are ordered Original, Crop, Smart crop, Flip with refined icons and smaller labels. Crop/Flip replace the bottom controls with inline editors, retaining Apply/Cancel behavior. Fine angle adjustment uses a swipeable graduated ruler. Appearance and Preview use neutral surfaces with shadows; Retake is text-only.
- **Export:** the compact format selector shows PDF/JPG/PNG. A single-page document has no paging arrows; multi-page navigation overlays the document sides at its vertical center. The path is one line with an ellipsis, and the date/timezone footer is removed. Review/Export omit header back arrows; Android Back remains available.

Phone appearance and interaction testing remain outstanding, including short screens, large system fonts and live theme changes.

## Version 1.7.0: approved Bondi model and crop controls

- **Startup only:** the approved Bondi bond-paper mascot in a salakot appears with I-SKAN, the actual installed version, and **Developed by Christian Cabahug**. It advances automatically after 1.8 seconds (tap to continue immediately), and does not reappear when returning Home. Home's old mascot and greeting are removed.
- **Flash:** starts Off and toggles only On/Off. On is yellow; Off is white. The independent Auto toggle still controls automatic shutter capture.
- **Smart crop:** renamed from Auto crop with a sparkle/crop icon. In Review, detected boundaries replace the current selection only when Smart crop is requested; capture may seed the initial selection. The previous 24 dp release-to-detected-point snap was removed. Users can move corners, edge midpoints or the selected area; valid manual adjustments persist immediately. Detection hints do not overwrite a manual selection.
- **Manual crop:** a dedicated grid/loupe editor provides X, Y, Width and Height in preview-source pixels, plus a −45° to +45° crop-selection angle in 0.1° steps. Rotating the selection fits it inside the photo; perspective extraction straightens that selection in the exported page. Angle changes use a stable baseline, avoiding incremental shrinking while scrubbing. Reset starts from the full photo; Sige commits and X/back cancels.
- **Transform:** separate Flip H, Flip V, −90° and +90° controls, without an angle slider. Flips apply before the quarter-turn rotation, consistently in the editor and exported pixels. Reset resets the transform. Transform/crop state survives draft restart and resets on retake.
- **Review hierarchy:** compact 40 dp appearance/Preview controls sit above larger 52 dp Retake/Next actions. Existing distinct colors and icons remain.
- **Processing:** headline **gina gisa pa…**, without a finishing subtitle. **bakal danay ice** is hidden initially and appears only after 15 seconds of the same active processing pass. It disappears on completion/error and does not add any processing delay.
- **Export:** **Save** writes to Downloads without opening a viewer. **Save & scan again** finishes saving successfully, then starts a fresh capture session. A save failure keeps the current document. The Share icon still saves before opening Android's share sheet.

Bondi asset provenance and the generation prompt are in ASSET-SOURCES.md. Device interaction and appearance remain for the user's phone acceptance pass.

## Version 1.6.0: optional OCR and pending-document Home action

Text recognition is now optional per page. Empty results, OCR exceptions, native-library failures and OCR memory errors mark that page **Cannot read text** and continue processing/export with its preserved image. Other readable pages retain selectable text. A text-layer verification mismatch removes the affected page's text layer and rebuilds the document while keeping its image. Image preparation/storage failures still need recovery; an unreadable image file cannot be exported as if it had succeeded.

The processing screen lists **✕ Cannot read text** below the stage checks. The export preview repeats it beneath image/export checks for the affected page (or either side of a combined ID). The state is remembered with the draft and cleared for retry after a retake or crop/appearance change. Switching formats or restarting does not require failed OCR again. No OCR exception/content is logged.

Home uses a yellow **Start new** button when an unsaved document is pending. **Resume** remains available for the current document. Start new creates a fresh capture session using the selected scan settings; it does not silently append to the previous document. Starting fresh replaces the active resumable draft.

The camera **Auto** toggle means automatic shutter capture after stable geometry/lighting/sharpness conditions. Off keeps manual shutter capture and quality guidance. This is separate from **Flash: Auto**.

## Version 1.5.0: review, ID printing and clearer recovery

- **ID print layout:** choose one A4 sheet with front and back stacked, or two A4 sheets with one side on each. Cards are placed at 85.6 × 53.98 mm with a 12 mm gap on the combined page. Select Print layout on Home or ID print layout in the overflow menu. Export previews, PDF page counts, and JPG/PNG counts follow the composed layout. Print at actual size / 100% in the viewer for physical dimensions. Hold an ID thumbnail to swap front/back if imported order needs correcting.
- **Expected pages:** a native scrollable number wheel replaces minus/plus controls.
- **Review:** the source page count overlays the image, leaving the overflow menu at the far right. The appearance selector is purple, Preview blue with an eye icon, Retake red with a camera icon, and Next green. Persistent warning rows were removed; page notices float briefly and fade without moving controls.
- **Previews:** image previews have an accessible top-right X close control. Confirmation acknowledgements say **Sige**; the completion status still says **tapos na po**.
- **Import:** Files allows multiple selection (typically long-press to select, then Open). A multi-selection promotes Single to Multiple and adjusts expected count. ID batches accept up to the two remaining sides; retakes accept one image. Imports identify which selected item failed, preserving earlier pages. System file-picker presentation and selection order depend on the phone/provider.
- **Auto crop:** a spinner and finding-corners overlay appear on the current photo during detection. Explicit Auto crop reports completion or offers manual adjustment when no boundary is clear. ID detection adds a rotated-rectangle fallback for rounded card edges. Manual handles remain available.
- **Failures:** the app identifies the affected page and processing step, with specific guidance for invalid images/crops, write failures, low memory, OCR and PDF text verification. PDF assembly failures are attributed to the document. OCR/PDF failures offer Retry, Review page and an explicit image-only PDF fallback. No raw exception/document text is shown or logged.
- OCR box sorting now uses a consistent ordering, avoiding a comparator contract violation on some dense layouts. PDF verification handles extraction whitespace/Unicode and retains overlapping OCR text. These are potential failure causes addressed in code, not a claim that the user's exact receipt failure was reproduced.

## Version 1.4.0: playful local style

The interface now uses lime, sky blue, mango and lilac accents, rounded mode cards and buttons, dark readable labels, soft elevation, and an original smiling paper character on Home. Camera and document workspaces remain dark so the captured page is easy to inspect. Scan controls and the established flow remain in their familiar positions.

Buttons respond on press with a small scale/position change (90 ms press, 160 ms release). Screen changes fade/settle over 220 ms, and changed feedback labels fade briefly. Input remains available during transitions; no processing delay is added. Android's disabled-animation setting skips motion, including the scanning sweep. Status/navigation bar contrast follows the current workspace.

User-provided Hiligaynon feedback is used as follows:

| Moment | Phrase |
| --- | --- |
| Hold still | pahimuyong |
| Finishing / saving | manog tapos na po |
| Converting / processing | gina gisa pa |
| Too dark | dulom man oi |
| Too bright | ay kasanag! |
| Good camera conditions | perpekt! |
| Done / ready to export | tapos na po |
| Waiting | bakal danay ice |

Camera decision logic retains its internal status values; local wording is a display layer. Specific instructions such as keeping four corners visible remain actionable. The new too-bright feedback requires an almost completely clipped, very bright, low-contrast frame; ordinary white paper with visible ink is not automatically rejected. Thresholds still need phone testing. The processing checklist continues to show actual stages alongside the playful headline. Documents/OCR/export contents are not translated.

## Version 1.3.0: phone-specific cleaner settings

Open **⋮ → Cleaner settings** from Home, Review or Export. Adjust **Paper whitening**, **Shadow cleanup**, **Faint text protection**, and **Color protection**. Values are remembered on this phone, survive restart and carry across scan types. Reset restores the conservative defaults (100%, 50%, 75%, 60% respectively); Cancel leaves the stored values unchanged.

Preview the original and cleaned versions of the same crop/rotation before applying. An uncaptured/saved document without originals uses an explicitly labelled sample page. Previews always start from the original image. Higher shadow cleanup includes darker paper; higher text/color protection excludes more faint or subtly colored content from whitening. Zero whitening leaves image colors untouched. These are user controls, not automatic camera calibration; inspect faint marks at your chosen settings.

The controls apply to **Clean paper** appearance for all export formats. Applying changed cleaner settings to an unsaved export reprocesses its original pages, OCR and PDF before saving. Review drafts are invalidated for the next processing pass. Other appearances retain their behavior. Saved exports remain unchanged; changed settings are used on the next scan.

## Version 1.2.0: icons and assisted cropping

Home now uses a document icon with **Single**, stacked documents with **Multiple**, and an ID icon **below** the **ID scan** label. Single and Multiple stay side by side; ID stays beneath them. Page layout is a single action row: layout icon and label on the left, paper size and color mode separated by a visual divider on the right, followed by a chevron. Flash has Auto/On/Off icons; Import photo and Switch camera have matching vector icons. Icons scale with screen density and keep their text labels.

Review adds **Auto crop** alongside Crop, Rotate and Original. It applies a newly detected document boundary, then keeps the handles editable. Faint dashed edges show the detected boundary without replacing a manual crop. When a dragged corner is released within 24 dp of its matching detected corner, it snaps with haptic feedback, provided the result is a valid quadrilateral. Detection failure leaves the existing crop untouched. The same local OpenCV detector is used for pages and IDs; this is not a newly trained AI model.

## Version 1.1.0: document digitization and ID scans

New scans start with **PDF · selectable text**. The app extracts the completed PDF's text and checks each supported OCR line before committing it. The export screen reports how many pages contain recognized text; **View / copy recognized text** reads the actual PDF text layer and supports long-press selection. This preserves the visual document with an invisible text layer; it does not reconstruct editable typography or invent missing text. OCR mistakes can still occur, so compare copied text with the page.

**Scan ID** sits beneath Single / Multiple. Capture and review the front, choose **Scan back**, then review the back. Both sides export as two card-size PDF pages (85.6 × 53.98 mm). The ID guide uses card proportions. ID defaults to Original color, with no lighting adjustment, watermark, extra margins or page numbers. Retake each side independently; side order stays fixed. This is image capture/OCR, not identity verification.

Review now includes appearance selection and a processed preview:

- **Original color:** crop/rotate only, with no color adjustment. Use for IDs, photos, faint stamps or colored paper.
- **Clean paper:** conservatively turns light neutral paper near the estimated local background white. Distinguishable faint marks and saturated colors remain untouched by its pixel rule. Ambiguous shadows remain rather than forcing the whole page white; it cannot guarantee preservation of marks indistinguishable from paper.
- **Grayscale:** removes color while keeping tones.
- **Black & white:** strict black/white pixels. Optional because thresholding can lose faint marks. Inspect the preview first.

PDF page images now use lossless compression, avoiding new JPEG artifacts and keeping binary page pixels black/white inside the PDF. The file still contains a page image plus selectable OCR text. JPG export necessarily uses JPEG compression; choose PDF or PNG for lossless output. Curved book pages are not automatically unwarped.

## The implemented flow

- **Home:** compact Scan ID, Import File and Scan actions, Page layout, recent saved scans and a separate history page. Unsaved work has Resume draft. Home does not scroll.
- **Capture:** native CameraX preview, icon-only On/Off flash, camera switching, tap focus, manual shutter, optional automatic capture, aspect-ratio guide, detected corners and measured feedback. Capture transitions directly to Review.
- **Review:** Original, Crop, Smart crop, Orientation and Flip tools; movable crop handles and magnifier; inline manual/transform editors; thumbnails; compact appearance/Preview and larger Retake/Next. Retakes replace a page only after receiving its replacement. Next processes the current freeform document; ID continues to its back side. Legacy drafts retain expected-count recovery.
- **Process:** local perspective correction, selected appearance and optional ONNX OCR. Scanning animation accompanies real stages. Originals remain available for retry after an interrupted pass.
- **Export:** actual document preview, larger inspection, editable title, Export setup in the overflow menu, darker automatic folder path with Share, and narrower Save / Scan again actions. Save becomes Exit after success; Exit returns Home and Scan again opens a fresh camera session. Multi-page arrows overlay the preview; a single page needs no pager.

Export formats are PDF, JPG and PNG. PDF retains selectable text on readable pages and preserved images when OCR cannot read a page. Multiple images are saved as numbered files. Exports go automatically to **Download/I-SKAN/** using Android MediaStore. The actual saved filename includes collision suffixes. Share saves successfully before opening Android's standard share sheet. Save does not open a viewer.

## Models and image processing

- Native CameraX 1.5.1 for live preview, analysis and high-resolution still capture.
- OpenCV 4.12.0 for page geometry, brightness/sharpness/motion measurements, crop/perspective and restrained lighting cleanup.
- ONNX Runtime Android 1.23.1 runs the same bundled PP-OCRv4 detector/recognizer and mobile text-orientation classifier used by the laptop build. Chinese and English are supported by the bundled recognizer.
- PDFBox Android 2.0.27.0 creates a genuine invisible OCR text layer. A bundled Noto CJK font supports Chinese and English text. OCR records are in memory, not sidecar files.
- Live condition feedback is conventional CV; it does not claim a learned ONNX quality score. Starting thresholds require calibration with the user's phone/papers. Automatic capture uses sustained measured quality, a cooldown, and scene-change checks to reduce duplicates.

There is no cloud SDK or internet permission. Do not expect guaranteed access to every physical lens, curved-page unwarping, handwriting-specific OCR, a validated glare detector, or automatic whole-page orientation. Manual rotation/cropping and import are supported. Inspect faint content and stamps before choosing black-and-white appearance.

## Recovery and privacy

Captures, ordered pages, crop/rotation and export operational state are written atomically in the app's private storage. OCR text/coordinates are not written to that journal. An interrupted processing pass returns to review/retry; ready image assets are reused and text can be recognized again.

MediaStore items are written pending, checked with content hashes, then published. Incomplete image sets are not accepted as a completed document during recovery. A receipt and log insertion use one SQLite transaction. Repeated saves of the same session or another output format do not insert a second scan entry. Shared/opened copies are counted only through successful saving.

The log content fields are exactly **title**, **page_count**, and **scanned_date**. The scanned date is assigned at first successful capture using Asia/Taipei and survives retakes. Internal session receipts are separate from those three content fields. Title approval is the explicit Save/Share action; after a successful save the title stays locked for that session.

Originals are removed after saving by default. Keep originals retains them longer in private working storage, up to 30 days. Other abandoned drafts/working assets expire after seven days; cleanup runs when the app starts. Exported files stay in Downloads. Uninstalling removes app-private drafts/preferences/logs; back up exported files separately.

No document logging, analytics, network telemetry, OCR sidecars, or document-content exception output is implemented. Native platform/library diagnostics may report technical failures; avoid sharing system diagnostics containing personal data.

## Build and checks

From the project root:

```powershell
.\build-android.ps1
.\build-android.ps1 -Check
```

Alternatively open this android directory in Android Studio. Use JDK 17+ (tested with Android Studio's JBR 21), Android SDK platform 35/build tools, and the included Gradle 8.14.3 wrapper. The script detects the installed Android Studio JBR and standard SDK directory. Dependencies/models are already present in the repository/build output; the first clean build needs Maven/Gradle downloads. The installed app does not download models.

Direct tasks:

```powershell
cd android
.\gradlew.bat assembleDebug testDebugUnitTest assembleDebugAndroidTest lintDebug
```

On a disposable Android test device/emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The instrumentation tests use synthetic fixture pages and a separate test-package context for drafts/logs, and clean up their created exports. They cover actual ONNX recognition, searchable PDF extraction, real phone MediaStore saving/retry, page replacement/order/date, and incomplete-set recovery.

## Validation status — 2026-10-15

- Native phone and emulator APK compilation: passed.
- Sixty-one JVM checks: passed (paper/orientation/dimensions, ID dimensions/two-side configuration, safe defaults, filename safety, font embedding, and conservative paper/ink/color pixel decisions).
- Android lint: 0 errors; warnings remain, mainly English strings in programmatic layouts and related static style notices.
- Twenty-four on-device integration tests (including angle-ruler gestures and day/night resource contrast, near-corner release persistence and flip/rotation pixel verification, mixed readable/unreadable export, combined/separate ID PDF and raster output, rounded ID cropping, exposure feedback, custom cleaner persistence/rendering, detected-boundary/manual-crop safety, PDF text extraction, binary image preservation, ID side persistence and processing fidelity): compiled successfully, **not executed**.
- Package audit: camera is the only device permission; no internet or broad storage permission.
- Bundled models, font and PDF CMap resources: verified.
- arm64 APK: zip alignment and all native PT_LOAD segments checked for 16 KB compatibility.
- Existing laptop integration suite: 15 passed.

**Device runtime validation remains outstanding.** This laptop has no working emulator acceleration; software emulator attempts fail to boot/crash, and no physical Android device was connected. Therefore camera/flash/focus behavior, native ONNX execution, physical-device OCR/PDF alignment, save/open/share intents, rotation/permission recovery, real-paper quality and frame rate are not claimed as device-tested. Install this updated native build on the intended phone for that acceptance pass. Do not mistake the earlier generated concept image for an actual native screenshot.

See THIRD_PARTY_NOTICES.md for dependency/model/font licenses.

