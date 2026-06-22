using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Text.RegularExpressions;
using System.Threading;
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
Console.WriteLine($"Thermo server: processing thread limit {ProcessingThrottle.Description}");

// Configure Kestrel to use HTTP/2 (h2c: prior-knowledge, no TLS) on the chosen endpoint
builder.WebHost.ConfigureKestrel(options =>
{
    options.Listen(ip, port, lo => { lo.Protocols = HttpProtocols.Http2; });
});
Console.WriteLine($"Thermo server: Kestrel configured in {startupClock.Elapsed.TotalSeconds:F2} s");

builder.Logging.ClearProviders(); // drop defaults
builder.Logging.SetMinimumLevel(LogLevel.Warning); // default: show warnings+only
builder.Logging.AddFilter("Microsoft", LogLevel.Error);
builder.Logging.AddFilter("Grpc", LogLevel.Error);
builder.Logging.AddProvider(new GrpcCallHandlerLoggerProvider());
builder.Logging.AddFilter("Grpc.AspNetCore.Server.ServerCallHandler", LogLevel.Information);
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

internal sealed class GrpcCallHandlerLoggerProvider : ILoggerProvider
{
    public ILogger CreateLogger(string categoryName) => new GrpcCallHandlerLogger(categoryName);
    public void Dispose() { }

    private sealed class GrpcCallHandlerLogger : ILogger
    {
        private readonly string _categoryName;

        public GrpcCallHandlerLogger(string categoryName)
        {
            _categoryName = categoryName;
        }

        public IDisposable BeginScope<TState>(TState state) where TState : notnull => NullScope.Instance;
        public bool IsEnabled(LogLevel logLevel) => true;

        public void Log<TState>(LogLevel logLevel, EventId eventId, TState state, Exception? exception, Func<TState, Exception?, string> formatter)
        {
            string message = formatter(state, exception);
            if (_categoryName == "Grpc.AspNetCore.Server.ServerCallHandler")
            {
                if (IsClientReset(exception, message))
                    return; // expected cancellation, suppress

                if (IsThermoInstrumentIndexError(message))
                {
                    // Expected for unsupported/metadata-only RAWs in large directory scans; Java layer reports this per-file.
                    return;
                }
            }

            Console.WriteLine($"{FormatLevel(logLevel)}: {_categoryName}[{eventId.Id}] {message}{FormatException(exception)}");
        }

        private static string FormatLevel(LogLevel logLevel) => logLevel switch
        {
            LogLevel.Trace => "trce",
            LogLevel.Debug => "dbug",
            LogLevel.Information => "info",
            LogLevel.Warning => "warn",
            LogLevel.Error => "fail",
            LogLevel.Critical => "crit",
            _ => "info",
        };

        private static string FormatException(Exception? exception)
        {
            return exception == null ? string.Empty : " " + exception;
        }

        private static bool IsClientReset(Exception? exception, string message)
        {
            if (exception is IOException ioEx &&
                ioEx.Message.Contains("client reset the request stream", StringComparison.OrdinalIgnoreCase))
                return true;

            return message.Contains("Error reading message", StringComparison.Ordinal) &&
                   message.Contains("client reset the request stream", StringComparison.OrdinalIgnoreCase);
        }

        private static bool IsThermoInstrumentIndexError(string message)
        {
            return message.Contains("Open failed:", StringComparison.Ordinal) &&
                   message.Contains("instrument index", StringComparison.OrdinalIgnoreCase);
        }

        private sealed class NullScope : IDisposable
        {
            public static readonly NullScope Instance = new();
            public void Dispose() { }
        }
    }
}

internal static class ProcessingThrottle
{
    private const string EnvVar = "MSRAW_THERMO_THREADS";
    private static readonly int? Limit = ParseLimit();
    private static readonly SemaphoreSlim? Semaphore = Limit.HasValue ? new SemaphoreSlim(Limit.Value, Limit.Value) : null;
    private static readonly IDisposable NoopLease = new Lease(null);

