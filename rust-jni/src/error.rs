use thiserror::Error;
use jni::JNIEnv;

#[derive(Debug, Error)]
pub enum NativeError {
    #[error("I/O error: {0}")]
    Io(String),
    #[error("SQLite error: {0}")]
    Sql(String),
    #[error("TIMS reader error: {0}")]
    Tims(String),
    #[error("Invalid argument: {0}")]
    Arg(String),
    #[error("Internal error: {0}")]
    Internal(String),
}

impl From<timsrust::readers::FrameReaderError> for NativeError {
    fn from(e: timsrust::readers::FrameReaderError) -> Self { Self::Tims(e.to_string()) }
}
impl From<timsrust::readers::MetadataReaderError> for NativeError {
    fn from(e: timsrust::readers::MetadataReaderError) -> Self { Self::Tims(e.to_string()) }
}
impl From<anyhow::Error> for NativeError {
    fn from(e: anyhow::Error) -> Self { Self::Internal(e.to_string()) }
}

pub fn throw_java(env: &mut JNIEnv, err: &NativeError) {
    let (class, msg) = match err {
        NativeError::Io(m)        => ("org/searlelab/msrawjava/exceptions/TdfIOException", m.clone()),
        NativeError::Sql(m)       => ("org/searlelab/msrawjava/exceptions/TdfSQLException", m.clone()),
        NativeError::Tims(m)      => ("org/searlelab/msrawjava/exceptions/TdfFormatException", m.clone()),
        NativeError::Arg(m)       => ("java/lang/IllegalArgumentException", m.clone()),
        NativeError::Internal(m)  => ("java/lang/RuntimeException", m.clone()),
    };
    let _ = env.throw_new(class, msg);
}