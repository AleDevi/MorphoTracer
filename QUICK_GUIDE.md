# MorphoTracer: Quick guide

Version 0.3.1. What to do, step by step. How it works is in the [Manual](MANUAL.md).

## Install (once)

1. Close Fiji.
2. Copy `MorphoTracer_-0.3.1.jar` into the `plugins` folder of Fiji (`Fiji.app\plugins`).
3. Open Fiji: the menu **Plugins > MorphoTracer** opens the panel.

To update: delete the old `MorphoTracer_-*.jar` from `plugins`, copy the new one, restart Fiji. Setups and results are kept.

## Choose or make a setup

- **Setup** list: pick one. Two examples come with the plugin:
  - `guppy_male_example`: purpose *Both* (measures + training annotations), SL, TL, the guppy landmarks, body, caudal, dorsal, gonopodium, orange, black, iridescent;
  - `measures_only_example`: purpose *Measures*, SL, TL, body, caudal fin, colour areas.
- **Edit...** changes it; **New...** starts an empty one. Saving with a new name keeps the old one (a copy).
- In the setup, choose first the **Purpose**: *Measures*, *Training annotations* or *Both*. Then the scale mode, and the items in the order you will mark them.

## Measure a folder of photos

1. **Photo folder...**: choose the folder (subfolders are included). The first photo not done opens.
2. **View** (if needed): *Rotate left* / *Rotate right* / *Mirror*. With the box ticked the next photos of the folder
   are turned the same way. Only the display turns; the marks are saved on the original photo.
3. **Scale**: with the line tool, draw a line over a known length on the graph paper, press **S**, type the mm (the last value is proposed), Enter.
   - **Shift+S**: the new scale is also used by the next photos.
   - **No scale possible**: sizes stay in pixels, mm columns are `NA`.
   - In *Once per folder* or *Carry forward* mode the next photos already have the scale: check the line *Scale:* and go on.
4. **Items**: the panel selects the first one to do and the right tool. *Tools for <item>* shows only the tools that
   fit it (e.g. *Polygon* or *Freehand* for an area); a mark made with another tool is refused. After each mark it goes on to the next item. For each item:
   - **Point** (landmark): click on the photo, it goes to the next point by itself. Not visible? Press **N**.
   - **SL**: segmented line from the snout tip to the end of the caudal peduncle (click at each bend, double-click
     at the end), **A**. *snout* and *peduncle_end* are set by its ends.
   - **TL**: go on from the end of SL to the tail tip (it is joined to SL), double-click, **A**. TL = SL + this line;
     *tail_tip* is set by its end.
   - Other **Length**: draw a line (straight or segmented), press **A**.
   - **Part** (body, caudal fin...): draw around it with the polygon or freehand tool, press **A**.
   - **Colour class**: draw around one spot, **A**; repeat for every spot of that colour; then **All spots done** (also when the fish has none).
   - **Count**: click each object with the multi-point tool, press **A**.
   - Wrong? **Remove last** removes the last mark of the selected item. Keys **1-9** select an item.
5. **Fish id**: read from the file name if the setup has a pattern, otherwise type it.
6. **Save fish**: it checks what is missing, saves, and opens the next photo. *Save anyway* saves an incomplete fish (marked `complete = FALSE`).

To go on another day: open *Plugins > MorphoTracer*; it reopens the last photo folder at the first photo not done
(or choose the folder again with **Photo folder...** and the same setup). Everything saved is kept.

To correct a fish later: choose it in the photo list (`[x]` done, `[~]` incomplete, `[ ]` to do), change it, **Save fish** again.

## Get the results

In the photo folder, `results_<setup name>\`:

- `morphology.csv`: one row per fish, for `read.csv()` in R. This is the file for the analysis.
- `morphometrics\`: `landmarks.csv`, `spots.csv` (position of every spot), `landmarks.tps` (for geomorph).
- `rois\`: the marks of each photo (open them in Fiji's ROI Manager if needed).
- `masks\` (Training / Both only): images for training Guppy Colour.

## For Guppy Colour training

- Use purpose **Training annotations** or **Both**.
- Trace **every** spot of each colour class, and press **All spots done** for each class.
- Do not crop, rotate or resize the photo in Fiji before marking it.
