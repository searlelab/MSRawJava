use jni::objects::{JClass, JIntArray, JString, JObject};
use jni::sys::{jlong, jobject};
use jni::JNIEnv;

use crate::state::{open_dataset, close_dataset, get_dataset};
use crate::iter::RtIterator;
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

#[no_mangle]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_createIterator(
    mut env: JNIEnv, _cls: JClass, handle: jlong, frame_indices: JIntArray,
    mz_lo: jni::sys::jdouble, mz_hi: jni::sys::jdouble,
    scan_lo: jni::sys::jint, scan_hi: jni::sys::jint
) -> jlong {
    let ds = match get_dataset(handle as u64) {
        Some(ds) => ds,
        None => { throw(&mut env, "java/lang/IllegalStateException", "invalid dataset handle"); return 0; }
    };

    let len = match env.get_array_length(&frame_indices) {
        Ok(l) => l as usize,
        Err(e) => { throw(&mut env, "java/lang/IllegalArgumentException", &format!("bad indices: {e}")); return 0; }
    };
    let mut indices = vec![0i32; len];
    if let Err(e) = env.get_int_array_region(&frame_indices, 0, &mut indices) {
        throw(&mut env, "java/lang/IllegalArgumentException", &format!("read indices failed: {e}"));
        return 0;
    }
    let indices: Vec<usize> = indices.into_iter().filter(|&x| x >= 0).map(|x| x as usize).collect();
    if indices.is_empty() {
        throw(&mut env, "java/lang/IllegalArgumentException", "frameIndices is empty");
        return 0;
    }
    if !(mz_lo.is_finite() && mz_hi.is_finite() && (mz_lo < mz_hi)) {
        throw(&mut env, "java/lang/IllegalArgumentException", "invalid m/z window");
        return 0;
    }

    let iter = RtIterator::new(ds, indices, mz_lo as f64, mz_hi as f64, scan_lo, scan_hi);
    let boxed = Box::new(iter);
    Box::into_raw(boxed) as usize as jlong
}

#[no_mangle]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_destroyIterator(
    _env: JNIEnv, _cls: JClass, iter_handle: jlong
) {
    if iter_handle == 0 { return; }
    unsafe { drop(Box::from_raw(iter_handle as usize as *mut RtIterator)); }
}

