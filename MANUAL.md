# MorphoTracer: Manual

Version 0.3.1. The short step-by-step version is the [Quick guide](QUICK_GUIDE.md).

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
| Colour % of parts | Part items whose total area is 100% for `<class>_percent` (e.g. `body, caudal`); empty for no percentage |
| Items | what is marked on every photo, in this order |

**Add names from a Guppy Colour profile...** adds the landmarks, body parts and colour classes of a Guppy Colour
profile (`guppycolor/profiles/*.json`) with the same names the app uses. It is optional: any name can be typed.

### Item types

| Type | What you draw | Result in `morphology.csv` | Other files |
| --- | --- | --- | --- |
| Length | a straight line, or a segmented line to follow a bent animal | `<name>_mm`: the length of the line (plus the length it continues) | its ends can set two Point items |
| Length from points | nothing: it is the straight distance between two Point items (From, To) | `<name>_mm` | |
| Point | one click | none | one row in `landmarks.csv`, a point in `landmarks.tps` |
| Part | a polygon or freehand outline; several pieces are summed | `<name>_area_mm2` | the outline in `rois`, masks for training |
| Colour class | one outline per spot | `<name>_area_mm2` (sum of the spots), `<name>_n` (number of spots), `<name>_percent` (when the setup has *Colour % of parts*), and in training setups `<name>_complete` | one row per spot in `spots.csv` |
| Count | several clicks with the multi-point tool | `<name>_n` | |

*Required* items are checked at *Save fish*. Optional ones may be left out.

### Lengths that set points and continue each other

A Length can have:

- **From** / **To**: Point items set by the start and the end of the line. They are placed (or moved) each time the
  line is drawn, and are then done in the list; they can still be clicked by hand afterwards.
- **Continues**: another Length it is added to. The new line is joined to the end of that length (its first vertex is
  moved there), and its value is the sum of the two.

The guppy example uses this for SL and TL, following the fish's shape: SL is a segmented line from the snout tip to
the end of the caudal peduncle (*From* = snout, *To* = peduncle_end); TL *Continues* SL from the end of the peduncle to
the tail tip (*To* = tail_tip), so `tl_mm` = `sl_mm` + the tail line. Drawing SL again moves the points and the start of
the TL line with it.

### Colour as a percentage of the animal

*Colour % of parts* lists the Part items whose total area counts as 100% (body and caudal in the guppy example). Each
colour class then gets `<class>_percent` = 100 x class area / total area of those parts. It is a ratio of pixels, so
it needs no scale. It is `NA` when one of those parts is not drawn, or when the class is unknown (no spot and not
confirmed *All spots done*).

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

- Clicking an item (or pressing 1-9) selects the right ImageJ tool and writes what to do under the list. The
  *Tools for <item>* buttons switch to another tool that fits it (e.g. *Segmented line* for a bent fish, *Freehand*
  for a spot edge); the others are greyed out.
- Each item accepts only its own kind of selection: Point items only a point (Point tool), Lengths only a straight or
  segmented line, Parts and Colour classes only a polygon or freehand outline (not a rectangle, oval or wand
  selection), Counts only points (Multi-point). Anything else is refused with a message and not added, so an area
  cannot be traced by mistake with the segmented line.
- **A** adds the current selection to the selected item, with its name (`body`, `orange_3`, `dorsal_base`) and the
  item's colour. Points are added as soon as they are clicked. After a point, a length, a part or a count the panel
  moves to the next item; after a spot it stays on the colour class for the next spot.
- A new Length, Point or Count replaces the old one of the same item; a new Part piece or spot is added to the others.
- **Remove last** removes the last mark of the selected item. **N** skips an optional point.
- Marks are drawn over the photo (overlay), with their names; those of the selected item are thicker. They cannot be
  selected or moved by clicking: to change one, select its item and *Remove last*. The photo itself is never changed.
- The keys A, N, S and 1-9 work when the photo or the panel is in front (not while typing in Fish id or Notes); they
  are not passed on to ImageJ.
- At start MorphoTracer reopens the last photo folder, at its first photo not done.

Colours in the list: `[x]` done, `[!]` required and missing, `[ ]` optional and missing. Photo list: `[x]` complete,
`[~]` saved but incomplete, `[ ]` not done.

## 6. View: turning or mirroring a photo

*Rotate left*, *Rotate right* (90 degrees) and *Mirror* (left-right) change how the photo is shown, e.g. to have every
fish with the head on the left. With *also for the next photos of this folder* ticked, the next photos of that folder
open turned the same way (also in a later session); unticked, it is for this photo only.

Only the display is turned. Every mark is converted back and saved in the coordinates of the original photo file, so
`landmarks.csv`, `spots.csv`, `rois` and `masks` always match the original photo, and turning a photo after marking it
changes nothing in the results. Lengths and areas do not change with the view. `morphology.csv` records `view`
(`none`, `r90`, `r180`, `r270`, `m`, `m+r90`...: mirrored first, then turned clockwise) and `view_for_next`.

## 7. Save fish

*Save fish* lists what is missing: required items, the scale, and in training setups the colour classes not confirmed
complete. **Go back** returns to the photo; **Save anyway** saves it with `complete = FALSE` and the problems in
`flags`. Then the next photo opens.

Moving to another photo with unsaved changes asks whether to save them.

## 8. Results

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
| `view`, `view_for_next` | how the photo was shown (section 6) |
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

## 9. Good practice

- Do not crop, rotate or resize photos with Fiji's own menus before marking: the coordinates must be those of the
  original file. Use MorphoTracer's *View* buttons to turn a photo instead.
- Zoom in (`+`/`-` keys, or the scroll wheel with Ctrl) to trace spot edges; small errors on the edge add up on small spots.
- Measure everything with the same setup; if the setup has to change mid-way, make a new one, so every results folder
  says how it was measured.
- For training, a smaller number of fully traced fish is worth more than many partly traced ones.

## 10. Files and security

MorphoTracer writes only to the results folder inside the chosen photo folder, to `<your user folder>\.morphotracer\`
(the setups) and to Fiji's preferences (last setup, last scale length). It does not use the network and does not
install anything. It needs no administrator rights.
