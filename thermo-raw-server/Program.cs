using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Text.RegularExpressions;
using System.Runtime.InteropServices;
using Grpc.Core;
using MSRaw.Thermo.Proto;
using ThermoFisher.CommonCore.Data.Business;
using ThermoFisher.CommonCore.Data.FilterEnums;   // MSOrderType
using ThermoFisher.CommonCore.Data.Interfaces;
using ThermoFisher.CommonCore.RawFileReader;
using Microsoft.AspNetCore.Server.Kestrel.Core;   // HttpProtocols

/// Program.cs is the entry point for the self-contained ASP.NET Core gRPC bridge that exposes
/// Thermo RAW access to the Java client. It boots the server, wires up service endpoints
/// (e.g., session open/close, run metadata, TIC/gradient, DIA window enumeration, and
/// MS1/MS2 streaming), configures basic logging and shutdown, and runs in a standalone
/// process so vendor SDK calls remain isolated from the JVM. The executable is intended to
/// be published as a single-file, RID-specific bundle and launched on demand by the Java
/// GrpcServerLauncher/ThermoServerPool.

var startupClock = Stopwatch.StartNew();
var proc = Process.GetCurrentProcess();
var sinceProcessStart = DateTime.Now - proc.StartTime;
Console.WriteLine($"Thermo server: startup begin (pid {proc.Id}, since process start {sinceProcessStart.TotalSeconds:F2} s)");
Console.WriteLine($"Thermo server: runtime {RuntimeInformation.FrameworkDescription}, arch {RuntimeInformation.ProcessArchitecture}, OS {RuntimeInformation.OSDescription}");
var builder = WebApplication.CreateBuilder(args);

// Resolve listening URL (plaintext HTTP/2)
string url = Environment.GetEnvironmentVariable("MSRAW_THERMO_URL")
          ?? args.FirstOrDefault(a => a.StartsWith("--url=", StringComparison.OrdinalIgnoreCase))?.Substring(6)
          ?? "http://127.0.0.1:50062";

var uri = new Uri(url);
IPAddress ip = uri.Host is "localhost" ? IPAddress.Loopback : IPAddress.Parse(uri.Host);
int port = uri.Port;

// Configure Kestrel to use HTTP/2 (h2c: prior-knowledge, no TLS) on the chosen endpoint
builder.WebHost.ConfigureKestrel(options =>
{
    options.Listen(ip, port, lo => { lo.Protocols = HttpProtocols.Http2; });
});
Console.WriteLine($"Thermo server: Kestrel configured in {startupClock.Elapsed.TotalSeconds:F2} s");

builder.Logging.ClearProviders(); // drop defaults
builder.Logging.AddSimpleConsole(o => {
    o.SingleLine = true;
    o.TimestampFormat = "";   // no timestamps
    o.IncludeScopes = false;
});
builder.Logging.SetMinimumLevel(LogLevel.Warning); // default: show warnings+only
builder.Logging.AddFilter("Microsoft", LogLevel.Error);
builder.Logging.AddFilter("Grpc", LogLevel.Error);
builder.Services.Configure<ConsoleLifetimeOptions>(o => o.SuppressStatusMessages = true);

builder.Services.AddGrpc();
Console.WriteLine($"Thermo server: gRPC services registered in {startupClock.Elapsed.TotalSeconds:F2} s");

var app = builder.Build();
Console.WriteLine($"Thermo server: app built in {startupClock.Elapsed.TotalSeconds:F2} s");
app.MapGrpcService<ThermoRawServiceImpl>();
app.MapGet("/", () => "MSRaw Thermo gRPC ready (HTTP/2 plaintext)");
Console.WriteLine("ThermoRawService methods:");
foreach (var m in ThermoRawService.Descriptor.Methods) Console.WriteLine("  " + m.Name);
Console.WriteLine($"LISTENING h2c on {ip}:{port}");
Console.WriteLine($"Thermo server: ready to accept connections in {startupClock.Elapsed.TotalSeconds:F2} s");
app.Run();

public sealed class ThermoRawServiceImpl : ThermoRawService.ThermoRawServiceBase
{
    private static readonly ConcurrentDictionary<string, IRawDataPlus> Sessions = new();

