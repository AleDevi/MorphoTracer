# Changelog

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
