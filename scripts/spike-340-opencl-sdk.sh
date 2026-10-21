#!/usr/bin/env bash
# Spike #340: fetch the Khronos OpenCL headers and build the ICD loader for arm64-v8a, into
# ml/opencl-sdk/ (gitignored). ml's CMake turns on llama.cpp's OpenCL backend when that directory exists.
# The loader is a link stub only: the APK excludes it, and the phone's own /vendor/lib64/libOpenCL.so
# is what libggml-opencl.so loads at run time.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="$(sed -n 's/^sdk.dir=//p' "$REPO_ROOT/local.properties")"
NDK="${ANDROID_NDK:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
CMAKE_BIN="$SDK/cmake/3.22.1/bin"
OUT="$REPO_ROOT/ml/opencl-sdk"
TAG="v2024.10.24"

rm -rf "$OUT" && mkdir -p "$OUT/src" "$OUT/include" "$OUT/lib"
git clone -q --depth 1 --branch "$TAG" https://github.com/KhronosGroup/OpenCL-Headers "$OUT/src/OpenCL-Headers"
git clone -q --depth 1 --branch "$TAG" https://github.com/KhronosGroup/OpenCL-ICD-Loader "$OUT/src/OpenCL-ICD-Loader"
cp -r "$OUT/src/OpenCL-Headers/CL" "$OUT/include/"

"$CMAKE_BIN/cmake" -S "$OUT/src/OpenCL-ICD-Loader" -B "$OUT/src/build" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$CMAKE_BIN/ninja" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DOPENCL_ICD_LOADER_HEADERS_DIR="$OUT/include" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=26 -DANDROID_STL=c++_shared \
  -DBUILD_TESTING=OFF
"$CMAKE_BIN/ninja" -C "$OUT/src/build" OpenCL
cp "$OUT/src/build/libOpenCL.so" "$OUT/lib/"
echo "OpenCL SDK ready in $OUT (NDK $(basename "$NDK"))"
