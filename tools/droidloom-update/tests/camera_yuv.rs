//! Exercise the exact raw input conversion used by the Android camera HAL.
use std::{path::Path, process::Command};

#[test]
fn camera_yu12_preserves_planes_and_rejects_incomplete_frames() {
    let repo = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let temp = tempfile::tempdir().unwrap();
    let binary = temp.path().join("camera-yuv-test");
    let compile = Command::new("c++")
        .args(["-std=c++17", "-Wall", "-Wextra", "-Werror", "-I"])
        .arg(repo.join("android/camera/include"))
        .arg(repo.join("android/camera/tests/yuv420_input.cpp"))
        .arg("-o")
        .arg(&binary)
        .output()
        .unwrap();
    assert!(
        compile.status.success(),
        "{}",
        String::from_utf8_lossy(&compile.stderr)
    );
    assert!(Command::new(binary).status().unwrap().success());
}
