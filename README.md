# MorphoTracer

A Fiji plugin to measure animals by hand on photos (lengths, areas, counts, landmarks, colour spots) and to save the
same marks as annotations for training image-analysis tools such as Guppy Colour.

- **Use it**: [Quick guide](QUICK_GUIDE.md)
- **Everything in detail**: [Manual](MANUAL.md)
- **What changed**: [CHANGELOG](CHANGELOG.md)

## Install

1. Download `MorphoTracer_-<version>.jar` from the [Releases](https://github.com/AleDevi/MorphoTracer/releases) page
   (the `.sha256` file next to it lets you check the download).
2. Copy it into `Fiji.app/plugins` (remove an older `MorphoTracer_-*.jar` first) and restart Fiji.
3. Menu: *Plugins > MorphoTracer*.

Setups are kept in `<your user folder>/.morphotracer/setups`, results next to the photos in `results_<setup>/`; both
stay when the plugin is updated.

## Licence

MorphoTracer is free software under the [GNU General Public License v3.0](LICENSE). It is developed with Guppy Colour
(colour-pattern analysis of fish photos), whose training it can feed with hand annotations.

## Build (developers)

Java 8 bytecode, built with Maven: `mvn package` gives `target/MorphoTracer_-<version>.jar` (tests run on synthetic
ROIs without a window). ImageJ (`net.imagej:ij`) is the only dependency, and Fiji provides it.
GitHub Actions builds and tests every push; pushing a tag `v<version>` publishes a release with the `.jar`, its
`.sha256` and that version's CHANGELOG section.

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
