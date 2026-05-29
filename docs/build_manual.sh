#/bin/sh
md-to-pdf MANUAL.md --pdf-options '{"format": "Letter", "margin": {"top": "1in", "bottom": "1in", "left": "1in", "right": "1in"}, "printBackground": true}'
mv MANUAL.pdf ../gui
