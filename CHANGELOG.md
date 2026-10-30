# Changelog

## 0.3.0 (2026-10-30)

- **SL and TL as one path**: SL is a segmented line from the snout to the end of the caudal peduncle; its two ends
  become the points *snout* and *peduncle_end* by themselves. TL goes on from the end of SL to the tail tip
  (the line is joined to the end of SL): `tl_mm` = SL + that line, and its end becomes *tail_tip*. Drawing SL again
  moves the points and the TL line with it. In the setup: a Length has *From* / *To* (points set by its ends) and
  *Continues* (the length it is added to).
- **Colour % of the fish**: new column `<class>_percent` = area of the class / area of the parts chosen in the setup
  (*Colour % of parts*; body + caudal in the examples) x 100. Works without a scale too.
- **Turn or mirror the photo**: *Rotate left*, *Rotate right*, *Mirror*, for this photo and (box ticked) the next
  photos of the folder. Only the display turns: marks are saved on the original photo, so masks and coordinates
  stay right. New columns `view` and `view_for_next`.
- Lengths start with the segmented-line tool.
- The example setups are updated (SL/TL path, % of body + caudal). If you had changed one, your version is kept as
  `<name>_before_0_3_0`.

## 0.2.0 (2026-10-30)

- Tool buttons in the panel (line, segmented line, polygon, freehand, point, multi-point): no need to pick them in
  the ImageJ toolbar.
- Marks already made can no longer be picked up and moved by mistake; the marks of the selected item are drawn thicker.
- **A** goes on to the next item for lengths, parts and counts too (colour classes stay, for the next spot).
- The keys (A, N, S, 1-9) also work when the panel is in front, not only the photo (not while typing in Fish id or Notes).
- MorphoTracer reopens the last photo folder at its first photo not done.

## 0.1.0 (2026-10-30)

First version.

- Panel in Fiji (*Plugins > MorphoTracer*): setup, photo folder (with subfolders), photo list with what is done,
  scale, items, notes, *Save fish*.
- Setups with a name, saved and reused: purpose (*Measures*, *Training annotations*, *Both*), annotator, scale mode,
  fish id from the file name, body axis, items of six types (Length, Length from points, Point, Part, Colour class,
  Count). Names can be added from a Guppy Colour profile. Two example setups are included.
- Scale: *Every photo*, *Once per folder*, *Carry forward*; on each photo **S** (this photo), **Shift+S** (also the
  next photos), *No scale possible*.
- Keys on the photo: **A** add, **N** skip a point, **S** scale, **1-9** pick an item; points go on by themselves.
- Results in `results_<setup>/`: `morphology.csv` (one row per fish), `morphometrics/` (`landmarks.csv`,
  `landmarks.tps`, `spots.csv` with the position of every spot along and across the body), `rois/` (every mark,
  reopened to correct a photo), `masks/` (training only).
- Checks at *Save fish*: required items, scale, and in training setups every colour class confirmed complete and the
  photo not cropped or resized.

## Planned

- *Import hand annotations* in Guppy Colour (reads `rois/` or `masks/`).
- Several animals in one photo.
- Fiji update site, so Fiji updates the plugin by itself.
- Own GitHub repository.