    public static string Description => Limit.HasValue ? Limit.Value.ToString(CultureInfo.InvariantCulture) : "max";

    public static IDisposable Enter(CancellationToken cancellationToken)
    {
        if (Semaphore == null) return NoopLease;
        Semaphore.Wait(cancellationToken);
        return new Lease(Semaphore);
    }

    public static async Task<IDisposable> EnterAsync(CancellationToken cancellationToken)
    {
        if (Semaphore == null) return NoopLease;
        await Semaphore.WaitAsync(cancellationToken);
        return new Lease(Semaphore);
    }

    private static int? ParseLimit()
    {
        string? raw = Environment.GetEnvironmentVariable(EnvVar);
        if (string.IsNullOrWhiteSpace(raw)) return null;
        if (!int.TryParse(raw, NumberStyles.Integer, CultureInfo.InvariantCulture, out int parsed) || parsed <= 0) return null;
        return parsed;
    }

    private sealed class Lease : IDisposable
    {
        private readonly SemaphoreSlim? semaphore;
        private bool disposed;

        public Lease(SemaphoreSlim? semaphore)
        {
            this.semaphore = semaphore;
        }

        public void Dispose()
        {
            if (disposed) return;
            disposed = true;
            semaphore?.Release();
        }
    }
}

public sealed class ThermoRawServiceImpl : ThermoRawService.ThermoRawServiceBase
{
    private static readonly ConcurrentDictionary<string, RawSession> Sessions = new();
    private static readonly int[] MsInstrumentIndices = { 1, 2, 3, 4 };