#[no_mangle]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_next(
    mut env: JNIEnv, _cls: JClass, iter_handle: jlong
) -> jobject {
    if iter_handle == 0 {
        throw(&mut env, "java/lang/IllegalStateException", "iterator handle is 0");
        return std::ptr::null_mut();
    }
    let iter = unsafe { &mut *(iter_handle as usize as *mut RtIterator) };
    match iter.next() {
        Ok(Some(arr)) => {
            // Build primitive arrays
            let mz = match env.new_double_array(arr.mz.len() as i32) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("mz alloc: {e}")); return std::ptr::null_mut(); }
            };
            if let Err(e) = env.set_double_array_region(&mz, 0, &arr.mz) {
                throw(&mut env, "java/lang/RuntimeException", &format!("mz fill: {e}"));
                return std::ptr::null_mut();
            }

            let im = match env.new_float_array(arr.im.len() as i32) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("im alloc: {e}")); return std::ptr::null_mut(); }
            };
            if let Err(e) = env.set_float_array_region(&im, 0, &arr.im) {
                throw(&mut env, "java/lang/RuntimeException", &format!("im fill: {e}"));
                return std::ptr::null_mut();
            }

            let inten = match env.new_float_array(arr.intensity.len() as i32) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("intensity alloc: {e}")); return std::ptr::null_mut(); }
            };
            if let Err(e) = env.set_float_array_region(&inten, 0, &arr.intensity) {
                throw(&mut env, "java/lang/RuntimeException", &format!("intensity fill: {e}"));
                return std::ptr::null_mut();
            }

            // Create Object[]: [double[] mz, float[] ims, float[] intensity, Integer msLevel, Integer frameIndex, Double rtSeconds]
            let obj_cls = match env.find_class("java/lang/Object") {
                Ok(c) => c,
                Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("find Object: {e}")); return std::ptr::null_mut(); }
            };
            let out = match env.new_object_array(6, obj_cls, JObject::null()) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("Object[] alloc: {e}")); return std::ptr::null_mut(); }
            };

            // IMPORTANT: pass references to avoid moving the handle types
            let _ = env.set_object_array_element(&out, 0, JObject::from(mz));
            let _ = env.set_object_array_element(&out, 1, JObject::from(im));
            let _ = env.set_object_array_element(&out, 2, JObject::from(inten));

            let ms = match env.new_object("java/lang/Integer", "(I)V", &[ (arr.ms_level as i32).into() ]) {
                Ok(o) => o, Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("box msLevel: {e}")); return std::ptr::null_mut(); }
            };
            let fi = match env.new_object("java/lang/Integer", "(I)V", &[ (arr.frame_index as i32).into() ]) {
                Ok(o) => o, Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("box frameIndex: {e}")); return std::ptr::null_mut(); }
            };
            let rt = match env.new_object("java/lang/Double", "(D)V", &[ arr.rt_seconds.into() ]) {
                Ok(o) => o, Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("box rtSeconds: {e}")); return std::ptr::null_mut(); }
            };

            let _ = env.set_object_array_element(&out, 3, ms);
            let _ = env.set_object_array_element(&out, 4, fi);
            let _ = env.set_object_array_element(&out, 5, rt);

            out.into_raw()
        }
        Ok(None) => std::ptr::null_mut(),
        Err(e) => {
            let err = NativeError::Tims(e);
            throw_java(&mut env, &err);
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_searlelab_msrawjava_io_tims_TimsNative_readSpectrum(
    mut env: JNIEnv, _cls: JClass, handle: jlong,
    frame_id: jni::sys::jint,
    mz_lo: jni::sys::jdouble, mz_hi: jni::sys::jdouble,
    scan_lo: jni::sys::jint, scan_hi: jni::sys::jint
) -> jobject {
    let ds = match get_dataset(handle as u64) {
        Some(ds) => ds,
        None => { throw(&mut env, "java/lang/IllegalStateException", "invalid dataset handle"); return std::ptr::null_mut(); }
    };

    if frame_id <= 0 {
        throw(&mut env, "java/lang/IllegalArgumentException", "frameId must be >= 1");
        return std::ptr::null_mut();
    }
    if !(mz_lo.is_finite() && mz_hi.is_finite() && (mz_lo < mz_hi)) {
        throw(&mut env, "java/lang/IllegalArgumentException", "invalid m/z window");
        return std::ptr::null_mut();
    }

    let idx = (frame_id as i32 - 1) as usize;
    let s_lo = if scan_lo < 0 { 0usize } else { scan_lo as usize };
    let s_hi = if scan_hi < 0 { usize::MAX } else { scan_hi as usize };

    match RtIterator::extract_for_frame(ds.as_ref(), idx, mz_lo as f64, mz_hi as f64, s_lo, s_hi) {
        Ok(Some(arr)) => {
            // Build primitive arrays (same layout as `next`)
            let mz = match env.new_double_array(arr.mz.len() as i32) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("mz alloc: {e}")); return std::ptr::null_mut(); }
            };
            if let Err(e) = env.set_double_array_region(&mz, 0, &arr.mz) {
                throw(&mut env, "java/lang/RuntimeException", &format!("mz fill: {e}"));
                return std::ptr::null_mut();
            }

            let im = match env.new_float_array(arr.im.len() as i32) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("im alloc: {e}")); return std::ptr::null_mut(); }
            };
            if let Err(e) = env.set_float_array_region(&im, 0, &arr.im) {
                throw(&mut env, "java/lang/RuntimeException", &format!("im fill: {e}"));
                return std::ptr::null_mut();
            }

            let inten = match env.new_float_array(arr.intensity.len() as i32) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("intensity alloc: {e}")); return std::ptr::null_mut(); }
            };
            if let Err(e) = env.set_float_array_region(&inten, 0, &arr.intensity) {
                throw(&mut env, "java/lang/RuntimeException", &format!("intensity fill: {e}"));
                return std::ptr::null_mut();
            }

            let obj_cls = match env.find_class("java/lang/Object") {
                Ok(c) => c,
                Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("find Object: {e}")); return std::ptr::null_mut(); }
            };
            let out = match env.new_object_array(6, obj_cls, JObject::null()) {
                Ok(a) => a,
                Err(e) => { throw(&mut env, "java/lang/OutOfMemoryError", &format!("Object[] alloc: {e}")); return std::ptr::null_mut(); }
            };

            let _ = env.set_object_array_element(&out, 0, JObject::from(mz));
            let _ = env.set_object_array_element(&out, 1, JObject::from(im));
            let _ = env.set_object_array_element(&out, 2, JObject::from(inten));

            let ms = match env.new_object("java/lang/Integer", "(I)V", &[ (arr.ms_level as i32).into() ]) {
                Ok(o) => o, Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("box msLevel: {e}")); return std::ptr::null_mut(); }
            };
            let fi = match env.new_object("java/lang/Integer", "(I)V", &[ (arr.frame_index as i32).into() ]) {
                Ok(o) => o, Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("box frameIndex: {e}")); return std::ptr::null_mut(); }
            };
            let rt = match env.new_object("java/lang/Double", "(D)V", &[ arr.rt_seconds.into() ]) {
                Ok(o) => o, Err(e) => { throw(&mut env, "java/lang/RuntimeException", &format!("box rtSeconds: {e}")); return std::ptr::null_mut(); }
            };

            let _ = env.set_object_array_element(&out, 3, ms);
            let _ = env.set_object_array_element(&out, 4, fi);
            let _ = env.set_object_array_element(&out, 5, rt);

            out.into_raw()
        }
        Ok(None) => std::ptr::null_mut(),
        Err(e) => {
            let err = NativeError::Tims(e);
            throw_java(&mut env, &err);
            std::ptr::null_mut()
        }
    }
}
