use jni::objects::{JClass, JIntArray, JString, JObject};
use jni::sys::{jint, jlong, jobject};
use jni::JNIEnv;

use crate::state::{open_dataset, close_dataset, get_dataset};
use crate::extract;
use crate::error::{NativeError, throw_java};

fn throw(env: &mut JNIEnv, class: &str, msg: &str) {
    let _ = env.throw_new(class, msg);
}

// JNI class: org.searlelab.timsjava.TimsNative

#[no_mangle]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_openDataset(
    mut env: JNIEnv, _cls: JClass, jpath: JString
) -> jlong {
    let path: String = match env.get_string(&jpath) {
        Ok(s) => s.into(),
        Err(e) => { throw(&mut env, "java/lang/IllegalArgumentException", &format!("bad path: {e}")); return 0; }
    };
    match open_dataset(&path) {
        Ok(id) => id as jlong,
        Err(e) => { throw(&mut env, "java/io/IOException", &e); 0 }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_closeDataset(
    mut env: JNIEnv, _cls: JClass, handle: jlong
) {
    if handle == 0 { return; }
    if !close_dataset(handle as u64) {
        throw(&mut env, "java/lang/IllegalStateException", "unknown dataset handle");
    }
}

fn pack_arrays<'a>(
    env: &mut JNIEnv<'a>,
    mz: &[f64],
    intensity: &[f64],
    scan: &[i32],
) -> Option<jobject> {
    let obj_cls = env.find_class("java/lang/Object").ok()?;
    let out = env.new_object_array(3, obj_cls, JObject::null()).ok()?;

    let mz_arr = env.new_double_array(mz.len() as i32).ok()?;
    env.set_double_array_region(&mz_arr, 0, mz).ok()?;
    let iz_arr = env.new_double_array(intensity.len() as i32).ok()?;
    env.set_double_array_region(&iz_arr, 0, intensity).ok()?;
    let sc_arr = env.new_int_array(scan.len() as i32).ok()?;
    env.set_int_array_region(&sc_arr, 0, scan).ok()?;

    let _ = env.set_object_array_element(&out, 0, JObject::from(mz_arr));
    let _ = env.set_object_array_element(&out, 1, JObject::from(iz_arr));
    let _ = env.set_object_array_element(&out, 2, JObject::from(sc_arr));
    Some(out.into_raw())
}

fn pack_int_triple<'a>(
    env: &mut JNIEnv<'a>,
    a0: &[i32],
    a1: &[i32],
    a2: &[i32],
) -> Option<jobject> {
    let obj_cls = env.find_class("java/lang/Object").ok()?;
    let out = env.new_object_array(3, obj_cls, JObject::null()).ok()?;

    let arr0 = env.new_int_array(a0.len() as i32).ok()?;
    env.set_int_array_region(&arr0, 0, a0).ok()?;
    let arr1 = env.new_int_array(a1.len() as i32).ok()?;
    env.set_int_array_region(&arr1, 0, a1).ok()?;
    let arr2 = env.new_int_array(a2.len() as i32).ok()?;
    env.set_int_array_region(&arr2, 0, a2).ok()?;

    let _ = env.set_object_array_element(&out, 0, JObject::from(arr0));
    let _ = env.set_object_array_element(&out, 1, JObject::from(arr1));
    let _ = env.set_object_array_element(&out, 2, JObject::from(arr2));
    Some(out.into_raw())
}

/// Existing whole-frame reader stays as-is:
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_readRawFrame(
    mut env: JNIEnv,
    _cls: JClass,
    handle: jlong,
    frame_index_zero_based: jint,
) -> jobject {
    let ds = match get_dataset(handle as u64) {
        Some(ds) => ds,
        None => { let _ = env.throw_new("java/lang/IllegalStateException", "invalid dataset handle"); return std::ptr::null_mut(); }
    };
    if frame_index_zero_based < 0 {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "frameIndex must be >= 0");
        return std::ptr::null_mut();
    }
    let idx = frame_index_zero_based as usize;

    let res = match extract::collect_frame_mz_int_scan(ds.as_ref(), idx) {
        Ok(r) => r,
        Err(e) => { let err = NativeError::Tims(e); throw_java(&mut env, &err); return std::ptr::null_mut(); }
    };
    pack_arrays(&mut env, &res.mz, &res.intensity, &res.scan).unwrap_or(std::ptr::null_mut())
}

