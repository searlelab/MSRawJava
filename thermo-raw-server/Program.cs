using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Globalization;
using System.Linq;
using System.Net;
using System.Text.RegularExpressions;
using Grpc.Core;
using MSRaw.Thermo.Proto;
using ThermoFisher.CommonCore.Data.Business;
using ThermoFisher.CommonCore.Data.FilterEnums;   // MSOrderType
using ThermoFisher.CommonCore.Data.Interfaces;
using ThermoFisher.CommonCore.RawFileReader;
using Microsoft.AspNetCore.Server.Kestrel.Core;   // HttpProtocols

//using Grpc.AspNetCore.Server.Reflection;
//using Grpc.Reflection;
//using Grpc.Reflection.V1Alpha;

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
// builder.Services.AddGrpcReflection();

var app = builder.Build();
app.MapGrpcService<ThermoRawServiceImpl>();
// app.MapGrpcReflectionService(); // enable reflection
app.MapGet("/", () => "MSRaw Thermo gRPC ready (HTTP/2 plaintext)");
Console.WriteLine($"LISTENING h2c on {ip}:{port}");
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
	    
	    catch (Exception ex)
	    {
	        // Local dev only, return full context
	        throw new RpcException(new Status(StatusCode.Internal, "Open failed: " + ex));
	    }
    }

    public override Task<CloseReply> Close(CloseRequest request, ServerCallContext context)
    {
        if (Sessions.TryRemove(request.SessionId, out var raw))
            raw.Dispose();

        return Task.FromResult(new CloseReply { Ok = true });
    }

    public override async Task GetPrecursors(PrecursorsRequest req, IServerStreamWriter<Spectrum> stream, ServerCallContext ctx)
    {
        var raw = Get(req.SessionId);

        foreach (int scan in ScansInRt(raw, req.RtMin, req.RtMax))
        {
            var filter = raw.GetFilterForScanNumber(scan);
            if (filter.MSOrder != MSOrderType.Ms) continue; // MS1
            
            var evt = raw.GetScanEventForScanNumber(scan);
            double center = evt.GetMass(0);
            double width  = evt.GetIsolationWidth(0);
            double lo = center - 0.5 * width;
            double hi = center + 0.5 * width;

            var spec = BuildSpectrum(raw, scan, isMs1: true, isoLo: lo, isoHi: hi);
            await stream.WriteAsync(spec);
        }
    }

    public override async Task GetStripes(StripesRequest req, IServerStreamWriter<Spectrum> stream, ServerCallContext ctx)
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

    private static Spectrum BuildSpectrum(IRawDataPlus raw, int scan, bool isMs1, double isoLo, double isoHi)
    {
        var cs = raw.GetCentroidStream(scan, false);
        double[] mz = cs?.Masses ?? Array.Empty<double>();
        double[] intensD = cs?.Intensities ?? Array.Empty<double>();
        float[]  intensF = new float[intensD.Length];
        for (int i = 0; i < intensD.Length; i++) intensF[i] = (float)intensD[i];

        double injS = 0;
        int charge = 0;

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
            }
        }
        catch { }

        var s = new Spectrum
        {
            ScanNumber = scan,
            RtSeconds  = raw.RetentionTimeFromScanNumber(scan)*60f,
            MsLevel    = isMs1 ? 1 : 2,
            IsoLower   = isoLo,
            IsoUpper   = isoHi,
            Charge     = charge,
            SpectrumName    = scan.ToString(CultureInfo.InvariantCulture),
            PrecursorName   = scan.ToString(CultureInfo.InvariantCulture),
            IonInjectionTimeS = injS
        };

        s.Mz.AddRange(mz);
        s.Intensity.AddRange(intensF);
        return s;
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
}