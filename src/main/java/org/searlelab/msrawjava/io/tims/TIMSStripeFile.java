package org.searlelab.msrawjava.io.tims;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.searlelab.msrawjava.model.FragmentScan;
import org.searlelab.msrawjava.model.PrecursorScan;
import org.searlelab.msrawjava.model.Range;
import org.searlelab.msrawjava.model.SpectrumRecord;
import org.searlelab.msrawjava.model.StripeFileInterface;
import org.searlelab.msrawjava.model.WindowData;

import gnu.trove.list.array.TIntArrayList;

/**
 * TIMS-backed implementation that reads frame metadata via sqlite-jdbc and
 * pulls peak arrays via the Rust JNI iterator. Java controls what to read.
 *
 */
public class TIMSStripeFile implements StripeFileInterface, AutoCloseable {

    private final Path dPath;
    private final File fileObj;
    private final String originalFileName;
    private final Connection conn;
    private final TimsReader reader;
    private volatile boolean open = true;
    private final int ms1Key;
    private final int ms2Key;

    public TIMSStripeFile(Path dPath) throws SQLException {
        Objects.requireNonNull(dPath, "dPath");
        this.dPath = dPath;
        this.fileObj = dPath.toFile();
        this.originalFileName = dPath.getFileName().toString();
        String url = "jdbc:sqlite:" + dPath.resolve("analysis.tdf").toAbsolutePath();
        this.conn = DriverManager.getConnection(url);
        this.conn.setAutoCommit(false);
        this.reader = TimsReader.open(dPath);
        
        String sql = "SELECT MsMsType, COUNT(*) FROM Frames GROUP BY MsMsType ORDER BY MsMsType";
        Map<Integer,Integer> hist = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) hist.put(rs.getInt(1), rs.getInt(2));
        }
        int expectedMS1 = hist.getOrDefault(0, 0);  // MsMsType=0 → MS1
    	int expectedMS2=0;
        int expectedMS1Key=0;
        int expectedMS2Key=-1;
        
    	if (hist.containsKey(Integer.valueOf(9))) {
        	expectedMS2Key=9;
           	expectedMS2=hist.getOrDefault(expectedMS2Key, 0);
        } else if (hist.containsKey(Integer.valueOf(8))) {
        	expectedMS2Key=8;
           	expectedMS2=hist.getOrDefault(expectedMS2Key, 0);
        } else {
            for (Entry<Integer, Integer> tally : hist.entrySet()) {
            	if (!Integer.valueOf(0).equals(tally.getKey())) {
            		Integer value = tally.getValue();
					
            		if (value!=null&&expectedMS2>value) {
            			expectedMS2=value;
            			expectedMS2Key=tally.getKey();
            		}
            	}
			}
        }
        if (expectedMS1==0) System.err.println("No MS1s found!");
        if (expectedMS2==0) System.err.println("No MS2s found!");
        ms1Key=expectedMS1Key;
        ms2Key=expectedMS2Key;
    }

    /** Histogram of MsMsType values present. */
    public Map<Integer, Integer> msmsTypeHistogram() throws SQLException {
        String sql = "SELECT MsMsType, COUNT(*) FROM Frames GROUP BY MsMsType ORDER BY MsMsType";
        Map<Integer,Integer> out = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.put(rs.getInt(1), rs.getInt(2));
        }
        return out;
    }
    
    public Range getRtRange() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT MIN(Time), MAX(Time) FROM Frames");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return new Range(0, Float.MAX_VALUE);
            return new Range(rs.getDouble(1), rs.getDouble(2));
        }
    }
    
    @Override
    public void openFile(File userFile) throws IOException, SQLException {
    	// FIXME Auto-generated method stub
    }

    // ------------------------------------------------------------
    // Public helpers commonly expected from StripeFileInterface
    // ------------------------------------------------------------

    /** Return DIA stripe boundaries and stats; empty for datasets without DIA. */
    public Map<Range, WindowData> getRanges() {
    	try {
	        if (!tableExists("DiaFrameMsMsWindows") || !tableExists("DiaFrameMsMsInfo")) {
	            return Collections.emptyMap();
	        }
	        // Gather all windows with their frame times
	        String sql = "SELECT F.Id AS FrameId, F.Time AS RT, W.IsolationMz, W.IsolationWidth, " +
	                     "       W.ScanNumBegin, W.ScanNumEnd " +
	                     "FROM Frames F " +
	                     "JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id " +
	                     "JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup " +
	                     "WHERE F.MsMsType = "+ ms2Key + " " +
	                     "ORDER BY W.IsolationMz ASC, F.Time ASC";
	
	        Map<Range, List<Double>> rtByRange = new LinkedHashMap<>();
	        Map<Range, int[]> scanRangeByRange = new HashMap<>();
	
	        try (PreparedStatement ps = conn.prepareStatement(sql);
	             ResultSet rs = ps.executeQuery()) {
	            while (rs.next()) {
	                double isoMz = rs.getDouble("IsolationMz");
	                double width = rs.getDouble("IsolationWidth");
	                int sLo = rs.getInt("ScanNumBegin");
	                int sHi = rs.getInt("ScanNumEnd");
	                double lo = isoMz - 0.5 * width;
	                double hi = isoMz + 0.5 * width;
	                Range r = new Range((float) lo, (float) hi);
	                rtByRange.computeIfAbsent(r, k -> new ArrayList<>()).add(rs.getDouble("RT"));
	                scanRangeByRange.putIfAbsent(r, new int[]{sLo, sHi});
	            }
	        }
	
	        Map<Range, WindowData> out = new LinkedHashMap<>();
	        for (Map.Entry<Range, List<Double>> e : rtByRange.entrySet()) {
	            Range r = e.getKey();
	            List<Double> rts = e.getValue();
	            // average duty cycle: mean delta between consecutive RTs for this window
	            float avgCycle = 0f;
	            if (rts.size() >= 2) {
	                double sum = 0.0;
	                for (int i = 1; i < rts.size(); i++) sum += (rts.get(i) - rts.get(i - 1));
	                avgCycle = (float) (sum / (rts.size() - 1));
	            }
	            int count = rts.size();
	            // Represent IM range as scan index range if present
	            Optional<Range> imRange = Optional.empty();
	            int[] scans = scanRangeByRange.get(e.getKey());
	            if (scans != null) {
	                imRange = Optional.of(new Range((float) scans[0], (float) scans[1]));
	            }
	            out.put(r, new WindowData(avgCycle, count, imRange));
	        }
	        return out;
    	} catch (SQLException e) {
    		throw new RuntimeException(e); //FIXME
    	}
    }
    
    /**
     * returns total precursor ion current across entire file
     */
    @Override
    public float getTIC() throws IOException, SQLException {
        ensureOpen();
        final String sql = "SELECT COALESCE(SUM(SummedIntensities), 0.0) FROM Frames WHERE MsMsType = "+ms1Key;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return (float) rs.getDouble(1);
            }
            return 0f;
        }
    }

    /**
     * returns the time in seconds between the first scan and the last scan
     */
    @Override
    public float getGradientLength() throws IOException, SQLException {
        ensureOpen();
        final String sql = "SELECT MIN(Time), MAX(Time) FROM Frames";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                double tMin = rs.getDouble(1);
                double tMax = rs.getDouble(2);
                // Guard against nulls or weird ordering
                if (rs.wasNull()) return 0f; // only checks last read column, but both MIN/MAX on empty give NULL
                return (float) Math.max(0.0, tMax - tMin);
            }
            return 0f;
        }
    }
    
    /** Read MS1 precursor scans within an RT window. */
    @Override
    public ArrayList<PrecursorScan> getPrecursors(float rtStart, float rtEnd)
            throws SQLException, IOException, java.util.zip.DataFormatException {
        ensureOpen();

        final int[] frameIds = selectFramesByTypeAndRt(ms1Key, rtStart, rtEnd); // ms1Key = 0
        final ArrayList<PrecursorScan> out = new ArrayList<>(frameIds.length);
        if (frameIds.length == 0) return out;

        // Zero-based indices for the native reader, parallel to frameIds order
        final int[] indices = java.util.Arrays.stream(frameIds).map(id -> id - 1).toArray();

        // Accumulation time and RT lookup by 1-based Id
        final java.util.Map<Integer, Float> accTimes = fetchAccumulationTimes(frameIds);
        final java.util.Map<Integer, Float> rtMap = new java.util.HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT Id, Time FROM Frames WHERE Id IN (" +
                        java.util.Arrays.stream(frameIds)
                                .mapToObj(i -> "?")
                                .collect(java.util.stream.Collectors.joining(",")) + ")")) {
            int j = 1; for (int id : frameIds) ps.setInt(j++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rtMap.put(rs.getInt(1), (float) rs.getDouble(2));
            }
        }

        // Read all spectra once. Map by zero-based frame index returned from native.
        final java.util.HashMap<Integer, SpectrumRecord> byIndex = new java.util.HashMap<>(frameIds.length * 2);
        final double mzLo = 10.0, mzHi = 10000.0;
        try (RustIterator it = reader.createIterator(indices, mzLo, mzHi, -1, -1)) {
            for (SpectrumRecord s; (s = it.next()) != null; ) {
                // s.frameIndex is the zero-based frame index in the dataset
                byIndex.put(s.frameIndex, s);
            }
        }

        // Emit exactly one PrecursorScan per requested frame, preserving RT order
        for (int k = 0; k < frameIds.length; k++) {
            final int frameId = frameIds[k];          // 1-based
            final int frameIdx0 = indices[k];         // 0-based
            final String name = Integer.toString(frameId);
            final float injTime = accTimes.getOrDefault(frameId, 0f)/1000f; //msec to sec
            final float rt = rtMap.getOrDefault(frameId, Float.NaN);

            final SpectrumRecord s = byIndex.get(frameIdx0);
            if (s != null) {
                // Use the real spectrum
                out.add(new PrecursorScan(
                        name, frameId, (float) s.rtSeconds, 0,
                        0.0, Double.POSITIVE_INFINITY,
                        injTime,
                        s.mz, s.intensity, s.ims
                ));
            } else {
                // Synthesize an empty spectrum for parity with Frames
                out.add(new PrecursorScan(
                        name, frameId, rt, 0,
                        0.0, Double.POSITIVE_INFINITY,
                        injTime,
                        new double[0], new float[0], new float[0]
                ));
            }
        }

        // Already in RT order because selectFramesByTypeAndRt orders by Time
        return out;
    }

    public boolean isOpen() { return open; }

    public File getFile() { return fileObj; }

    public String getOriginalFileName() { return originalFileName; }

    @Override
    public void close() {
        if (!open) return;
        try { reader.close(); } catch (Exception ignore) {}
        try { conn.close(); } catch (Exception ignore) {}
        open = false;
    }

    // ------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------

    private void ensureOpen() throws IOException {
        if (!open) throw new IOException("TIMSStripeFile is closed");
    }

    private boolean tableExists(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private int[] selectFramesByTypeAndRt(int msmsType, double rtStart, double rtEnd) throws SQLException {
        String sql = "SELECT Id FROM Frames WHERE MsMsType = ? AND Time BETWEEN ? AND ? ORDER BY Time ASC";
        TIntArrayList ids = new TIntArrayList();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, msmsType);
            ps.setDouble(2, rtStart);
            ps.setDouble(3, rtEnd);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids.toArray();
    }

    private Map<Integer, Float> fetchAccumulationTimes(int[] frameIds) throws SQLException {
        if (frameIds.length == 0) return Map.of();
        String inClause = Arrays.stream(frameIds).mapToObj(i -> "?").collect(Collectors.joining(","));
        String sql = "SELECT Id, AccumulationTime FROM Frames WHERE Id IN (" + inClause + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (int id : frameIds) ps.setInt(idx++, id);
            Map<Integer, Float> map = new HashMap<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) map.put(rs.getInt(1), (float) rs.getDouble(2));
            }
            return map;
        }
    }
    
    /**
     * metadata map for experiment
     */
    @Override
    public Map<String, String> getMetadata() throws IOException, SQLException {
        ensureOpen();
        LinkedHashMap<String, String> out = new LinkedHashMap<>();

        // File basics
        out.put("file.path", dPath.toAbsolutePath().toString());
        out.put("file.name", originalFileName);

        // --- Frames summary ---
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*), MIN(Time), MAX(Time), " +
                "       SUM(CASE WHEN MsMsType=0 THEN 1 ELSE 0 END), " +   // MS1
                "       SUM(CASE WHEN MsMsType=8 THEN 1 ELSE 0 END), " +   // DDA MS2
                "       SUM(CASE WHEN MsMsType=9 THEN 1 ELSE 0 END) " +    // DIA MS2
                "FROM Frames")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt(1);
                    double tMin = rs.getDouble(2);
                    double tMax = rs.getDouble(3);
                    int nMS1 = rs.getInt(4);
                    int nDDA = rs.getInt(5);
                    int nDIA = rs.getInt(6);
                    out.put("frames.total", Integer.toString(total));
                    out.put("rt.start.s", Double.toString(tMin));
                    out.put("rt.end.s", Double.toString(tMax));
                    out.put("rt.length.s", Double.toString(Math.max(0.0, tMax - tMin)));
                    out.put("frames.ms1", Integer.toString(nMS1));
                    out.put("frames.ms2.dda", Integer.toString(nDDA));
                    out.put("frames.ms2.dia", Integer.toString(nDIA));
                }
            }
        }

        // Average accumulation time by class (seconds = 1000 * AccumulationTime, per your convention)
        if (columnsOf("Frames").contains("AccumulationTime")) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT " +
                    "AVG(CASE WHEN MsMsType=0 THEN AccumulationTime END), " +
                    "AVG(CASE WHEN MsMsType=8 THEN AccumulationTime END), " +
                    "AVG(CASE WHEN MsMsType=9 THEN AccumulationTime END) " +
                    "FROM Frames")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double ms1 = rs.getDouble(1);
                        double dda = rs.getDouble(2);
                        double dia = rs.getDouble(3);
                        out.put("accTime.avg.ms1.s", Double.toString(1000.0 * ms1));
                        out.put("accTime.avg.ms2.dda.s", Double.toString(1000.0 * dda));
                        out.put("accTime.avg.ms2.dia.s", Double.toString(1000.0 * dia));
                    }
                }
            }
        }

        // Distinct TIMS stacks (useful to know multiplexing or merged acquisitions)
        if (columnsOf("Frames").contains("TimsId")) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(DISTINCT TimsId) FROM Frames");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) out.put("tims.distinctIds", Integer.toString(rs.getInt(1)));
            }
        }

        // --- DIA window summary (if present) ---
        if (tableExists("DiaFrameMsMsWindows")) {
            Set<String> cols = columnsOf("DiaFrameMsMsWindows");
            boolean hasWg = cols.contains("WindowGroup");
            boolean hasIso = cols.contains("IsolationMz") && cols.contains("IsolationWidth");
            if (hasIso) {
                String cntSql = "SELECT COUNT(*) FROM DiaFrameMsMsWindows";
                String wgSql  = hasWg ? "SELECT COUNT(DISTINCT WindowGroup) FROM DiaFrameMsMsWindows" : null;
                String spanSql = "SELECT " +
                        "MIN(IsolationMz - 0.5*IsolationWidth), " +
                        "MAX(IsolationMz + 0.5*IsolationWidth), " +
                        "AVG(IsolationWidth) " +
                        "FROM DiaFrameMsMsWindows";
                try (PreparedStatement ps1 = conn.prepareStatement(cntSql);
                     ResultSet r1 = ps1.executeQuery()) {
                    if (r1.next()) out.put("dia.windows.count", Integer.toString(r1.getInt(1)));
                }
                if (wgSql != null) try (PreparedStatement ps2 = conn.prepareStatement(wgSql);
                                        ResultSet r2 = ps2.executeQuery()) {
                    if (r2.next()) out.put("dia.windowGroups.count", Integer.toString(r2.getInt(1)));
                }
                try (PreparedStatement ps3 = conn.prepareStatement(spanSql);
                     ResultSet r3 = ps3.executeQuery()) {
                    if (r3.next()) {
                        out.put("dia.mz.min", Double.toString(r3.getDouble(1)));
                        out.put("dia.mz.max", Double.toString(r3.getDouble(2)));
                        out.put("dia.window.avgWidth", Double.toString(r3.getDouble(3)));
                    }
                }
            }
        }

        // --- DDA isolation summary (if present) ---
        if (tableExists("PasefFrameMsMsInfo")) {
            Set<String> cols = columnsOf("PasefFrameMsMsInfo");
            if (cols.containsAll(Set.of("IsolationMz", "IsolationWidth"))) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*), " +
                        "MIN(IsolationMz - 0.5*IsolationWidth), " +
                        "MAX(IsolationMz + 0.5*IsolationWidth), " +
                        "AVG(IsolationWidth) " +
                        "FROM PasefFrameMsMsInfo");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        out.put("dda.targets.count", Integer.toString(rs.getInt(1)));
                        out.put("dda.mz.min", Double.toString(rs.getDouble(2)));
                        out.put("dda.mz.max", Double.toString(rs.getDouble(3)));
                        out.put("dda.window.avgWidth", Double.toString(rs.getDouble(4)));
                    }
                }
            }
        }

        // --- Global key/value stores (if present) ---
        if (tableExists("GlobalMetadata")) {
            Set<String> cols = columnsOf("GlobalMetadata");
            String keyCol = cols.contains("Key") ? "Key" : cols.contains("Tag") ? "Tag" :
                            cols.contains("Name") ? "Name" : null;
            String valCol = cols.contains("Value") ? "Value" : null;
            if (keyCol != null && valCol != null) {
                String sql = "SELECT " + keyCol + ", " + valCol + " FROM GlobalMetadata";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    int added = 0;
                    while (rs.next() && added < 200) { // cap to keep the map reasonable
                        String k = rs.getString(1);
                        String v = rs.getString(2);
                        if (k != null && v != null && !out.containsKey("meta." + k)) {
                            out.put("meta." + k, v);
                            added++;
                        }
                    }
                }
            }
        }

        if (tableExists("Properties")) {
            Set<String> cols = columnsOf("Properties");
            if (cols.contains("Name") && cols.contains("Value")) {
                String sql = "SELECT Name, Value FROM Properties";
                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    int added = 0;
                    while (rs.next() && added < 200) {
                        String k = rs.getString(1);
                        String v = rs.getString(2);
                        if (k != null && v != null && !out.containsKey("prop." + k)) {
                            out.put("prop." + k, v);
                            added++;
                        }
                    }
                }
            }
        }

        return out;
    }

    /** Return the set of column names for a table, empty if table missing. */
    private Set<String> columnsOf(String table) throws SQLException {
        LinkedHashSet<String> cols = new LinkedHashSet<>();
        if (!tableExists(table)) return cols;
        // PRAGMA table_info() cannot take a bound parameter, compose carefully.
        String sql = "PRAGMA table_info(" + table + ")";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null) cols.add(name);
            }
        }
        return cols;
    }

    @Override
    public ArrayList<FragmentScan> getStripes(double targetMz, float minRT, float maxRT, boolean sqrt)
            throws IOException, SQLException {
        ensureOpen();
        if (ms2Key == 9) {
            // DIA: select the single window per frame that contains targetMz
            final String sql =
                "SELECT F.Id, F.Time, W.ScanNumBegin, W.ScanNumEnd, " +
                "       W.IsolationMz, W.IsolationWidth, F.AccumulationTime " +
                "FROM Frames F " +
                "JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id " +
                "JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup " +
                "WHERE F.MsMsType = " + ms2Key + " AND F.Time BETWEEN ? AND ? " +
                "AND (? BETWEEN (W.IsolationMz - 0.5*W.IsolationWidth) AND (W.IsolationMz + 0.5*W.IsolationWidth)) " +
                "ORDER BY F.Time ASC, ABS(W.IsolationMz - ?) ASC";

            // Deduplicate by frame: keep the closest window to targetMz when multiple match
            class Meta { int frameId; float rt; double isoLo; double isoHi; float acc; }
            java.util.LinkedHashMap<Integer, Meta> byFrame = new java.util.LinkedHashMap<>();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, minRT);
                ps.setDouble(2, maxRT);
                ps.setDouble(3, targetMz);
                ps.setDouble(4, targetMz);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int fid = rs.getInt(1);
                        if (byFrame.containsKey(fid)) continue; // keep the first (closest) per ordering
                        double center = rs.getDouble(5);
                        double width  = rs.getDouble(6);
                        Meta m = new Meta();
                        m.frameId = fid;
                        m.rt = (float) rs.getDouble(2);
                        m.isoLo = center - 0.5 * width;
                        m.isoHi = center + 0.5 * width;
                        m.acc = (float) rs.getDouble(7);
                        byFrame.put(fid, m);
                    }
                }
            }

            if (byFrame.isEmpty()) return new ArrayList<>();

            // Build frame order by RT and compute a single mz filter that covers all chosen windows
            java.util.ArrayList<Meta> metas = new java.util.ArrayList<>(byFrame.values());
            metas.sort(java.util.Comparator.comparingDouble(m -> m.rt));
            double globalLo = Double.POSITIVE_INFINITY, globalHi = Double.NEGATIVE_INFINITY;
            for (Meta m : metas) { if (m.isoLo < globalLo) globalLo = m.isoLo; if (m.isoHi > globalHi) globalHi = m.isoHi; }

            int[] indices = new int[metas.size()];
            for (int i = 0; i < metas.size(); i++) indices[i] = metas.get(i).frameId - 1;

            ArrayList<FragmentScan> out = new ArrayList<>();
            try (RustIterator it = reader.createIterator(indices, globalLo, globalHi, -1, -1)) {
                int k = 0;
                for (SpectrumRecord s; (s = it.next()) != null; k++) {
                    Meta m = metas.get(k);
                    String name = Integer.toString(m.frameId);
                    // Filter spectrum to the per-frame isolation bounds
                    double lo = m.isoLo, hi = m.isoHi;
                    java.util.ArrayList<Double> mzL = new java.util.ArrayList<>(s.mz.length);
                    java.util.ArrayList<Float>  imL = new java.util.ArrayList<>(s.ims.length);
                    java.util.ArrayList<Float>  inL = new java.util.ArrayList<>(s.intensity.length);
                    for (int i = 0; i < s.mz.length; i++) {
                        double mz = s.mz[i];
                        if (mz >= lo && mz <= hi) {
                            mzL.add(mz);
                            imL.add(s.ims[i]);
                            float iz = s.intensity[i];
                            inL.add(sqrt ? (float)Math.sqrt(Math.max(iz, 0f)) : iz);
                        }
                    }
                    double[] mzArr = new double[mzL.size()];
                    float[] imArr = new float[imL.size()];
                    float[] inArr = new float[inL.size()];
                    for (int i = 0; i < mzL.size(); i++) { mzArr[i] = mzL.get(i); imArr[i] = imL.get(i); inArr[i] = inL.get(i); }

                    out.add(new FragmentScan(
                            name,              // spectrumName
                            name,              // precursorName (DIA → 0, use frame id string)
                            m.frameId,         // spectrumIndex
                            m.rt,              // scanStartTime
                            0,                 // fraction
                            1000f * m.acc,     // IonInjectionTime per spec
                            lo, hi,            // isolation window for this frame
                            mzArr, inArr, imArr,
                            (byte)0            // charge for DIA is 0
                    ));
                }
            }
            return out;
        } else if (ms2Key == 8) {
            // DDA: select frames whose isolation contains targetMz, pick closest per frame, include charge and parent
        	final String sql =
        	        "SELECT F.Id, F.Time, " +
        	        "       I.ScanNumBegin, I.ScanNumEnd, I.IsolationMz, I.IsolationWidth, " +
        	        "       F.AccumulationTime, " +
        	        "       COALESCE(P.Charge, 0) AS Charge, COALESCE(P.Parent, F.Id) AS Parent " +
        	        "FROM Frames F " +
        	        "JOIN PasefFrameMsMsInfo I ON I.Frame = F.Id " +
        	        "LEFT JOIN Precursors P ON P.Id = I.Precursor " +
        	        "WHERE F.MsMsType = 8 AND F.Time BETWEEN ? AND ? " +
        	        "ORDER BY F.Time ASC, I.ScanNumBegin ASC";

        	    final ArrayList<FragmentScan> out = new ArrayList<>();

        	    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        	        ps.setDouble(1, minRT);
        	        ps.setDouble(2, maxRT);
        	        try (ResultSet rs = ps.executeQuery()) {
        	            while (rs.next()) {
        	                final int frameId = rs.getInt(1);
        	                final float rt     = (float) rs.getDouble(2);
        	                final int scanLo   = rs.getInt(3);
        	                final int scanHi   = rs.getInt(4);
        	                final double isoMz = rs.getDouble(5);
        	                final double isoW  = rs.getDouble(6);
        	                final float acc    = (float) rs.getDouble(7);
        	                final byte charge  = (byte) Math.max(0, rs.getInt(8));
        	                final String parent= Integer.toString(rs.getInt(9));

        	                final double isoLo = isoMz - 0.5 * isoW;
        	                final double isoHi = isoMz + 0.5 * isoW;

        	                // One frame at a time, restricted to this window’s scans
        	                final int[] indices = new int[]{ frameId - 1 };
        	                final double mzLo = 150.0, mzHi = 1700.0; // broad fragment window
        	                try (RustIterator it = reader.createIterator(indices, mzLo, mzHi, scanLo, scanHi)) {
        	                    final SpectrumRecord s = it.next();
        	                    if (s == null) continue;

        	                    // Optionally sqrt intensities
        	                    float[] intens = s.intensity;
        	                    if (sqrt) {
        	                        intens = intens.clone();
        	                        for (int i = 0; i < intens.length; i++) {
        	                            final float v = intens[i];
        	                            intens[i] = (float) Math.sqrt(v < 0f ? 0f : v);
        	                        }
        	                    }

        	                    final String name = Integer.toString(frameId); // spectrumName
        	                    out.add(new FragmentScan(
        	                            name,                   // spectrumName
        	                            parent,                 // precursorName from Precursors.Parent
        	                            frameId,                // spectrumIndex
        	                            rt,                     // scanStartTime
        	                            0,                      // fraction
        	                            1000f * acc,            // IonInjectionTime (sec) = 1000 * AccumulationTime
        	                            isoLo, isoHi,           // isolation window bounds
        	                            s.mz, intens, s.ims,
        	                            charge                  // precursor charge
        	                    ));
        	                }
        	            }
        	        }
        	    }
            return out;
        } else {
            // Unknown MS2 key, return empty
            return new ArrayList<>();
        }
    }

    @Override
    public ArrayList<FragmentScan> getStripes(Range targetMzRange, float minRT, float maxRT, final boolean sqrt)
            throws IOException, SQLException {
        ensureOpen();
        final double rangeLo = targetMzRange.getStart();
        final double rangeHi = targetMzRange.getStop();

        if (ms2Key == 9) {
            // DIA: gather all windows overlapping the target range per frame,
            // iterate once across frames with mz filter = target range,
            // then split arrays per window intersection
            final String sql =
                "SELECT F.Id, F.Time, W.IsolationMz, W.IsolationWidth, F.AccumulationTime " +
                "FROM Frames F " +
                "JOIN DiaFrameMsMsInfo I ON I.Frame = F.Id " +
                "JOIN DiaFrameMsMsWindows W ON W.WindowGroup = I.WindowGroup " +
                "WHERE F.MsMsType = " + ms2Key + " AND F.Time BETWEEN ? AND ? " +
                "AND ( W.IsolationMz <= ? AND W.IsolationMz >= ? ) " +
                "ORDER BY F.Time ASC, W.IsolationMz ASC";

            class Win { double lo, hi; }
            class Meta { int frameId; float rt; float acc; java.util.ArrayList<Win> wins = new java.util.ArrayList<>(); }
            java.util.LinkedHashMap<Integer, Meta> map = new java.util.LinkedHashMap<>();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDouble(1, minRT);
                ps.setDouble(2, maxRT);
                ps.setDouble(3, rangeHi);
                ps.setDouble(4, rangeLo);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int fid = rs.getInt(1);
                        Meta m = map.get(fid);
                        if (m == null) {
                            m = new Meta();
                            m.frameId = fid;
                            m.rt = (float) rs.getDouble(2);
                            m.acc = (float) rs.getDouble(5);
                            map.put(fid, m);
                        }
                        double center = rs.getDouble(3);
                        double width  = rs.getDouble(4);
                        Win w = new Win();
                        w.lo = center - 0.5 * width;
                        w.hi = center + 0.5 * width;
                        m.wins.add(w);
                    }
                }
            }
            if (map.isEmpty()) return new ArrayList<>();

            // Build time-ordered list of frames
            java.util.ArrayList<Meta> metas = new java.util.ArrayList<>(map.values());
            metas.sort(java.util.Comparator.comparingDouble(m -> m.rt));
            int[] indices = new int[metas.size()];
            for (int i = 0; i < metas.size(); i++) indices[i] = metas.get(i).frameId - 1;

            ArrayList<FragmentScan> out = new ArrayList<>();
            try (RustIterator it = reader.createIterator(indices, rangeLo, rangeHi, -1, -1)) {
                int k = 0;
                for (SpectrumRecord s; (s = it.next()) != null; k++) {
                    Meta m = metas.get(k);
                    for (Win w : m.wins) {
                        // intersect window with target range
                        double lo = Math.max(rangeLo, w.lo);
                        double hi = Math.min(rangeHi, w.hi);
                        if (lo > hi) continue;

                        // filter arrays to [lo, hi]
                        java.util.ArrayList<Double> mzL = new java.util.ArrayList<>(s.mz.length);
                        java.util.ArrayList<Float>  imL = new java.util.ArrayList<>(s.ims.length);
                        java.util.ArrayList<Float>  inL = new java.util.ArrayList<>(s.intensity.length);
                        for (int i = 0; i < s.mz.length; i++) {
                            double mz = s.mz[i];
                            if (mz >= lo && mz <= hi) {
                                mzL.add(mz);
                                imL.add(s.ims[i]);
                                float iz = s.intensity[i];
                                inL.add(sqrt ? (float)Math.sqrt(Math.max(iz, 0f)) : iz);
                            }
                        }
                        double[] mzArr = new double[mzL.size()];
                        float[] imArr = new float[imL.size()];
                        float[] inArr = new float[inL.size()];
                        for (int i = 0; i < mzL.size(); i++) { mzArr[i] = mzL.get(i); imArr[i] = imL.get(i); inArr[i] = inL.get(i); }

                        String name = Integer.toString(m.frameId);
                        out.add(new FragmentScan(
                                name, name,
                                m.frameId, m.rt, 0, 1000f * m.acc,
                                w.lo, w.hi,    // store the full DIA isolation window bounds
                                mzArr, inArr, imArr,
                                (byte)0
                        ));
                    }
                }
            }
            return out;
        } else if (ms2Key == 8) {
            // DDA: pick targets whose isolation window overlaps the target range.
            // Produce one FragmentScan per frame (closest isolation to the center of target range).
        	final String sql =
        	        "SELECT F.Id, F.Time, " +
        	        "       I.ScanNumBegin, I.ScanNumEnd, I.IsolationMz, I.IsolationWidth, " +
        	        "       F.AccumulationTime, " +
        	        "       COALESCE(P.Charge, 0) AS Charge, COALESCE(P.Parent, F.Id) AS Parent " +
        	        "FROM Frames F " +
        	        "JOIN PasefFrameMsMsInfo I ON I.Frame = F.Id " +
        	        "LEFT JOIN Precursors P ON P.Id = I.Precursor " +
        	        "WHERE F.MsMsType = 8 AND F.Time BETWEEN ? AND ? " +
        	        "ORDER BY F.Time ASC, I.ScanNumBegin ASC";

        	    final ArrayList<FragmentScan> out = new ArrayList<>();

        	    try (PreparedStatement ps = conn.prepareStatement(sql)) {
        	        ps.setDouble(1, minRT);
        	        ps.setDouble(2, maxRT);
        	        try (ResultSet rs = ps.executeQuery()) {
        	            while (rs.next()) {
        	                final int frameId = rs.getInt(1);
        	                final float rt     = (float) rs.getDouble(2);
        	                final int scanLo   = rs.getInt(3);
        	                final int scanHi   = rs.getInt(4);
        	                final double isoMz = rs.getDouble(5);
        	                final double isoW  = rs.getDouble(6);
        	                final float acc    = (float) rs.getDouble(7);
        	                final byte charge  = (byte) Math.max(0, rs.getInt(8));
        	                final String parent= Integer.toString(rs.getInt(9));

        	                final double isoLo = isoMz - 0.5 * isoW;
        	                final double isoHi = isoMz + 0.5 * isoW;

        	                // One frame at a time, restricted to this window’s scans
        	                final int[] indices = new int[]{ frameId - 1 };
        	                final double mzLo = 150.0, mzHi = 1700.0; // broad fragment window
        	                try (RustIterator it = reader.createIterator(indices, mzLo, mzHi, scanLo, scanHi)) {
        	                    final SpectrumRecord s = it.next();
        	                    if (s == null) continue;

        	                    // Optionally sqrt intensities
        	                    float[] intens = s.intensity;
        	                    if (sqrt) {
        	                        intens = intens.clone();
        	                        for (int i = 0; i < intens.length; i++) {
        	                            final float v = intens[i];
        	                            intens[i] = (float) Math.sqrt(v < 0f ? 0f : v);
        	                        }
        	                    }

        	                    final String name = Integer.toString(frameId); // spectrumName
        	                    out.add(new FragmentScan(
        	                            name,                   // spectrumName
        	                            parent,                 // precursorName from Precursors.Parent
        	                            frameId,                // spectrumIndex
        	                            rt,                     // scanStartTime
        	                            0,                      // fraction
        	                            1000f * acc,            // IonInjectionTime (sec) = 1000 * AccumulationTime
        	                            isoLo, isoHi,           // isolation window bounds
        	                            s.mz, intens, s.ims,
        	                            charge                  // precursor charge
        	                    ));
        	                }
        	            }
        	        }
        	    }
            return out;
        } else {
            return new ArrayList<>();
        }
    }

}