/// New: scan-range (inclusive) reader.
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_readRawFrameRange(
    mut env: JNIEnv,
    _cls: JClass,
    handle: jlong,
    frame_index_zero_based: jint,
    scan_lo_inclusive: jint,
    scan_hi_inclusive: jint,
) -> jobject {
    let ds = match get_dataset(handle as u64) {
        Some(ds) => ds,
        None => { let _ = env.throw_new("java/lang/IllegalStateException", "invalid dataset handle"); return std::ptr::null_mut(); }
    };
    if frame_index_zero_based < 0 {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "frameIndex must be >= 0");
        return std::ptr::null_mut();
    }
    let idx = frame_index_zero_based as usize;

    // Clamp/interpret bounds
    let frame = match ds.frames.get(idx) {
        Ok(fr) => fr,
        Err(e) => { let err = NativeError::Tims(e.to_string()); throw_java(&mut env, &err); return std::ptr::null_mut(); }
    };
    let sc_count = frame.scan_offsets.len();

    if sc_count == 0 {
        return pack_arrays(&mut env, &[], &[], &[]).unwrap_or(std::ptr::null_mut());
    }

    let s_lo = if scan_lo_inclusive < 0 { 0 } else { scan_lo_inclusive as usize };
    let s_hi = if scan_hi_inclusive < 0 { sc_count.saturating_sub(1) } else { scan_hi_inclusive as usize };

    let res = match extract::collect_frame_mz_int_scan_range(ds.as_ref(), idx, s_lo, s_hi) {
        Ok(r) => r,
        Err(e) => { let err = NativeError::Tims(e); throw_java(&mut env, &err); return std::ptr::null_mut(); }
    };
    pack_arrays(&mut env, &res.mz, &res.intensity, &res.scan).unwrap_or(std::ptr::null_mut())
}

// Convert &[u32] → Vec<i32> (JNI int[]); note: u32>i32 will wrap if >2^31-1 (rare for tof)
fn u32_to_i32_slice(v: &[u32]) -> Vec<i32> {
    v.iter().map(|&x| x as i32).collect()
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_readRawFrameTofIntRange(
    mut env: JNIEnv,
    _cls: JClass,
    handle: jlong,
    frame_index_zero_based: jint,
    scan_lo_inclusive: jint,
    scan_hi_inclusive: jint,
) -> jobject {
    let ds = match get_dataset(handle as u64) {
        Some(ds) => ds,
        None => { let _ = env.throw_new("java/lang/IllegalStateException", "invalid dataset handle"); return std::ptr::null_mut(); }
    };
    if frame_index_zero_based < 0 {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "frameIndex must be >= 0");
        return std::ptr::null_mut();
    }
    let idx = frame_index_zero_based as usize;

    // Clamp/interpret bounds to the frame
    let frame = match ds.frames.get(idx) {
        Ok(fr) => fr,
        Err(e) => { let err = NativeError::Tims(e.to_string()); throw_java(&mut env, &err); return std::ptr::null_mut(); }
    };
    let sc_count = frame.scan_offsets.len();
    if sc_count == 0 {
        return pack_int_triple(&mut env, &[], &[], &[]).unwrap_or(std::ptr::null_mut());
    }
    let s_lo = if scan_lo_inclusive < 0 { 0 } else { scan_lo_inclusive as usize };
    let s_hi = if scan_hi_inclusive < 0 { sc_count.saturating_sub(1) } else { scan_hi_inclusive as usize };

    let res = match extract::collect_frame_tof_int_scan_range(ds.as_ref(), idx, s_lo, s_hi) {
        Ok(r) => r,
        Err(e) => { let err = NativeError::Tims(e); throw_java(&mut env, &err); return std::ptr::null_mut(); }
    };

    let tof_i32 = u32_to_i32_slice(&res.tof);
    let iz_i32  = u32_to_i32_slice(&res.intensity);
    pack_int_triple(&mut env, &tof_i32, &iz_i32, &res.scan).unwrap_or(std::ptr::null_mut())
}