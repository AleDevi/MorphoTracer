# MorphoTracer: Manual

Version 0.1.0. The short step-by-step version is the [Quick guide](QUICK_GUIDE.md).

## 1. What it is for

MorphoTracer is a Fiji plugin for measuring animals by hand on photos. It serves two purposes, which can overlap:

1. **Measures**: lengths, areas and counts per individual, for the analysis, as one would take them in ImageJ, but
   always with the same names, the same order and one table at the end.
2. **Training annotations**: the same marks saved with their full coordinates, so that an automatic tool (Guppy
   Colour, or any other) can learn from them and be checked against them.

Every mark is kept as an ImageJ ROI in the photo's own pixel coordinates, so a measure can always be traced back to
what was drawn, corrected later, or computed again in a different way.

It is generic: nothing is specific to guppies. The items to measure, their names and their number are set by the
user in a *setup*. The guppy male example is only an example.

## 2. Setups

A setup is a named way of measuring (e.g. `guppy_morpho`, `guppy_morpho_training`, `zebra_col`). It is a JSON file
in `<your user folder>\.morphotracer\setups\`; it can be copied to a colleague. A copy is also written into each
results folder (`setup.json`), so the results always say how they were measured.

| Field | Meaning |
| --- | --- |
| Setup name | letters, digits, `_` and `-` |
| Purpose | *Measures*, *Training annotations* or *Both* (section 3) |
| Annotator | written in every row of `morphology.csv` |
| Scale | scale mode (section 4) |
| Known length (mm) | proposed when a scale line is drawn (the last value used is proposed after that) |
| Fish id from file name | optional regular expression; the part in `( )`, or the whole match, is the id. E.g. `M\d+` finds `M103` in `IMG_9860_M103.JPG`; `(\d+)_` finds `9860` in `9860_left.jpg`. A test field shows the result |
| Body axis from / to point | two Point items (e.g. `snout`, `peduncle_end`): the axis used for spot positions |
| Body part for depth | a Part item (e.g. `body`): used for the position of spots across the body |
| Items | what is marked on every photo, in this order |

**Add names from a Guppy Colour profile...** adds the landmarks, body parts and colour classes of a Guppy Colour
profile (`guppycolor/profiles/*.json`) with the same names the app uses. It is optional: any name can be typed.

### Item types

| Type | What you draw | Result in `morphology.csv` | Other files |
| --- | --- | --- | --- |
| Length | a straight line, or a segmented line to follow a bent animal | `<name>_mm`: the length of the line | |
| Length from points | nothing: it is the straight distance between two Point items (From, To) | `<name>_mm` | |
| Point | one click | none | one row in `landmarks.csv`, a point in `landmarks.tps` |
| Part | a polygon or freehand outline; several pieces are summed | `<name>_area_mm2` | the outline in `rois`, masks for training |
| Colour class | one outline per spot | `<name>_area_mm2` (sum of the spots), `<name>_n` (number of spots), and in training setups `<name>_complete` | one row per spot in `spots.csv` |
| Count | several clicks with the multi-point tool | `<name>_n` | |

*Required* items are checked at *Save fish*. Optional ones may be left out.

## 3. Purpose

The purpose is chosen first because it decides the rules and what is saved.

| | Measures | Training annotations | Both |
| --- | --- | --- | --- |
| Colour classes | trace the spots you want to measure; *All spots done* says that a class with no spot is 0, not unknown | every spot of each class must be traced, and each class confirmed with *All spots done* | as Training |
| Cropped / resized photo | warning, saved as incomplete | refused: the marks would not match the original photo | refused |
| `<class>_complete` columns | no | yes | yes |
| `masks\` | no | yes | yes |

Why the stricter rules: a model learns from what is marked *and* from what is not. A spot left out would be taught as
"skin without colour". So for training every spot of a class must be there, and the person says so explicitly.

## 4. Scale

The scale is pixels per mm, from a line drawn over a known length (e.g. 10 mm of graph paper).

**Default mode** (in the setup):

| Mode | What happens |
| --- | --- |
| Every photo | each photo needs its own scale line |
| Once per folder | the first line drawn in a folder is used by every other photo of that folder |
| Carry forward | each photo uses the last scale used, until a new one is drawn |

**On each photo**:

- **S**: draw a scale for this photo only. If the photo had no scale yet and the mode is *Once per folder* or
  *Carry forward*, it also becomes the scale for the next photos.
- **Shift+S**: draw a scale and make it the scale for the next photos.
- **No scale possible**: no scale; mm columns are `NA` (the marks and their pixel coordinates are still saved).

The line *Scale:* in the panel always says which scale is in use and where it comes from. `morphology.csv` records
`px_per_mm`, `scale_source` (`drawn`, `drawn_shared`, `folder`, `carried`, `none`) and `scale_photo` (the photo on
which the line was drawn). Because the line is kept in `rois`, a wrong scale can be found and redrawn later.

A photo that was already saved keeps its own scale when it is opened again, whatever the mode.

## 5. Marking a photo

- Clicking an item (or pressing 1-9 on the photo) selects the right ImageJ tool and writes what to do under the list.
- **A** adds the current selection to the selected item, with its name (`body`, `orange_3`, `dorsal_base`) and the
  item's colour. Points are added as soon as they are clicked, and the panel moves to the next item.
- A new Length, Point or Count replaces the old one of the same item; a new Part piece or spot is added to the others.
- **Remove last** removes the last mark of the selected item. **N** skips an optional point.
- Marks are drawn over the photo (overlay), with their names. The photo itself is never changed or saved.
- The keys A, N, S and 1-9 work when the photo window is in front; they are not passed on to ImageJ.

Colours in the list: `[x]` done, `[!]` required and missing, `[ ]` optional and missing. Photo list: `[x]` complete,
`[~]` saved but incomplete, `[ ]` not done.

## 6. Save fish

*Save fish* lists what is missing: required items, the scale, and in training setups the colour classes not confirmed
complete. **Go back** returns to the photo; **Save anyway** saves it with `complete = FALSE` and the problems in
`flags`. Then the next photo opens.

Moving to another photo with unsaved changes asks whether to save them.

## 7. Results

The results of a setup are in the photo folder, in `results_<setup name>\`. Nothing else is written there, and the
photos are never changed.

```
results_<setup>/
  morphology.csv        one row per fish: the file for the analysis
  setup.json            copy of the setup used
  morphometrics/        only when the setup has points or colour classes
    landmarks.csv       one row per point
    landmarks.tps       the points for geomorph
    spots.csv           one row per spot
  rois/<photo>.zip      every mark of a photo (scale line included)
  masks/                only for Training / Both
    <photo>_parts.png
    <photo>_colours.png
```

All CSV files use a comma, a decimal point and `NA` for missing values, so `read.csv("morphology.csv")` reads them in
R. Text starting with `=`, `+`, `-` or `@` gets a leading `'` so Excel does not run it as a formula.

### morphology.csv

| Column | Content |
| --- | --- |
| `photo`, `folder` | file name, and its subfolder (`.` for the chosen folder itself) |
| `fish_id` | from the file name pattern or typed |
| `setup`, `purpose`, `annotator`, `date` | how, by whom and when (date and time of the last save) |
| `image_width`, `image_height` | size of the photo in pixels |
| `px_per_mm`, `scale_source`, `scale_photo` | the scale (section 4) |
| one block per item | section 2, in the setup's order |
| `complete` | `TRUE` when nothing was missing at *Save fish* |
| `flags` | what was missing, separated by `;` |
| `notes` | the Notes field |

Areas are counted as ImageJ's *Measure* counts them: the pixels inside the outline, divided by `px_per_mm²`.

### landmarks.csv and landmarks.tps

`photo, folder, fish_id, landmark, x_px, y_px, x_mm, y_mm`: pixel coordinates from the top-left corner of the photo;
mm are the same divided by `px_per_mm`.

`landmarks.tps` has one block per photo with every Point item of the setup in its order; `y` is measured upward
(`image_height - y`), as in tpsDig; a missing point is written `-1 -1`, so read it in R with
`geomorph::readland.tps("landmarks.tps", specID = "ID", negNA = TRUE)`. `SCALE` is mm per pixel.

### spots.csv

`photo, folder, fish_id, class, spot, area_px, area_mm2, centroid_x_px, centroid_y_px, along_sl, along_mm, across_depth`

- `centroid_*`: centre of the spot's pixels.
- `along_sl`: position of the centre along the body axis (setup: *Body axis from / to*), as a fraction of the axis
  length: 0 at the first point (e.g. snout), 1 at the second (e.g. end of the caudal peduncle). With the guppy example
  the axis is SL, so 0.5 is mid-body. Values below 0 or above 1 are before or after the axis.
- `along_mm`: the same distance in mm.
- `across_depth`: position across the body, as a fraction of the body depth at that point, measured on the body part
  of the setup: 0 at the body edge on the left of the axis direction, 1 at the other edge. For an animal with the head
  on the left and the back up this is 0 at the back and 1 at the belly.
- They are `NA` when the axis points or the body part are missing.

### rois and masks

`rois/<photo>.zip` is a normal ImageJ ROI set: *ROI Manager > More > Open* shows it on the photo. Each ROI has the
item's name and a property `mt_item` with the item it belongs to. These files are what MorphoTracer reads to reopen a
photo, and what another tool can import.

`masks/<photo>_parts.png` and `_colours.png` (training only) are 8-bit images the size of the photo: 0 = nothing,
then 1, 2, 3... for the Part (or Colour class) items in the setup's order. Where parts overlap, the later one wins.

## 8. Good practice

- Do not crop, rotate or resize photos in Fiji before marking: the coordinates must be those of the original file.
- Zoom in (`+`/`-` keys, or the scroll wheel with Ctrl) to trace spot edges; small errors on the edge add up on small spots.
- Measure everything with the same setup; if the setup has to change mid-way, make a new one, so every results folder
  says how it was measured.
- For training, a smaller number of fully traced fish is worth more than many partly traced ones.

## 9. Files and security

MorphoTracer writes only to the results folder inside the chosen photo folder, to `<your user folder>\.morphotracer\`
(the setups) and to Fiji's preferences (last setup, last scale length). It does not use the network and does not
install anything. It needs no administrator rights.
