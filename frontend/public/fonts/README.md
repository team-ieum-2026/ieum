# Noto Sans KR local web fonts

The original 10,414,588-byte variable TTF is served as two WOFF2 subsets.

- `NotoSansKR-common.woff2`: 583,660 bytes. Preloaded by `next/font/local`;
  Latin, symbols, Jamo and the 2,350 common Korean syllables in KS X 1001.
- `NotoSansKR-extended.woff2`: 3,405,380 bytes. Loaded through
  `src/styles/font-extended.css` only for characters missing from the common subset.

Together they preserve all 23,174 Unicode mappings in the original font and
the variable weight axis from 100 to 900. The OFL license is retained alongside
the fonts. The initial font download is about 94.4% smaller.

To regenerate, obtain the original Noto Sans KR variable TTF from the
[Google Fonts repository](https://github.com/google/fonts/tree/main/ofl/notosanskr).
Its SHA-256 is `194018e6b2b293a7964f037b25c0249ce1418bc9ab3c971060a03aa57861e252`.
With Python and the one-time `fonttools[woff]` build tools available, run:

```text
python src/styles/subset-font.py /path/to/NotoSansKR.ttf
```

The script regenerates both fonts and the extended font's `unicode-range` CSS,
then verifies character coverage and weight ranges. No external font service
or additional frontend dependency is needed at runtime.
