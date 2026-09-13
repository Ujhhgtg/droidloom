// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>

namespace droidloom::camera {

// V4L2 single-planar YUV420 has Y/U/V planes; chroma stride is half the Y
// stride. Validate the full input before touching the Android output buffer.
inline bool copyYuv420(const uint8_t* input, size_t inputSize, size_t width, size_t height,
                       size_t inputStride, uint8_t* y, size_t yStride,
                       uint8_t* u, uint8_t* v, size_t chromaStride) {
    if (!input || !y || !u || !v || width == 0 || height == 0 || (width & 1) ||
        (height & 1) || inputStride < width || (inputStride & 1) || yStride < width ||
        chromaStride < width / 2 || height > inputSize / inputStride ||
        height > std::numeric_limits<size_t>::max() / yStride ||
        height / 2 > std::numeric_limits<size_t>::max() / chromaStride) {
        return false;
    }
    const size_t yBytes = inputStride * height;
    const size_t chromaBytes = (inputStride / 2) * (height / 2);
    if (chromaBytes > (inputSize - yBytes) / 2) {
        return false;
    }
    const uint8_t* inputU = input + yBytes;
    const uint8_t* inputV = inputU + chromaBytes;
    for (size_t row = 0; row < height; ++row) {
        std::memcpy(y + row * yStride, input + row * inputStride, width);
    }
    for (size_t row = 0; row < height / 2; ++row) {
        std::memcpy(u + row * chromaStride, inputU + row * inputStride / 2, width / 2);
        std::memcpy(v + row * chromaStride, inputV + row * inputStride / 2, width / 2);
    }
    return true;
}

}  // namespace droidloom::camera
