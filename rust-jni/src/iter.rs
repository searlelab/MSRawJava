use std::cmp::{max, min};
use timsrust::MSLevel;
use timsrust::converters::ConvertableDomain;
use crate::state::Dataset;

pub struct SpectrumArrays {
    pub mz: Vec<f64>,
    pub im: Vec<f32>,
    pub intensity: Vec<f32>,
    pub ms_level: u32,     // 0=Unknown, 1=MS1, 2=MS2
    pub frame_index: u32,
    pub rt_seconds: f64,
}

pub struct RtIterator {
    ds: std::sync::Arc<Dataset>,
    frames_sorted_by_rt: Vec<usize>,
    mz_lo: f64,
    mz_hi: f64,
    scan_lo: usize,
    scan_hi: usize,
    i: usize,
}

impl RtIterator {
    pub fn new(
        ds: std::sync::Arc<Dataset>,
        indices: Vec<usize>,
        mz_lo: f64,
        mz_hi: f64,
        scan_lo: i32,
        scan_hi: i32
    ) -> Self {
        let mut with_rt: Vec<(usize, f64)> = indices
            .into_iter()
            .filter_map(|idx| ds.frames.get(idx).ok().map(|fr| (idx, fr.rt_in_seconds)))
            .collect();
        with_rt.sort_by(|a, b| a.1.partial_cmp(&b.1).unwrap());
        let frames_sorted_by_rt = with_rt.into_iter().map(|x| x.0).collect();

        let s_lo = if scan_lo < 0 { 0 } else { scan_lo as usize };
        let s_hi = if scan_hi < 0 { usize::MAX } else { scan_hi as usize };

        Self { ds, frames_sorted_by_rt, mz_lo, mz_hi, scan_lo: s_lo, scan_hi: s_hi, i: 0 }
    }

    /// Extract a single spectrum from one frame. `frame_idx` is zero-based.
    /// `scan_lo_inclusive` and `scan_hi_inclusive` are clamped to the frame.
    /// Returns Ok(Some(...)) if the frame yields points, Ok(None) if the frame has zero scans.
    pub fn extract_for_frame(
        ds: &Dataset,
        frame_idx: usize,
        mz_lo: f64,
        mz_hi: f64,
        scan_lo_inclusive: usize,
        scan_hi_inclusive: usize
    ) -> Result<Option<SpectrumArrays>, String> {
        // Pull frame
        let frame = ds.frames.get(frame_idx).map_err(|e| e.to_string())?;
        let ms_level = match frame.ms_level {
            MSLevel::MS1 => 1u32,
            MSLevel::MS2 => 2u32,
            MSLevel::Unknown => 0u32,
        };
        let rt_seconds = frame.rt_in_seconds;

        // Clamp scan bounds to the frame
        let sc_count = frame.scan_offsets.len();
        if sc_count == 0 {
			return Ok(Some(SpectrumArrays {
			    mz: Vec::new(),
			    im: Vec::new(),
			    intensity: Vec::new(),
			    ms_level,
			    frame_index: frame_idx as u32,
			    rt_seconds,
			}));
        }
        let s_lo = min(max(scan_lo_inclusive, 0), sc_count.saturating_sub(1));
        let s_hi = min(max(scan_hi_inclusive, 0), sc_count.saturating_sub(1));
        let (s_lo, s_hi) = if s_lo <= s_hi { (s_lo, s_hi) } else { (s_hi, s_lo) };

        // m/z to TOF window
        if !(mz_lo.is_finite() && mz_hi.is_finite() && mz_lo < mz_hi) {
            return Err("Invalid m/z window".to_string());
        }
        let mzc = &ds.meta.mz_converter;
        let mut tof_lo = mzc.invert(mz_lo).floor();
        let mut tof_hi = mzc.invert(mz_hi).ceil();
        if !tof_lo.is_finite() || !tof_hi.is_finite() {
            return Err("m/z window inversion failed".to_string());
        }
        if tof_lo < 0.0 { tof_lo = 0.0; }
        if tof_hi < 0.0 { tof_hi = 0.0; }
        let tof_lo = tof_lo as u32;
        let tof_hi = tof_hi as u32;

        // Reserve capacity
        let mut approx = 0usize;
        for s in s_lo..=s_hi {
            let start = frame.scan_offsets[s];
            let end = if s + 1 < sc_count { frame.scan_offsets[s + 1] } else { frame.tof_indices.len() };
            approx += end.saturating_sub(start);
        }
        let mut mz = Vec::<f64>::with_capacity(approx / 2 + 16);
        let mut im = Vec::<f32>::with_capacity(approx / 2 + 16);
        let mut intensity = Vec::<f32>::with_capacity(approx / 2 + 16);

        let imc = &ds.meta.im_converter;

        // Collect points
        for s in s_lo..=s_hi {
            let start = frame.scan_offsets[s];
            let end = if s + 1 < sc_count { frame.scan_offsets[s + 1] } else { frame.tof_indices.len() };
            let im_val = imc.convert(s as u32) as f32;
            for j in start..end {
                let tof = frame.tof_indices[j];
                if tof < tof_lo || tof > tof_hi { continue; }
                let iz = frame.get_corrected_intensity(j) as f32;
                if iz <= 0.0 { continue; }
                mz.push(mzc.convert(tof));
                im.push(im_val);
                intensity.push(iz);
            }
        }

        Ok(Some(SpectrumArrays {
            mz, im, intensity, ms_level,
            frame_index: frame_idx as u32,
            rt_seconds
        }))
    }

    pub fn next(&mut self) -> Result<Option<SpectrumArrays>, String> {
        while self.i < self.frames_sorted_by_rt.len() {
            let frame_idx = self.frames_sorted_by_rt[self.i];
            self.i += 1;

            // Delegate to the per-frame extractor
            match RtIterator::extract_for_frame(self.ds.as_ref(), frame_idx, self.mz_lo, self.mz_hi, self.scan_lo, self.scan_hi)? {
                Some(sa) => return Ok(Some(sa)),
                None => continue,
            }
        }
        Ok(None)
    }
}