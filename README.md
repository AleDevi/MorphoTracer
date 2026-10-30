# MorphoTracer

A Fiji plugin to measure animals by hand on photos (lengths, areas, counts, landmarks, colour spots) and to save the
same marks as annotations for training image-analysis tools such as Guppy Colour.

- **Use it**: [Quick guide](QUICK_GUIDE.md)
- **Everything in detail**: [Manual](MANUAL.md)
- **What changed**: [CHANGELOG](CHANGELOG.md)

## Install

Copy `MorphoTracer_-<version>.jar` into `Fiji.app/plugins` and restart Fiji. Menu: *Plugins > MorphoTracer*.

## Build (developers)

Java 8 bytecode, built with Maven: `mvn package` gives `target/MorphoTracer_-<version>.jar` (tests run on synthetic
ROIs without a window). ImageJ (`net.imagej:ij`) is the only dependency, and Fiji provides it.

| File | What it does |
| --- | --- |
| `MorphoTracer_.java` | the Fiji command; example setups on first start |
| `Panel.java` | the window: photos, scale, items, keys, Save fish |
| `SetupDialog.java` | editing a setup |
| `Setup.java`, `Item.java` | what is measured; JSON load/save; Guppy Colour profile import |
| `Scale.java` | scale of a photo and where it comes from (modes) |
| `Measures.java` | all computations from the ROIs (sizes, areas, counts, points, spot positions) |
| `Store.java` | the results folder: CSV files, TPS, ROI zips, masks |
| `Csv.java`, `Json.java` | small readers/writers (CSV for R; JSON without extra libraries) |