    public override Task<OpenReply> Open(OpenRequest request, ServerCallContext context)
    {
		try
    	{
	        var raw = RawFileReaderFactory.ReadFile(request.Path);
	        if (!raw.IsOpen)
	            throw new RpcException(new Status(StatusCode.Internal, $"Failed to open RAW: {request.Path}"));
	
	        raw.SelectInstrument(Device.MS, 1);
	
	        string model = raw.GetInstrumentData().Model ?? string.Empty;
	        int first = raw.RunHeaderEx.FirstSpectrum;
	        int last  = raw.RunHeaderEx.LastSpectrum;
	        double rtFirst = raw.RetentionTimeFromScanNumber(first);
	        double rtLast  = raw.RetentionTimeFromScanNumber(last);
	
	        string sid = Guid.NewGuid().ToString("N");
	        Sessions[sid] = raw;
	
	        return Task.FromResult(new OpenReply {
	            SessionId = sid, InstrumentModel = model, StartTime = rtFirst, EndTime = rtLast
	        });
	    }
	    
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "Open failed: " + ex));
	    }
    }

    public override Task<CloseReply> Close(CloseRequest request, ServerCallContext context)
    {
		try 
		{
	        if (Sessions.TryRemove(request.SessionId, out var raw))
	            raw.Dispose();
	
	        return Task.FromResult(new CloseReply { Ok = true });
	    }  
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "Close failed: " + ex));
	    }
    }
    
     public override Task<TicReply> GetMs1Tic(TicRequest req, ServerCallContext context)
	{
	    try
	    {
	        var raw = Get(req.SessionId); // your existing helper
	
	        // Default to full run if caller passed 0/0 (keeps behavior friendly while remaining minimal)
	        double rtMin = req.RtMin;
	        double rtMax = req.RtMax;
	        if (rtMin == 0 && rtMax == 0)
	        {
	            rtMin = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.StartTime : raw.RunHeader.StartTime; // minutes
	            rtMax = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.EndTime   : raw.RunHeader.EndTime;   // minutes
	        }
	
	        var rts  = new List<double>(1024);
	        var tics = new List<double>(1024);
	
	        foreach (int scan in ScansInRt(raw, rtMin, rtMax)) // ScansInRt compares in MINUTES
	        {
	            // MS1 only
		    var filter = raw.GetFilterForScanNumber(scan);
	            if (filter == null || filter.MSOrder != MSOrderType.Ms) continue;
	
	            // Lightweight per-scan stats
		    var stats = raw.GetScanStatsForScanNumber(scan);
	            if (stats == null) continue;
	
	            // RT -> seconds for the reply
	            double rtSec = raw.RetentionTimeFromScanNumber(scan) * 60.0;
	
	            rts.Add(rtSec);
	            tics.Add(stats.TIC);
	        }
	
	        var reply = new TicReply();
	        reply.RtSeconds.AddRange(rts);
	        reply.Tic.AddRange(tics);
	        return Task.FromResult(reply);
	    }
	    catch (RpcException) { throw; } // preserve explicit status code if thrown elsewhere
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetMs1Tic failed: " + ex));
	    }
	}


    
    public override Task<RunSummary> GetRunSummary(Session request, ServerCallContext context)
    {
	    try
	    {
	        if (!Sessions.TryGetValue(request.SessionId, out var raw) || raw == null)
	            throw new RpcException(new Status(StatusCode.NotFound, "invalid session"));
	
	        // Depending on RawFileReader version these are exposed via RunHeader or RunHeaderEx
			double startMin = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.StartTime : raw.RunHeader.StartTime;
			double endMin   = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.EndTime  : raw.RunHeader.EndTime;
			int startScan = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.FirstSpectrum : raw.RunHeader.FirstSpectrum;
			int endScan   = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.LastSpectrum  : raw.RunHeader.LastSpectrum;

			var gradientSeconds = Math.Max(0.0, (endMin - startMin) * 60.0);
	
	        // Some builds expose ChromatogramTraceSettings/TraceType directly in CommonCore.
	        // If your package layout differs, the names are the same.
	        var ticTrace = new ChromatogramTraceSettings(TraceType.TIC);
	        ticTrace.Filter = "ms";
	
	        // Time range in minutes, inclusive; using header bounds
	        var chromData = raw.GetChromatogramData(new[] { ticTrace }, startScan, endScan);
	
	        // Convert to signals, then sum intensities
	        var signals = ChromatogramSignal.FromChromatogramData(chromData);
	        double ticSum = 0.0;
			if (signals != null && signals.Length > 0)
			{
			    var ints = signals[0].Intensities; // IList<double>
			    if (ints != null && ints.Count > 0)
			    {
			        foreach (var v in ints) ticSum += v;
			    }
			}
	
	        var reply = new RunSummary
	        {
	            GradientLengthSeconds = gradientSeconds,
	            TotalIonCurrent = ticSum
	        };
	        return Task.FromResult(reply);
	    }
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetRunSummary failed: " + ex));
	    }
    }
    
    public override Task<RangesReply> GetRanges(Session request, ServerCallContext context)
    {
	    try
	    {
	        if (!Sessions.TryGetValue(request.SessionId, out var raw) || raw == null)
	            throw new RpcException(new Status(StatusCode.NotFound, "invalid session"));
	
			int first = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.FirstSpectrum : raw.RunHeader.FirstSpectrum;
			int last  = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.LastSpectrum  : raw.RunHeader.LastSpectrum;
	
	        // key: tuple(lo, hi) after rounding to stabilize buckets
	        var buckets = new Dictionary<(double lo, double hi), List<double>>();
	
	        for (int scan = first; scan <= last; scan++)
	        {
	            // Filter to MS/MS only
	            IScanFilter flt;
	            try { flt = raw.GetFilterForScanNumber(scan); }
	            catch { continue; }
	
	            if (flt == null) continue;
	            var order = flt.MSOrder;
	            if (order <= MSOrderType.Ms) continue; // keep MS2, MS3,...
	
	            // Event with precursor isolation info
	            IScanEvent evt;
	            try { evt = raw.GetScanEventForScanNumber(scan); } catch { continue; }
	            if (evt == null) continue;
	
	            // Some DIA modes multiplex multiple windows in one scan
	            int rxnCount = GetReactionCount(evt);
	
	            var rtSec = raw.RetentionTimeFromScanNumber(scan) * 60.0;
	
	            if (rxnCount > 0)
	            {
	                for (int i = 0; i < rxnCount; i++)
	                {
	                    double center = double.NaN, width = double.NaN;
	
	                    try { center = evt.GetReaction(i)?.PrecursorMass ?? double.NaN; } catch { }
	                    // Try to get width via API first
	                    try
	                    {
	                        width = evt.GetIsolationWidth(i);
	                        if (!(width > 0 && double.IsFinite(width))) width = double.NaN;
	                    } catch { }
	
	                    // Fallback: some builds expose width on the reaction object
	                    if (!(width > 0 && double.IsFinite(width)))
	                    {
	                        try { width = evt.GetReaction(i)?.IsolationWidth ?? double.NaN; } catch { }
	                    }
	
	                    if (!(center > 0 && double.IsFinite(center) && width > 0 && double.IsFinite(width)))
	                        continue;
	
	                    var lo = center - width / 2.0;
	                    var hi = center + width / 2.0;
	
	                    var key = (lo, hi);
	                    if (!buckets.TryGetValue(key, out var times))
	                    {
	                        times = new List<double>(64);
	                        buckets[key] = times;
	                    }
	                    times.Add(rtSec);
	                }
	            }
	            else
	            {
	                // If no reactions are reported, some instruments bake isolation in the filter text,
	                // but this varies. You can parse flt.ToString() here if needed.
	                // For now we skip to avoid false positives.
	                continue;
	            }
	        }
	
	        var reply = new RangesReply();
	
	        foreach (var kv in buckets)
	        {
	            var times = kv.Value;
	            times.Sort();
	
	            double avgDuty = 0.0;
	            if (times.Count >= 2)
	            {
	                double sum = 0.0;
	                for (int i = 1; i < times.Count; i++) sum += (times[i] - times[i - 1]);
	                avgDuty = sum / (times.Count - 1);
	            }
	
	            reply.Windows.Add(new WindowRange
	            {
	                Lo = kv.Key.lo,
	                Hi = kv.Key.hi,
	                AverageDutyCycleSeconds = avgDuty,
	                NumberOfMsms = times.Count
	            });
	        }
	
	        return Task.FromResult(reply);
        	    }
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetRanges failed: " + ex));
	    }
    }

    public override Task<SummariesReply> GetScanSummaries(Session request, ServerCallContext context)
    {
	    try
	    {
	        if (!Sessions.TryGetValue(request.SessionId, out var raw) || raw == null)
	            throw new RpcException(new Status(StatusCode.NotFound, "invalid session"));

	        int first = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.FirstSpectrum : raw.RunHeader.FirstSpectrum;
	        int last  = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.LastSpectrum  : raw.RunHeader.LastSpectrum;

	        var reply = new SummariesReply();

	        for (int scan = first; scan <= last; scan++)
	        {
	            IScanFilter filter;
	            try { filter = raw.GetFilterForScanNumber(scan); }
	            catch { continue; }
	            if (filter == null) continue;

	            int msLevel = filter.MSOrder == MSOrderType.Ms ? 1 : 2;

	            double rtSec = raw.RetentionTimeFromScanNumber(scan) * 60.0;

	            double isoLo = double.PositiveInfinity;
	            double isoHi = double.NegativeInfinity;

	            IScanEvent evt = null;
	            try { evt = raw.GetScanEventForScanNumber(scan); } catch { }
	            if (evt != null)
	            {
	                if (msLevel == 1)
	                {
	                    int n = 0;
	                    try { n = evt.MassRangeCount; } catch { n = 0; }
	                    for (int i = 0; i < n; i++)
	                    {
	                        var r = evt.GetMassRange(i);
	                        double l = r.Low, h = r.High;
	                        if (h > l && l > 0)
	                        {
	                            if (l < isoLo) isoLo = l;
	                            if (h > isoHi) isoHi = h;
	                        }
	                    }
	                    if (!(isoHi > isoLo))
	                    {
	                        isoLo = 0.0;
	                        isoHi = double.PositiveInfinity;
	                    }
	                }
	                else
	                {
	                    double center = evt.GetMass(0);
	                    double width = evt.GetIsolationWidth(0);
	                    isoLo = center - 0.5 * width;
	                    isoHi = center + 0.5 * width;
	                }
	            }
	            if (!(isoHi > isoLo) || !double.IsFinite(isoLo) || !double.IsFinite(isoHi))
	            {
	                isoLo = 0.0;
	                isoHi = double.PositiveInfinity;
	            }

	            double injS;
	            int charge;
	            int precursorScan;
	            ExtractTrailerInfo(raw, scan, out injS, out charge, out precursorScan);

	            double swLo, swHi;
	            GetScanWindow(evt, out swLo, out swHi);

	            var summary = new SpectrumSummary
	            {
	                ScanNumber = scan,
	                RtSeconds = rtSec,
	                MsLevel = msLevel,
	                IsoLower = isoLo,
	                IsoUpper = isoHi,
	                Charge = charge,
	                SpectrumName = scan.ToString(CultureInfo.InvariantCulture),
	                PrecursorName = precursorScan.ToString(CultureInfo.InvariantCulture),
	                IonInjectionTimeS = injS,
	                ScanWindowLower = swLo,
	                ScanWindowUpper = swHi
	            };
	            reply.Summaries.Add(summary);
	        }
	        return Task.FromResult(reply);
	    }
	    catch (RpcException) { throw; }
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetScanSummaries failed: " + ex));
	    }
    }
    
    private static int GetReactionCount(IScanEvent evt)
	{
	    if (evt == null) return 0;
	
	    try
	    {
	        var t = evt.GetType();
	
	        // Property: ReactionCount
	        var p = t.GetProperty("ReactionCount");
	        if (p != null)
	        {
	            var v = p.GetValue(evt);
	            if (v is int c) return c;
	        }
	
	        // Property: ReactionsCount (some older builds)
	        var p2 = t.GetProperty("ReactionsCount");
	        if (p2 != null)
	        {
	            var v = p2.GetValue(evt);
	            if (v is int c) return c;
	        }
	
	        // Property: Reactions (IList/ICollection) -> Count
	        var p3 = t.GetProperty("Reactions");
	        if (p3 != null)
	        {
	            var coll = p3.GetValue(evt) as System.Collections.ICollection;
	            if (coll != null) return coll.Count;
	        }
	
	        // Method: GetReactionCount()
	        var m1 = t.GetMethod("GetReactionCount", Type.EmptyTypes);
	        if (m1 != null)
	        {
	            var v = m1.Invoke(evt, null);
	            if (v is int c) return c;
	        }
	
	        // Method: GetNumberOfReactions()
	        var m2 = t.GetMethod("GetNumberOfReactions", Type.EmptyTypes);
	        if (m2 != null)
	        {
	            var v = m2.Invoke(evt, null);
	            if (v is int c) return c;
	        }
	    }
	    catch
	    {
	        // ignore and fall through to probing
	    }
	
	    // Last-resort probe: iterate until GetReaction throws/returns null
	    int i = 0;
	    for (;; i++)
	    {
	        try
	        {
	            var r = evt.GetReaction(i);
	            if (r == null) break;
	        }
	        catch
	        {
	            break;
	        }
	    }
	    return i;
	}

       public override async Task GetPrecursors(PrecursorsRequest req, IServerStreamWriter<Spectrum> stream, ServerCallContext ctx)
    {
		try 
		{
	        var raw = Get(req.SessionId);
	
	        foreach (int scan in ScansInRt(raw, req.RtMin, req.RtMax))
	        {
	            var filter = raw.GetFilterForScanNumber(scan);
	            if (filter.MSOrder != MSOrderType.Ms) continue; // MS1
	            
	    		double lo = double.PositiveInfinity;
	    		double hi = double.NegativeInfinity;
	    		int found = 0;
	            
	            var evt = raw.GetScanEventForScanNumber(scan);
	            try
		        {
		            int n = evt.MassRangeCount;
		            for (int i = 0; i < n; i++)
		            {
		                var r = evt.GetMassRange(i);
		                double l = r.Low, h = r.High;
		                if (h > l && l > 0) { if (l < lo) lo = l; if (h > hi) hi = h; found++; }
		            }
		            
		            if (found==0) 
		            {
						lo=0;
						hi=Double.PositiveInfinity;
					}
		        }
	            catch 
	            {
					lo=0;
					hi=Double.PositiveInfinity;
				}
	            var spec = BuildSpectrum(raw, scan, isMs1: true, isoLo: lo, isoHi: hi);
	            await stream.WriteAsync(spec);
	        }
        }
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetPrecursors failed: " + ex));
	    }
    }

    public override async Task GetStripes(StripesRequest req, IServerStreamWriter<Spectrum> stream, ServerCallContext ctx)
    {
		try
		{
	        var raw = Get(req.SessionId);
	
	        foreach (int scan in ScansInRt(raw, req.RtMin, req.RtMax))
	        {
	            var filter = raw.GetFilterForScanNumber(scan);
	            if (filter.MSOrder != MSOrderType.Ms2) continue;
	
	            var evt = raw.GetScanEventForScanNumber(scan);
	            double center = evt.GetMass(0);
	            double width  = evt.GetIsolationWidth(0);
	            double lo = center - 0.5 * width;
	            double hi = center + 0.5 * width;
	
	            if (!(lo < req.MzHi && hi > req.MzLo)) continue;
	
	            var spec = BuildSpectrum(raw, scan, isMs1: false, isoLo: lo, isoHi: hi);
	            await stream.WriteAsync(spec);
	        }
        }
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetStripes failed: " + ex));
	    }
    }

    // ---------- helpers ----------

    private static IRawDataPlus Get(string sid) =>
        Sessions.TryGetValue(sid, out var raw)
            ? raw
            : throw new RpcException(new Status(StatusCode.NotFound, "Unknown session"));

    private static IEnumerable<int> ScansInRt(IRawDataPlus raw, double rtMin, double rtMax)
    {
        int first = raw.RunHeaderEx.FirstSpectrum, last = raw.RunHeaderEx.LastSpectrum;
        for (int scan = first; scan <= last; scan++)
        {
            double rt = raw.RetentionTimeFromScanNumber(scan);
            if (rt < rtMin) continue;
            if (rt > rtMax) break;
            yield return scan;
        }
    }
    
    private static void ReadMzAndIntensity(IRawDataPlus raw, int scan, out double[] mz, out float[] intensity)
	{
	    // 1) Prefer centroided data. 'true' tells the reader to centroid profile/segmented scans.
	    var cs = raw.GetCentroidStream(scan, true);
	    if (cs != null && cs.Masses != null && cs.Intensities != null && cs.Intensities.Length > 0)
	    {
	        mz = cs.Masses;
	        var intensD = cs.Intensities;
	        intensity = new float[intensD.Length];
	        for (int i = 0; i < intensD.Length; i++) intensity[i] = (float)intensD[i];
	        return;
	    }
	
	    // 2) Fallback for low-res ion-trap or cases where centroid stream is empty:
	    //    use segmented data. This is vendor-decimated mass-intensity pairs.
	    var seg = raw.GetSegmentedScanFromScanNumber(scan);
	    if (seg != null && seg.Positions != null && seg.Intensities != null && seg.Intensities.Length > 0)
	    {
	        mz = seg.Positions;
	        var intensD = seg.Intensities;
	        intensity = new float[intensD.Length];
	        for (int i = 0; i < intensD.Length; i++) intensity[i] = (float)intensD[i];
	        return;
	    }
	
	    // 3) Nothing usable
	    mz = Array.Empty<double>();
	    intensity = Array.Empty<float>();
	}

    private static Spectrum BuildSpectrum(IRawDataPlus raw, int scan, bool isMs1, double isoLo, double isoHi)
    {
        var cs = raw.GetCentroidStream(scan, false);
        ReadMzAndIntensity(raw, scan, out var mz, out var intensF);
        
        double injS;
        int charge;
        int precursorScan;
        ExtractTrailerInfo(raw, scan, out injS, out charge, out precursorScan);

        double swLo, swHi;
        GetScanWindow(raw, scan, out swLo, out swHi);

        var s = new Spectrum
        {
            ScanNumber = scan,
            RtSeconds  = raw.RetentionTimeFromScanNumber(scan)*60f,
            MsLevel    = isMs1 ? 1 : 2,
            IsoLower   = isoLo,
            IsoUpper   = isoHi,
            Charge     = charge,
            SpectrumName    = scan.ToString(CultureInfo.InvariantCulture),
            PrecursorName   = precursorScan.ToString(CultureInfo.InvariantCulture),
            IonInjectionTimeS = injS,
	        ScanWindowLower = swLo,
	        ScanWindowUpper = swHi
        };

        s.Mz.AddRange(mz);
        s.Intensity.AddRange(intensF);
        return s;
    }

    private static void GetScanWindow(IRawDataPlus raw, int scan, out double swLo, out double swHi)
    {
        IScanEvent evt2 = null;
        try { evt2 = raw.GetScanEventForScanNumber(scan); } catch { }
        GetScanWindow(evt2, out swLo, out swHi);
    }

    private static void GetScanWindow(IScanEvent evt2, out double swLo, out double swHi)
    {
        swLo = double.PositiveInfinity;
        swHi = double.NegativeInfinity;
        try
        {
            if (evt2 != null)
            {
                int n = 0;
                try { n = evt2.MassRangeCount; } catch { n = 0; }
                for (int i = 0; i < n; i++)
                {
                    var r = evt2.GetMassRange(i);
                    double l = r.Low, h = r.High;
                    if (h > l && l > 0)
                    {
                        if (l < swLo) swLo = l;
                        if (h > swHi) swHi = h;
                    }
                }
            }
        }
        catch { }
        if (!(swHi > swLo) || !double.IsFinite(swLo) || !double.IsFinite(swHi))
        {
            swLo = 0.0;
            swHi = double.PositiveInfinity;
        }
    }

    private static void ExtractTrailerInfo(IRawDataPlus raw, int scan, out double injS, out int charge, out int precursorScan)
    {
        injS = 0;
        charge = 0;
        precursorScan = 0;
        try
        {
            var trailers = raw.GetTrailerExtraInformation(scan); // ILogEntryAccess with Labels/Values
            int n = trailers.Length;
            for (int i = 0; i < n; i++)
            {
                string label = trailers.Labels?[i] ?? string.Empty;
                string value = trailers.Values?[i] ?? string.Empty;

                if (injS == 0 && label.IndexOf("ion injection time", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (TryParseNumber(value, out var injMs))
                        injS = injMs / 1000.0;
                }
                if (charge == 0 && label.IndexOf("charge", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (int.TryParse(ExtractInteger(value), NumberStyles.Integer, CultureInfo.InvariantCulture, out var ch))
                        charge = ch;
                }
                if (precursorScan == 0 && (label.IndexOf("Master Scan Number", StringComparison.OrdinalIgnoreCase) >= 0 ||
                         label.IndexOf("Master Index", StringComparison.OrdinalIgnoreCase) >= 0))
                {
                    if (int.TryParse(ExtractInteger(value), NumberStyles.Integer, CultureInfo.InvariantCulture, out var ps))
                        precursorScan = ps;
                }
            }
        }
        catch { }
    }

    private static bool TryParseNumber(string s, out double value)
    {
        value = 0;
        if (string.IsNullOrEmpty(s)) return false;
        var chars = s.Where(c => char.IsDigit(c) || c == '.' || c == '-' || c == '+').ToArray();
        if (chars.Length == 0) return false;
        return double.TryParse(new string(chars), NumberStyles.Float, CultureInfo.InvariantCulture, out value);
    }

    private static string ExtractInteger(string s)
    {
        if (string.IsNullOrEmpty(s)) return "0";
        
	    if (string.IsNullOrWhiteSpace(s)) return "0";
	    s = s.Trim();
	    if (!Regex.IsMatch(s, @"^[+-]?\d+$")) return "0";
    
        var chars = s.Where(char.IsDigit).ToArray();
        return chars.Length == 0 ? "0" : new string(chars);
    }
    
    public override Task<MetadataReply> GetMetadata(Session request, ServerCallContext context)
	{
	    try
	    {
	        if (!Sessions.TryGetValue(request.SessionId, out var raw) || raw == null)
	            throw new RpcException(new Status(StatusCode.NotFound, "invalid session"));
	
	        var kv = new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
	
	        void Add(string k, object? v)
			{
			    if (v is null) return;
			    var s = v.ToString();
			    if (!string.IsNullOrWhiteSpace(s)) kv[k] = s;
			}
	
	        // --- File / run header ---
	        try { Add("file.path", raw.FileName); } catch { }
	        try
	        {
	            var p = raw.FileName;
	            if (!string.IsNullOrEmpty(p))
	            {
	                Add("file.name", Path.GetFileName(p));
	                try { var fi = new FileInfo(p); Add("file.size_bytes", fi.Length); } catch { }
	            }
	        } catch { }
	
	        // Header minutes & scans
	        double startMin, endMin;
	        int firstScan, lastScan;
	        if (raw.RunHeaderEx != null)
	        {
	            startMin = raw.RunHeaderEx.StartTime;
	            endMin   = raw.RunHeaderEx.EndTime;
	            firstScan = raw.RunHeaderEx.FirstSpectrum;
	            lastScan  = raw.RunHeaderEx.LastSpectrum;
	        }
	        else
	        {
	            startMin = raw.RunHeader.StartTime;
	            endMin   = raw.RunHeader.EndTime;
	            firstScan = raw.RunHeader.FirstSpectrum;
	            lastScan  = raw.RunHeader.LastSpectrum;
	        }
	        Add("run.start_time_min", startMin);
	        Add("run.end_time_min",   endMin);
	        Add("run.start_scan",     firstScan);
	        Add("run.end_scan",       lastScan);
	        Add("run.total_scans",    (lastScan >= firstScan) ? (lastScan - firstScan + 1) : 0);
	
	        // If you already compute these elsewhere, reuse them; otherwise:
	        var gradientSeconds = Math.Max(0.0, (endMin - startMin) * 60.0);
	        Add("run.gradient_length_seconds", gradientSeconds);
	
	        // Optional TIC total (same logic as GetRunSummary; okay to repeat)
	        try
	        {
	            var ticTrace = new ChromatogramTraceSettings(TraceType.TIC) { Filter = "ms" };
	            var chrom = raw.GetChromatogramData(new[] { ticTrace }, firstScan, lastScan);
	            var signals = ChromatogramSignal.FromChromatogramData(chrom);
	            double ticSum = 0.0;
	            if (signals != null && signals.Length > 0)
	            {
	                var ints = signals[0].Intensities;
	                if (ints != null) foreach (var v in ints) ticSum += v;
	            }
	            Add("run.tic_total", ticSum);
	        }
	        catch { /* ignore */ }
	
	        // --- Instrument block ---
	        try
	        {
	            var inst = raw.GetInstrumentData();
	            if (inst !=null)
	            {
		            if (inst?.Model!=null) Add("instrument.model", inst?.Model);
		            if (inst?.Name!=null) Add("instrument.name", inst?.Name);
		            if (inst?.SerialNumber!=null) Add("instrument.serial_number", inst?.SerialNumber);
		            if (inst?.SoftwareVersion!=null) Add("instrument.software_version", inst?.SoftwareVersion);
	            }
	        }
	        catch { }
	
	        // --- Acquisition summary (fast scan filter pass) ---
	        var analyzers   = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
	        var polarities  = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
	        var frags       = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
	        int ms1Count = 0, ms2PlusCount = 0;
	
	        for (int scan = firstScan; scan <= lastScan; scan++)
	        {
	            IScanFilter f;
	            try { f = raw.GetFilterForScanNumber(scan); } catch { continue; }
	            if (f == null) continue;
	
	            // MS level
	            if (f.MSOrder == ThermoFisher.CommonCore.Data.FilterEnums.MSOrderType.Ms) ms1Count++;
	            else ms2PlusCount++;
	
	            // Analyzer & polarity from filter (cheap)
	            try { analyzers.Add(f.MassAnalyzer.ToString()); } catch { }
	            try { polarities.Add(f.Polarity.ToString()); } catch { }
	
	            // Fragmentation types via scan event reactions (best-effort)
	            try
	            {
	                var evt = raw.GetScanEventForScanNumber(scan);
	                if (evt != null)
	                {
	                    int n = 0;
	                    try { n = (int)(evt.GetType().GetProperty("ReactionCount")?.GetValue(evt) ?? 0); } catch { }
	                    if (n <= 0)
	                    {
	                        // fallback probe
	                        for (;; n++)
	                        {
	                            try { if (evt.GetReaction(n) == null) break; } catch { break; }
	                        }
	                    }
	                    for (int i = 0; i < n; i++)
	                    {
	                        try
	                        {
	                            var r = evt.GetReaction(i);
	                            var at = r?.ActivationType.ToString();
	                            if (!string.IsNullOrEmpty(at)) frags.Add(at);
	                        } catch { }
	                    }
	                }
	            } catch { }
	        }
	        
			try
	        {
		        if (analyzers.Count > 0)  Add("acq.mass_analyzers",  string.Join(",", analyzers));
		        if (polarities.Count > 0) Add("acq.polarities",      string.Join(",", polarities));
		        if (frags.Count > 0)      Add("acq.fragmentations",  string.Join(",", frags));
		        if (ms1Count > 0)         Add("acq.ms1_count",       ms1Count);
		        if (ms2PlusCount > 0)     Add("acq.ms2_count",       ms2PlusCount);
		    }
	        catch { }
	
	        var reply = new MetadataReply();
	        reply.Kv.Add(kv);
	        return Task.FromResult(reply);
	    }
	    catch (RpcException) { throw; }
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetMetadata failed: " + ex));
	    }
	}
}