    public override Task<OpenReply> Open(OpenRequest request, ServerCallContext context)
    {
		using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
		IRawDataPlus? raw = null;
		bool sessionRegistered = false;
		try
    	{
	        raw = RawFileReaderFactory.ReadFile(request.Path);
	        if (!raw.IsOpen)
	            throw new RpcException(new Status(StatusCode.Internal, $"Failed to open RAW: {request.Path}"));

	        SelectMsInstrument(raw);
	
	        string model = raw.GetInstrumentData().Model ?? string.Empty;
	        int first = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.FirstSpectrum : raw.RunHeader.FirstSpectrum;
	        int last = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.LastSpectrum : raw.RunHeader.LastSpectrum;
	        double rtFirst = raw.RetentionTimeFromScanNumber(first);
	        double rtLast  = raw.RetentionTimeFromScanNumber(last);
	        string runStartTimeIso8601 = GetRunStartTimeIso8601(raw);
	
	        string sid = Guid.NewGuid().ToString("N");
	        Sessions[sid] = new RawSession(raw);
	        sessionRegistered = true;
	
	        return Task.FromResult(new OpenReply {
	            SessionId = sid, InstrumentModel = model, StartTime = rtFirst, EndTime = rtLast, RunStartTimeIso8601 = runStartTimeIso8601
	        });
	    }
	    
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "Open failed: " + ex));
	    }
		finally
		{
			if (!sessionRegistered && raw != null)
				raw.Dispose();
		}
    }

	private static void SelectMsInstrument(IRawDataPlus raw)
	{
		ArgumentOutOfRangeException? last = null;
		foreach (int index in MsInstrumentIndices)
		{
			try
			{
				raw.SelectInstrument(Device.MS, index);
				return;
			}
			catch (ArgumentOutOfRangeException ex)
			{
				last = ex;
			}
		}
		if (last != null) throw last;
	}

	private static string GetRunStartTimeIso8601(IRawDataPlus raw)
	{
		try
		{
			var startDateProp = raw.GetType().GetProperty("CreationDate")
				?? raw.GetType().GetProperty("DateCreated")
				?? raw.GetType().GetProperty("AcquisitionDate")
				?? raw.GetType().GetProperty("StartDate");
			var startDateValue = startDateProp?.GetValue(raw);
			if (startDateValue is DateTime dt) return dt.ToUniversalTime().ToString("O");
			if (startDateValue is DateTimeOffset dto) return dto.ToUniversalTime().ToString("O");
		}
		catch { }
		return string.Empty;
	}

    public override Task<CloseReply> Close(CloseRequest request, ServerCallContext context)
    {
		using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
		try 
		{
	        if (Sessions.TryRemove(request.SessionId, out var session))
	            session.Dispose();
	
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
		using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
	    try
	    {
	        var raw = Get(req.SessionId);
	
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
	
	        foreach (int scan in ScansInRt(raw, rtMin, rtMax))
	        {
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
	    using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
	    try
	    {
	        var raw = Get(request.SessionId);
	
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
	    using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
	    try
	    {
	        var session = GetSession(request.SessionId);
	
	        var buckets = new Dictionary<(double lo, double hi), List<double>>();
	
	        foreach (var scan in session.ScanIndex.AllScans)
	        {
	            if (scan.IsMs1) continue;
	            var rtSec = scan.RtMinutes * 60.0;
	
	            foreach (var isolation in scan.IsolationWindows)
	            {
	                var key = (isolation.Lower, isolation.Upper);
	                if (!buckets.TryGetValue(key, out var times))
	                {
	                    times = new List<double>(64);
	                    buckets[key] = times;
	                }
	                times.Add(rtSec);
	            }
	        }
	
	        var reply = new RangesReply();
	
	        foreach (var kv in buckets)
	        {
	            var times = kv.Value;
	            times.Sort();
	            double rtStart = times.Count > 0 ? times[0] : 0.0;
	            double rtEnd = times.Count > 0 ? times[times.Count - 1] : 0.0;
	
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
	                NumberOfMsms = times.Count,
	                RtStartSeconds = rtStart,
	                RtEndSeconds = rtEnd
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
	    using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
	    try
	    {
	        var session = GetSession(request.SessionId);
	        var raw = session.Raw;

	        var reply = new SummariesReply();

	        foreach (var scan in session.ScanIndex.AllScans)
	        {
	            int msLevel = scan.IsMs1 ? 1 : 2;
	            scan.TryGetIsolationSuperset(out var isolation);

	            double injS;
	            double rawOvFtT;
	            int charge;
	            int precursorScan;
	            ExtractTrailerInfo(raw, scan.ScanNumber, out injS, out charge, out precursorScan, out rawOvFtT);
	            double tic = 0.0;
	            try
	            {
	                var stats = raw.GetScanStatsForScanNumber(scan.ScanNumber);
	                if (stats != null) tic = stats.TIC;
	            }
	            catch { }

	            var summary = new SpectrumSummary
	            {
	                ScanNumber = scan.ScanNumber,
	                RtSeconds = scan.RtMinutes * 60.0,
	                MsLevel = msLevel,
	                IsoLower = isolation.Lower,
	                IsoUpper = isolation.Upper,
	                Charge = charge,
	                SpectrumName = scan.ScanNumber.ToString(CultureInfo.InvariantCulture),
	                PrecursorName = precursorScan.ToString(CultureInfo.InvariantCulture),
	                IonInjectionTimeS = injS,
	                ScanWindowLower = scan.ScanWindowLower,
	                ScanWindowUpper = scan.ScanWindowUpper,
	                Tic = tic,
	                RawOvFtt = rawOvFtT,
	                IsoTarget = isolation.Target
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
		using var throttle = await ProcessingThrottle.EnterAsync(ctx.CancellationToken);
		try 
		{
	        var session = GetSession(req.SessionId);
	        var raw = session.Raw;
	
	        foreach (var scan in session.ScanIndex.InRtRange(req.RtMin, req.RtMax))
	        {
	            if (!scan.IsMs1) continue;
	            scan.TryGetIsolationSuperset(out var isolation);
	            var spec = BuildSpectrum(raw, scan, isolation);
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
		using var throttle = await ProcessingThrottle.EnterAsync(ctx.CancellationToken);
		try
		{
	        var session = GetSession(req.SessionId);
	        var raw = session.Raw;
	
	        foreach (var scan in session.ScanIndex.InRtRange(req.RtMin, req.RtMax))
	        {
	            if (!scan.IsMs2) continue;
	
	            foreach (var isolation in scan.IsolationWindows)
	            {
	                double lo = isolation.Lower;
	                double hi = isolation.Upper;
	
	                if (!(lo < req.MzHi && hi > req.MzLo)) continue;
	
	                var spec = BuildSpectrum(raw, scan, isolation);
	                await stream.WriteAsync(spec);
	            }
	        }
        }
	    catch (RpcException) { throw; } // preserve explicit statuses
	    catch (Exception ex)
	    {
	        throw new RpcException(new Status(StatusCode.Internal, "GetStripes failed: " + ex));
	    }
    }

    // ---------- helpers ----------

    private static RawSession GetSession(string sid) =>
        Sessions.TryGetValue(sid, out var session)
            ? session
            : throw new RpcException(new Status(StatusCode.NotFound, "Unknown session"));

    private static IRawDataPlus Get(string sid) => GetSession(sid).Raw;

    private static IEnumerable<int> ScansInRt(IRawDataPlus raw, double rtMin, double rtMax)
    {
        int first = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.FirstSpectrum : raw.RunHeader.FirstSpectrum;
        int last = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.LastSpectrum : raw.RunHeader.LastSpectrum;
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

    private readonly struct IsolationWindowInfo
    {
        public readonly double Lower;
        public readonly double Target;
        public readonly double Upper;

        public IsolationWindowInfo(double lower, double target, double upper)
        {
            Lower = lower;
            Target = target;
            Upper = upper;
        }

        public bool IsUsable =>
            Target > 0 && Upper > Lower && double.IsFinite(Lower) && double.IsFinite(Target) && double.IsFinite(Upper);
    }

    private sealed class IndexedScan
    {
        public readonly int ScanNumber;
        public readonly double RtMinutes;
        public readonly bool IsMs1;
        public readonly bool IsMs2;
        public readonly IsolationWindowInfo[] IsolationWindows;
        public readonly double ScanWindowLower;
        public readonly double ScanWindowUpper;

        public IndexedScan(int scanNumber, double rtMinutes, bool isMs1, bool isMs2, IsolationWindowInfo[] isolationWindows, double scanWindowLower,
            double scanWindowUpper)
        {
            ScanNumber = scanNumber;
            RtMinutes = rtMinutes;
            IsMs1 = isMs1;
            IsMs2 = isMs2;
            IsolationWindows = isolationWindows;
            ScanWindowLower = scanWindowLower;
            ScanWindowUpper = scanWindowUpper;
        }

        public bool TryGetIsolationSuperset(out IsolationWindowInfo superset)
        {
            if (IsolationWindows.Length == 0)
            {
                superset = new IsolationWindowInfo(0.0, 0.0, double.PositiveInfinity);
                return false;
            }
            if (IsolationWindows.Length == 1)
            {
                superset = IsolationWindows[0];
                return true;
            }

            double lo = double.PositiveInfinity;
            double hi = double.NegativeInfinity;
            foreach (var window in IsolationWindows)
            {
                if (window.Lower < lo) lo = window.Lower;
                if (window.Upper > hi) hi = window.Upper;
            }
            superset = new IsolationWindowInfo(lo, (lo + hi) / 2.0, hi);
            return true;
        }
    }

    private sealed class ThermoScanIndex
    {
        private const double RtBoundaryToleranceMinutes = 1e-6;
        private readonly IndexedScan[] scans;

        public ThermoScanIndex(IndexedScan[] scans)
        {
            this.scans = scans;
        }

        public IndexedScan[] AllScans => scans;

        public IEnumerable<IndexedScan> InRtRange(double minRtMinutes, double maxRtMinutes)
        {
            int start = LowerBound(minRtMinutes - RtBoundaryToleranceMinutes);
            int stop = UpperBound(maxRtMinutes + RtBoundaryToleranceMinutes);
            for (int i = start; i < stop; i++)
                yield return scans[i];
        }

        private int LowerBound(double rtMinutes)
        {
            int lo = 0;
            int hi = scans.Length;
            while (lo < hi)
            {
                int mid = lo + (hi - lo) / 2;
                if (scans[mid].RtMinutes < rtMinutes)
                    lo = mid + 1;
                else
                    hi = mid;
            }
            return lo;
        }

        private int UpperBound(double rtMinutes)
        {
            int lo = 0;
            int hi = scans.Length;
            while (lo < hi)
            {
                int mid = lo + (hi - lo) / 2;
                if (scans[mid].RtMinutes <= rtMinutes)
                    lo = mid + 1;
                else
                    hi = mid;
            }
            return lo;
        }
    }

    private sealed class RawSession : IDisposable
    {
        private readonly Lazy<ThermoScanIndex> scanIndex;

        public RawSession(IRawDataPlus raw)
        {
            Raw = raw;
            scanIndex = new Lazy<ThermoScanIndex>(() => BuildScanIndex(raw), LazyThreadSafetyMode.ExecutionAndPublication);
        }

        public IRawDataPlus Raw { get; }
        public ThermoScanIndex ScanIndex => scanIndex.Value;

        public void Dispose()
        {
            Raw.Dispose();
        }
    }

    private static ThermoScanIndex BuildScanIndex(IRawDataPlus raw)
    {
        var clock = Stopwatch.StartNew();
        int first = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.FirstSpectrum : raw.RunHeader.FirstSpectrum;
        int last = (raw.RunHeaderEx != null) ? raw.RunHeaderEx.LastSpectrum : raw.RunHeader.LastSpectrum;
        var scans = new List<IndexedScan>(Math.Max(0, last - first + 1));

        for (int scan = first; scan <= last; scan++)
        {
            IScanFilter filter;
            try { filter = raw.GetFilterForScanNumber(scan); }
            catch { continue; }
            if (filter == null) continue;

            double rtMinutes;
            try { rtMinutes = raw.RetentionTimeFromScanNumber(scan); }
            catch { continue; }

            IScanEvent? evt = null;
            try { evt = raw.GetScanEventForScanNumber(scan); } catch { }
            GetScanWindow(evt, out var scanWindowLower, out var scanWindowUpper);

            bool isMs1 = filter.MSOrder == MSOrderType.Ms;
            bool isMs2 = filter.MSOrder == MSOrderType.Ms2;
            IsolationWindowInfo[] isolationWindows;
            if (isMs1)
            {
                isolationWindows = new[] { GetMs1IsolationWindow(evt) };
            }
            else
            {
                isolationWindows = evt == null ? Array.Empty<IsolationWindowInfo>() : GetMs2IsolationWindows(raw, scan, evt).ToArray();
            }
            scans.Add(new IndexedScan(scan, rtMinutes, isMs1, isMs2, isolationWindows, scanWindowLower, scanWindowUpper));
        }

        scans.Sort((a, b) =>
        {
            int c = a.RtMinutes.CompareTo(b.RtMinutes);
            return c != 0 ? c : a.ScanNumber.CompareTo(b.ScanNumber);
        });
        Console.WriteLine($"Thermo server: indexed {scans.Count} scans in {clock.Elapsed.TotalSeconds:F2} s");
        return new ThermoScanIndex(scans.ToArray());
    }

    private static IsolationWindowInfo GetMs1IsolationWindow(IScanEvent? evt)
    {
        double lo = double.PositiveInfinity;
        double hi = double.NegativeInfinity;
        if (evt != null)
        {
            try
            {
                int count = evt.MassRangeCount;
                for (int i = 0; i < count; i++)
                {
                    var range = evt.GetMassRange(i);
                    if (range.High > range.Low && range.Low > 0)
                    {
                        if (range.Low < lo) lo = range.Low;
                        if (range.High > hi) hi = range.High;
                    }
                }
            }
            catch { }
        }
        if (!(hi > lo))
            return new IsolationWindowInfo(0.0, 0.0, double.PositiveInfinity);
        return new IsolationWindowInfo(lo, (lo + hi) / 2.0, hi);
    }

    private static IsolationWindowInfo GetMs2IsolationWindow(IRawDataPlus raw, int scan, IScanEvent evt, int reactionIndex)
    {
        double center = double.NaN;
        double width = double.NaN;

        try { center = evt.GetReaction(reactionIndex)?.PrecursorMass ?? double.NaN; } catch { }
        if (!(center > 0 && double.IsFinite(center)))
        {
            try { center = evt.GetMass(reactionIndex); } catch { }
        }

        try
        {
            width = evt.GetIsolationWidth(reactionIndex);
            if (!(width > 0 && double.IsFinite(width))) width = double.NaN;
        } catch { }

        if (!(width > 0 && double.IsFinite(width)))
        {
            try { width = evt.GetReaction(reactionIndex)?.IsolationWidth ?? double.NaN; } catch { }
        }

        ExtractTrailerInfo(raw, scan, out _, out _, out _, out _, out var trailerWidth, out var trailerOffset);
        if (trailerWidth > 0 && double.IsFinite(trailerWidth))
        {
            width = trailerWidth;
        }

        if (!(center > 0 && double.IsFinite(center) && width > 0 && double.IsFinite(width)))
        {
            return new IsolationWindowInfo(0.0, 0.0, double.PositiveInfinity);
        }

        double target = center;
        double offset = double.IsFinite(trailerOffset) ? trailerOffset : 0.0;
        return new IsolationWindowInfo(target - 0.5 * width + offset, target, target + 0.5 * width + offset);
    }

    private static List<IsolationWindowInfo> GetMs2IsolationWindows(IRawDataPlus raw, int scan, IScanEvent evt)
    {
        var windows = new List<IsolationWindowInfo>();
        int rxnCount = GetReactionCount(evt);
        if (rxnCount <= 0)
        {
            // If no reactions are reported, some instruments bake isolation in the filter text,
            // but this varies. Keep the old reaction-0 fallback for APIs that can still resolve it.
            var fallback = GetMs2IsolationWindow(raw, scan, evt, 0);
            if (fallback.IsUsable) windows.Add(fallback);
            return windows;
        }

        for (int i = 0; i < rxnCount; i++)
        {
            var isolation = GetMs2IsolationWindow(raw, scan, evt, i);
            if (isolation.IsUsable) windows.Add(isolation);
        }
        return windows;
    }

    private static Spectrum BuildSpectrum(IRawDataPlus raw, IndexedScan scan, IsolationWindowInfo isolation)
    {
        ReadMzAndIntensity(raw, scan.ScanNumber, out var mz, out var intensF);
        
        double injS;
        double rawOvFtT;
        int charge;
        int precursorScan;
        ExtractTrailerInfo(raw, scan.ScanNumber, out injS, out charge, out precursorScan, out rawOvFtT);

        var s = new Spectrum
        {
            ScanNumber = scan.ScanNumber,
            RtSeconds  = scan.RtMinutes * 60.0,
            MsLevel    = scan.IsMs1 ? 1 : 2,
            IsoLower   = isolation.Lower,
            IsoUpper   = isolation.Upper,
            IsoTarget  = isolation.Target,
            Charge     = charge,
            SpectrumName    = scan.ScanNumber.ToString(CultureInfo.InvariantCulture),
            PrecursorName   = precursorScan.ToString(CultureInfo.InvariantCulture),
            IonInjectionTimeS = injS,
	        ScanWindowLower = scan.ScanWindowLower,
	        ScanWindowUpper = scan.ScanWindowUpper,
	        RawOvFtt = rawOvFtT
        };
        s.Mz.AddRange(mz);
        s.Intensity.AddRange(intensF);
        return s;
    }

    private static void GetScanWindow(IScanEvent? evt2, out double swLo, out double swHi)
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

    private static void ExtractTrailerInfo(IRawDataPlus raw, int scan, out double injS, out int charge, out int precursorScan, out double rawOvFtT)
    {
        ExtractTrailerInfo(raw, scan, out injS, out charge, out precursorScan, out rawOvFtT, out _, out _);
    }

    private static void ExtractTrailerInfo(IRawDataPlus raw, int scan, out double injS, out int charge, out int precursorScan, out double rawOvFtT,
        out double isolationWidth, out double isolationOffset)
    {
        injS = 0;
        charge = 0;
        precursorScan = 0;
        rawOvFtT = 0;
        isolationWidth = double.NaN;
        isolationOffset = double.NaN;
        try
        {
            var trailers = raw.GetTrailerExtraInformation(scan); // ILogEntryAccess with Labels/Values
            int n = trailers.Length;
            int masterScanNumber = 0;
            int masterIndex = 0;
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
                if (masterScanNumber == 0 && label.IndexOf("Master Scan Number", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (int.TryParse(ExtractInteger(value), NumberStyles.Integer, CultureInfo.InvariantCulture, out var ps))
                        masterScanNumber = ps;
                }
                if (masterIndex == 0 && label.IndexOf("Master Index", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (int.TryParse(ExtractInteger(value), NumberStyles.Integer, CultureInfo.InvariantCulture, out var ps))
                        masterIndex = ps;
                }
                if (rawOvFtT == 0 && label.IndexOf("RawOvFtT", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (TryParseNumber(value, out var parsedRawOvFtT))
                        rawOvFtT = parsedRawOvFtT;
                }
                if (!(isolationWidth > 0) && label.IndexOf("Isolation Width", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (TryParseNumber(value, out var parsedIsolationWidth))
                        isolationWidth = parsedIsolationWidth;
                }
                if (!double.IsFinite(isolationOffset) && label.IndexOf("Isolation Offset", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (TryParseNumber(value, out var parsedIsolationOffset))
                        isolationOffset = parsedIsolationOffset;
                }
            }
            precursorScan = masterScanNumber > 0 ? masterScanNumber : masterIndex;
        }
        catch { }
    }

    public override Task<ScanMetadataReply> GetScanMetadata(ScanMetadataRequest request, ServerCallContext context)
    {
		using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
        try
        {
            var raw = Get(request.SessionId);

            var reply = new ScanMetadataReply();
            AddStatusLogValues(raw, request.ScanNumber, reply);
            return Task.FromResult(reply);
        }
        catch (RpcException) { throw; }
        catch
        {
            return Task.FromResult(new ScanMetadataReply());
        }
    }

    private static void AddStatusLogValues(IRawDataPlus raw, int scan, ScanMetadataReply reply)
    {
        if (reply == null) return;
        try
        {
            double rt = raw.RetentionTimeFromScanNumber(scan);
            var statusLog = raw.GetStatusLogForRetentionTime(rt);
            int n = statusLog.Length;
            for (int i = 0; i < n; i++)
            {
                string label = StripTrailingColon(statusLog.Labels?[i] ?? string.Empty);
                string value = statusLog.Values?[i] ?? string.Empty;
                if (!string.IsNullOrWhiteSpace(label) && !string.IsNullOrWhiteSpace(value))
                {
                    reply.Properties.Add(label);
                    reply.Values.Add(value);
                }
            }
        }
        catch { }
    }

    private static string StripTrailingColon(string label)
    {
        if (string.IsNullOrWhiteSpace(label)) return string.Empty;
        label = label.Trim();
        return label.EndsWith(":", StringComparison.Ordinal) ? label.Substring(0, label.Length - 1) : label;
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
		using var throttle = ProcessingThrottle.Enter(context.CancellationToken);
	    try
	    {
	        var raw = Get(request.SessionId);
	
	        var kv = new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
	
	        void Add(string k, object? v)
			{
			    if (v is null) return;
			    var s = v.ToString();
			    if (!string.IsNullOrWhiteSpace(s)) kv[k] = s;
			}

	        void AddProperty(string k, object? owner, string propertyName)
	        {
	            if (owner == null) return;
	            try
	            {
	                var prop = owner.GetType().GetProperty(propertyName);
	                Add(k, prop?.GetValue(owner));
	            }
	            catch { }
	        }
	
	        // --- File / run header ---
	        try { Add("file.path", raw.FileName); } catch { }
	        AddProperty("thermo.creation_date", raw, "CreationDate");
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
	        object runHeader = raw.RunHeaderEx != null ? (object)raw.RunHeaderEx : raw.RunHeader;
	        AddProperty("thermo.run.expected_run_time", runHeader, "ExpectedRunTime");
	        AddProperty("thermo.run.max_integrated_intensity", runHeader, "MaxIntegratedIntensity");
	        AddProperty("thermo.run.spectra_count", runHeader, "SpectraCount");
	        try
	        {
	            var startDateProp = raw.GetType().GetProperty("CreationDate")
	                ?? raw.GetType().GetProperty("DateCreated")
	                ?? raw.GetType().GetProperty("AcquisitionDate")
	                ?? raw.GetType().GetProperty("StartDate");
	            var startDateValue = startDateProp?.GetValue(raw);
	            if (startDateValue is DateTime dt) Add("run.start_time_iso8601", dt.ToUniversalTime().ToString("O"));
	            else if (startDateValue is DateTimeOffset dto) Add("run.start_time_iso8601", dto.ToUniversalTime().ToString("O"));
	        }
	        catch { }
	
	        // If you already compute these elsewhere, reuse them; otherwise:
	        var gradientSeconds = Math.Max(0.0, (endMin - startMin) * 60.0);
	        Add("run.gradient_length_seconds", gradientSeconds);
	        Add("thermo.run.duration_minutes", Math.Max(0.0, endMin - startMin));
	
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

	        try
	        {
	            var sample = raw.SampleInformation;
	            AddProperty("thermo.sample.injection_volume", sample, "InjectionVolume");
	            AddProperty("thermo.sample.instrument_method_file", sample, "InstrumentMethodFile");
	            AddProperty("thermo.sample.raw_file_name", sample, "RawFileName");
	            AddProperty("thermo.sample.vial", sample, "Vial");
	        }
	        catch { }

	        try
	        {
	            int methodCount = raw.InstrumentMethodsCount;
	            Add("thermo.instrument_method.count", methodCount);
	            var methodNames = raw.GetAllInstrumentNamesFromInstrumentMethod();
	            for (int methodIndex = 0; methodIndex < methodCount; methodIndex++)
	            {
	                var methodName = methodNames?.ElementAtOrDefault(methodIndex);
	                if (!string.IsNullOrWhiteSpace(methodName)) Add($"thermo.instrument_method.{methodIndex}.name", methodName);
	                Add($"thermo.instrument_method.{methodIndex}.raw_text", raw.GetInstrumentMethod(methodIndex));
	            }
	        }
	        catch { }

	        try
	        {
	            if (raw.GetTuneDataCount() > 0)
	            {
	                var tune = raw.GetTuneData(0);
	                AddLogEntryValue("thermo.tune.0.spray_voltage_positive", tune, "Spray Voltage (+)");
	                AddLogEntryValue("thermo.tune.0.spray_voltage_negative", tune, "Spray Voltage (-)");
	                AddLogEntryValue("thermo.tune.0.ion_transfer_tube_temperature_positive", tune, "Ion Transfer Tube Temperature (+ or +-)");
	                AddLogEntryValue("thermo.tune.0.ion_transfer_tube_temperature_negative", tune, "Ion Transfer Tube Temperature (-)");
	            }
	        }
	        catch { }

	        void AddLogEntryValue(string key, dynamic logEntry, string label)
	        {
	            try
	            {
	                int n = logEntry.Length;
	                for (int i = 0; i < n; i++)
	                {
	                    string current = StripTrailingColon(logEntry.Labels?[i] ?? string.Empty);
	                    if (string.Equals(current, label, StringComparison.OrdinalIgnoreCase))
	                    {
	                        Add(key, logEntry.Values?[i]);
	                        return;
	                    }
	                }
	            }
	            catch { }
	        }
	
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
