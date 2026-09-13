// SPDX-License-Identifier: GPL-3.0-or-later
#include <droidloom_camera_yuv.h>

#include <algorithm>
#include <array>
#include <cassert>
#include <limits>

int main() {
    // 4x4 YU12 with padded input rows (8 Y / 4 U,V) and different output strides.
    std::array<uint8_t, 48> input;
    input.fill(0xee);
    for (size_t row = 0; row < 4; ++row) {
        for (size_t col = 0; col < 4; ++col) input[row * 8 + col] = row * 4 + col;
    }
    for (size_t row = 0; row < 2; ++row) {
        for (size_t col = 0; col < 2; ++col) {
            input[32 + row * 4 + col] = 0x40 + row * 2 + col;
            input[40 + row * 4 + col] = 0x80 + row * 2 + col;
        }
    }
    std::array<uint8_t, 24> y;
    std::array<uint8_t, 8> u, v;
    auto clear = [&] { y.fill(0xcc); u.fill(0xcc); v.fill(0xcc); };
    auto untouched = [&] {
        auto unchanged = [](auto& plane) {
            return std::all_of(plane.begin(), plane.end(), [](uint8_t b) { return b == 0xcc; });
        };
        return unchanged(y) && unchanged(u) && unchanged(v);
    };
    auto copy = [&](size_t size, size_t width = 4, size_t height = 4, size_t stride = 8) {
        return droidloom::camera::copyYuv420(input.data(), size, width, height, stride,
                                            y.data(), 6, u.data(), v.data(), 4);
    };
    clear();
    assert(copy(input.size()));
    for (size_t row = 0; row < 4; ++row) {
        for (size_t col = 0; col < 6; ++col) {
            assert(y[row * 6 + col] == (col < 4 ? row * 4 + col : 0xcc));
        }
    }
    for (size_t row = 0; row < 2; ++row) {
        for (size_t col = 0; col < 4; ++col) {
            assert(u[row * 4 + col] == (col < 2 ? 0x40 + row * 2 + col : 0xcc));
            assert(v[row * 4 + col] == (col < 2 ? 0x80 + row * 2 + col : 0xcc));
        }
    }
    // A short final V plane must reject the whole frame without partial output.
    for (size_t size = 0; size < input.size(); ++size) {
        clear();
        assert(!copy(size));
        assert(untouched());
    }
    for (auto shape : {std::array<size_t, 3>{0, 4, 8}, {4, 0, 8}, {3, 4, 8},
                       {4, 3, 8}, {4, 4, 2}, {4, 4, 7}, {4, 4, 0}}) {
        clear();
        assert(!copy(input.size(), shape[0], shape[1], shape[2]));
        assert(untouched());
    }
    clear();
    const size_t huge = std::numeric_limits<size_t>::max() - 1;
    assert(!copy(huge, 4, huge, 8));
    assert(!droidloom::camera::copyYuv420(input.data(), input.size(), 4, 4, 8,
                                         y.data(), huge, u.data(), v.data(), 4));
    assert(!droidloom::camera::copyYuv420(nullptr, input.size(), 4, 4, 8,
                                         y.data(), 6, u.data(), v.data(), 4));
    assert(untouched());
    // The tightly packed layout produced by scrcpy also preserves U/V order.
    const std::array<uint8_t, 6> packed{1, 2, 3, 4, 0x55, 0xaa};
    assert(droidloom::camera::copyYuv420(packed.data(), packed.size(), 2, 2, 2,
                                        y.data(), 2, u.data(), v.data(), 1));
    assert(y[0] == 1 && y[1] == 2 && y[2] == 3 && y[3] == 4);
    assert(u[0] == 0x55 && v[0] == 0xaa);
}
