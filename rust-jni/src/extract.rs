// src/extract.rs
use timsrust::converters::ConvertableDomain; // enables .convert()
use crate::state::Dataset;

#[derive(Debug)]
pub struct FrameMzIntScan {
    pub mz: Vec<f64>,
    pub intensity: Vec<f64>, // corrected using frame.get_corrected_intensity(...)
    pub scan: Vec<i32>,      // raw scan indices, not IM
}

pub fn collect_frame_mz_int_scan(ds: &Dataset, frame_idx: usize)
    -> Result<FrameMzIntScan, String>
{
    let frame = ds.frames.get(frame_idx).map_err(|e| e.to_string())?;
    let sc_count = frame.scan_offsets.len();
    if sc_count == 0 {
        return Ok(FrameMzIntScan { mz: vec![], intensity: vec![], scan: vec![] });
    }
    collect_frame_mz_int_scan_range(ds, frame_idx, 0, sc_count - 1)
}

pub fn collect_frame_mz_int_scan_range(
    ds: &Dataset,
    frame_idx: usize,
    s_lo_inclusive: usize,
    s_hi_inclusive: usize,
) -> Result<FrameMzIntScan, String> {
    let frame = ds.frames.get(frame_idx).map_err(|e| e.to_string())?;
    let sc_count = frame.scan_offsets.len();
    if sc_count == 0 {
        return Ok(FrameMzIntScan { mz: vec![], intensity: vec![], scan: vec![] });
    }

    let mzc = &ds.meta.mz_converter;

    // clamp + order
    let s_lo = s_lo_inclusive.min(sc_count.saturating_sub(1));
    let s_hi = s_hi_inclusive.min(sc_count.saturating_sub(1));
    let (s_lo, s_hi) = if s_lo <= s_hi { (s_lo, s_hi) } else { (s_hi, s_lo) };

    // capacity hint
    let mut approx = 0usize;
    for s in s_lo..=s_hi {
        let start = frame.scan_offsets[s];
        let end = if s + 1 < sc_count { frame.scan_offsets[s + 1] } else { frame.tof_indices.len() };
        approx += end.saturating_sub(start);
    }

    let mut mz = Vec::<f64>::with_capacity(approx);
    let mut intensity = Vec::<f64>::with_capacity(approx);
    let mut scan = Vec::<i32>::with_capacity(approx);

    for s in s_lo..=s_hi {
        let start = frame.scan_offsets[s];
        let end = if s + 1 < sc_count { frame.scan_offsets[s + 1] } else { frame.tof_indices.len() };
        for j in start..end {
            let tof = frame.tof_indices[j];
            let mz_val = mzc.convert(tof);
            let iz = frame.get_corrected_intensity(j);
            mz.push(mz_val);
            intensity.push(iz);
            scan.push(s as i32);
        }
    }
    Ok(FrameMzIntScan { mz, intensity, scan })
}

#[derive(Debug)]
pub struct FrameTofIntScan {
    pub tof: Vec<u32>,        // raw TOF sample indices (absolute, already delta-decoded)
    pub intensity: Vec<u32>,  // raw intensities (u32), no correction
    pub scan: Vec<i32>,       // scan index per peak
}

/// Whole-frame (ZERO-BASED frame index)
pub fn collect_frame_tof_int_scan(ds: &Dataset, frame_idx: usize)
    -> Result<FrameTofIntScan, String>
{
    let frame = ds.frames.get(frame_idx).map_err(|e| e.to_string())?;
    let sc_count = frame.scan_offsets.len();
    if sc_count == 0 {
        return Ok(FrameTofIntScan { tof: vec![], intensity: vec![], scan: vec![] });
    }
    collect_frame_tof_int_scan_range(ds, frame_idx, 0, sc_count - 1)
}

/// Scan range (inclusive). Indices will be clamped/order-corrected inside.
pub fn collect_frame_tof_int_scan_range(
    ds: &Dataset,
    frame_idx: usize,
    s_lo_inclusive: usize,
    s_hi_inclusive: usize,
) -> Result<FrameTofIntScan, String> {
    let frame = ds.frames.get(frame_idx).map_err(|e| e.to_string())?;
    let sc_count = frame.scan_offsets.len();
    if sc_count == 0 {
        return Ok(FrameTofIntScan { tof: vec![], intensity: vec![], scan: vec![] });
    }

    // clamp + order
    let s_lo = s_lo_inclusive.min(sc_count.saturating_sub(1));
    let s_hi = s_hi_inclusive.min(sc_count.saturating_sub(1));
    let (s_lo, s_hi) = if s_lo <= s_hi { (s_lo, s_hi) } else { (s_hi, s_lo) };

    // capacity hint
    let mut approx = 0usize;
    for s in s_lo..=s_hi {
        let start = frame.scan_offsets[s];
        let end = if s + 1 < sc_count { frame.scan_offsets[s + 1] } else { frame.tof_indices.len() };
        approx += end.saturating_sub(start);
    }

    let mut tof = Vec::<u32>::with_capacity(approx);
    let mut intensity = Vec::<u32>::with_capacity(approx);
    let mut scan = Vec::<i32>::with_capacity(approx);

    for s in s_lo..=s_hi {
        let start = frame.scan_offsets[s];
        let end = if s + 1 < sc_count { frame.scan_offsets[s + 1] } else { frame.tof_indices.len() };
        for j in start..end {
            tof.push(frame.tof_indices[j]);
            intensity.push(frame.intensities[j]);
            scan.push(s as i32);
        }
    }

    Ok(FrameTofIntScan { tof, intensity, scan })
}