# TODO

## CORE
- option to merge IMS (one peak per m/z), smarter method to choose IMS center (mode? median intensity?)
- *low priority feature* readers for mzML, check for missing data. Remember status lights!
- *low priority feature* migrate todo into a real todo/changelog split to make it easier to track higher-level changes
- **high priority bug** needs testing with PASEF-off TIMS files! Does it even work?
- *low priority bug* precursors with Thermo demux still emit the original spectrum indices and names (not updated to match ordering based on the new numbers for fragments). We should have this match ProteoWizard using the "originalScan=2 demux=0 scan=4" format.

## GUI 
- *low priority bug* where structure visualization only shows DDA ranges that are repeated (rare). However, is it a good idea to plot all DDA ranges? There may be 100k points. Note, Bruker parser doesn't produce range data for DDA.
- *low priority feature* ability to define fixed range for plotting the spectra, both for intensity (max only) and start/stop m/z. This will make it easier to draw figures!
