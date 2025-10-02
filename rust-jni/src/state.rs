use std::{collections::HashMap, sync::Arc};
use once_cell::sync::Lazy;
use parking_lot::RwLock;
use std::sync::atomic::{AtomicU64, Ordering};

use timsrust::readers::{FrameReader, MetadataReader};
use timsrust::Metadata;

pub struct Dataset {
    pub path: String,
    pub frames: FrameReader,
    pub meta: Metadata,  // includes converters for TOF→m/z and scan→1/k0
}

// Global registry of open datasets, addressed by opaque handles
static NEXT_ID: AtomicU64 = AtomicU64::new(1);
static REGISTRY:   Lazy<RwLock<HashMap<u64, Arc<Dataset>>>> = Lazy::new(|| RwLock::new(HashMap::new()));

pub fn open_dataset(path: &str) -> Result<u64, String> {
    let frames = FrameReader::new(path).map_err(|e| e.to_string())?;
    let meta   = MetadataReader::new(path).map_err(|e| e.to_string())?;
    let ds = Arc::new(Dataset { path: path.to_string(), frames, meta });
    let id = NEXT_ID.fetch_add(1, Ordering::SeqCst);
    REGISTRY.write().insert(id, ds);
    Ok(id)
}

pub fn get_dataset(id: u64) -> Option<Arc<Dataset>> {
    REGISTRY.read().get(&id).cloned()
}

pub fn close_dataset(id: u64) -> bool {
    REGISTRY.write().remove(&id).is_some()
}
