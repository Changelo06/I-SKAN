# Document cleaner: findings and implementation

## What changed

The previous Clean paper mode selectively whitened neutral background pixels; it did not independently tune exposure, brightness, contrast or sharpness. The previous B&W path used one fixed 41-pixel local window and offset 12. The new cleaner gives those decisions explicit controls and named document looks rather than relying on a colored photograph alone.

## Controls and looks

| Control | Range / behavior |
| --- | --- |
| Paper whitening | 0–100%; selectively whitens neutral paper in Clean color and grayscale; also scales B&W illumination normalization |
| Shadow cleanup | 0–100%; controls background eligibility or B&W normalization strength |
| Faint text protection | 0–100%; preserves more faint marks from selective whitening |
| Color protection | 0–100%; preserves tinted areas/stamps in Clean color |
| Digital sharpening | 0–100%; luminance-only unsharp detail, ±24 intensity halo cap and 2-level flat-noise deadband |
| Brightness | −50…+50; signed tone offset |
| Exposure | −2…+2 EV; sRGB linearization, stop gain, re-encoding through a 256-entry LUT |
| Contrast | −50…+50; monotonic contrast around the middle tone |
| B&W ink retention | 0–100%; reduces the adaptive white bias from 16 to 2 as retention increases |

**Document B&W** is a one-tap preset: whitening 100, shadow cleanup 65, faint protection 85, color protection 60, sharpening 25, neutral brightness/exposure, contrast +15 and B&W ink retention 60. It produces strict 0/255 pixels. This is suitable for testing forms/receipts; it can still discard subtle tones, photographs and very faint marks. Inspect the original comparison. Use Soft grayscale for tone-rich content and Clean color for colored stamps. IDs start in Original color, which bypasses every cleaner control unless the user explicitly selects a processed look.

The settings preview is always rebuilt from the captured original. Cancel leaves stored settings unchanged. Apply persists all controls, selects the look, invalidates stale prepared images/OCR for the current unsaved draft, and reprocesses a ready export. Saved exports are unchanged. Neutral new controls preserve older cleaner defaults. Choosing a cleaner look clears the legacy tone-correction strength to avoid stacking the previous and new correction paths.

## Pipeline decisions

1. Geometry correction, source flips and quarter-turn orientation remain unchanged.
2. Clean color / grayscale selectively whiten local paper using a low-resolution background estimate and the existing ink/color guards.
3. Tone controls operate on luminance, with chroma kept separate. One LUT handles exposure, brightness and contrast; there is no separate full-image pass for each slider.
4. Optional sharpening adds bounded detail from a Gaussian-blurred luminance image. No sharpening is done in neutral settings. Small flat-region noise is not boosted. This strengthens existing edges; it does not recover missing letters or repair severe blur.
5. B&W estimates illumination on an image whose long side is at most 700 pixels, then blends bounded background normalization. Local Gaussian thresholding uses a resolution-dependent odd window (21–101 pixels, reduced for tiny images) and the adjustable ink-retention offset. Tiny images use an Otsu fallback. No automatic erosion, dilation of the final ink mask, or connected-component deletion removes fine lines or punctuation. Morphological closing is used only to estimate the illumination field, not to alter the final ink mask.
6. The result feeds the existing OCR and export pipeline. PDF/PNG preserve strict B&W pixels; JPG can introduce compression tones. Selectable PDF text still depends on OCR, independently of how white the page looks.

Adaptive thresholding was chosen because illumination can vary across a photographed sheet. Global thresholding alone would use a single threshold across those different regions. CLAHE was considered but not added as an automatic default: explicit tone controls and bounded edge enhancement provide a simpler adjustment path without another independent local-contrast control.

## Performance and validation

All processing and comparisons use the existing worker executor. Java whitening buffers now scale with row width instead of page area. Sharpening and B&W normalization likewise use small row arrays; native images are released in finally blocks. The illumination estimate is capped at 700 pixels. Neutral enhancement skips luminance conversion. Preview failure releases partially created images. Existing processed-page/OCR reuse and draft invalidation are retained.

59 JVM tests pass, including exact neutral tonal identity, monotonic and clamped tonal curves, signed exposure/brightness, bounded sharpening, tiny-image threshold windows and ink-retention behavior. 21 Android integration tests compile, including Original color bypass, persisted controls, B&W pixels/rules and lossless PDF/OCR verification; they were not executed. Physical Android quality and performance remain unverified.

The reproducible portable experiment is `tools/verify_cleaner_reference.py`. It runs Python OpenCV 4.12 against a synthetic 900×1200 shaded document with text, one-pixel rules, faint marks and a colored stamp. Artifacts are in `output/cleaner-research/`. Its reference B&W output contains only 0 and 255 and retains 100% of the fixture's marked dark text/rule pixels. This is a narrow synthetic result, not an OCR accuracy score or general preservation guarantee. Desktop timing is not a phone benchmark. The comparison image is an algorithm experiment, not an app screenshot.

## Primary references

- OpenCV 4.12 [Image Thresholding](https://docs.opencv.org/4.12.0/d7/d4d/tutorial_py_thresholding.html): local Gaussian thresholds, neighborhood size, offset C and Otsu.
- OpenCV 4.12 [Changing contrast and brightness](https://docs.opencv.org/4.12.0/d3/dc1/tutorial_basic_linear_transform.html): independent gain/offset and nonlinear tone concepts.
- OpenCV 4.12 [Image Filtering](https://docs.opencv.org/4.12.0/d4/d86/group__imgproc__filter.html): Gaussian smoothing and morphology primitives.
