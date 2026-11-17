/*
 * Copyright (C) 2026 Rizki Fauzi
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Software H.264 encoder loop on x264 (NEON). FFmpeg decodes/scales the source and writes raw
 * I420 frames into a named pipe; this code reads whole frames, encodes them and writes an
 * Annex-B H.264 stream that FFmpeg then muxes with the original audio. See MorphNative.kt.
 */
#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <poll.h>
#include <csignal>
#include <unistd.h>
#include <vector>

#include "x264.h"

#define TAG "MorphNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

// Negative results returned to Kotlin (MorphNative.kt mirrors these). >= 0 = encoded frames.
constexpr jlong ERR_ARGS = -1;
constexpr jlong ERR_OPEN_PIPE = -2;
constexpr jlong ERR_OPEN_OUTPUT = -3;
constexpr jlong ERR_ENCODER = -4;
constexpr jlong ERR_ENCODE = -5;
constexpr jlong ERR_WRITE = -6;
constexpr jlong ERR_CANCELLED = -7;
constexpr jlong ERR_READ = -8;

// One conversion runs at a time (MorphService is sequential), so one flag is enough.
std::atomic<bool> g_cancel{false};

struct Session {
    x264_t *encoder = nullptr;
    FILE *out = nullptr;
    int pipeFd = -1;
    int width = 0;
    int height = 0;
    std::vector<uint8_t> frame;

    // Closing the read end gives a writer blocked in write() an EPIPE, so FFmpeg can't hang
    void closePipe() {
        if (pipeFd >= 0) {
            close(pipeFd);
            pipeFd = -1;
        }
    }

    ~Session() {
        closePipe();
        if (out) fclose(out);
        if (encoder) x264_encoder_close(encoder);
    }
};

class Utf {
public:
    Utf(JNIEnv *env, jstring s) : env_(env), s_(s), c_(s ? env->GetStringUTFChars(s, nullptr) : nullptr) {}
    ~Utf() { if (c_) env_->ReleaseStringUTFChars(s_, c_); }
    const char *get() const { return c_; }

private:
    JNIEnv *env_;
    jstring s_;
    const char *c_;
};

/*
 * Reads one frame. Polls (200 ms) instead of blocking so a cancel, or a writer that never shows
 * up because FFmpeg failed before opening the pipe, can't hang the thread.
 * Returns the bytes read (< size only at end of stream) or -1 on error/cancel.
 */
ssize_t readFrame(int fd, uint8_t *dst, size_t size) {
    size_t got = 0;
    while (got < size) {
        if (g_cancel.load()) return -1;
        pollfd p{fd, POLLIN, 0};
        int ready = poll(&p, 1, 200);
        if (ready < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (ready == 0) continue;
        ssize_t n = read(fd, dst + got, size - got);
        if (n > 0) {
            got += static_cast<size_t>(n);
        } else if (n == 0) {
            break; // the writer closed the pipe: end of stream
        } else if (errno != EAGAIN && errno != EINTR) {
            return -1;
        }
    }
    return static_cast<ssize_t>(got);
}

bool writeNals(Session *s, x264_nal_t *nals, int bytes) {
    // x264 lays the NALs of one encode call out contiguously
    return bytes <= 0 || fwrite(nals[0].p_payload, 1, static_cast<size_t>(bytes), s->out) == static_cast<size_t>(bytes);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_morph_app_MorphNative_nativeX264Open(JNIEnv *env, jclass, jstring pipePath, jstring outPath,
                                              jint width, jint height, jint fpsNum, jint fpsDen,
                                              jstring preset, jint bitrateKbps, jint keyint) {
    if (width <= 0 || height <= 0 || (width & 1) || (height & 1) || fpsNum <= 0 || fpsDen <= 0 ||
        bitrateKbps <= 0 || keyint <= 0) {
        LOGE("open: bad args %dx%d fps %d/%d %d kbps keyint %d", width, height, fpsNum, fpsDen, bitrateKbps, keyint);
        return ERR_ARGS;
    }
    Utf pipe(env, pipePath), out(env, outPath), presetName(env, preset);
    if (!pipe.get() || !out.get() || !presetName.get()) {
        LOGE("open: null path or preset string");
        return ERR_ARGS;
    }
    g_cancel.store(false);
    // We close the read end early on cancel/error. Without SIG_IGN the writer's next write raises
    // SIGPIPE, whose default action kills the whole app; ignored, FFmpeg just gets EPIPE and exits.
    signal(SIGPIPE, SIG_IGN);

    auto *s = new Session();
    s->width = width;
    s->height = height;

    // Open the read end first and without blocking: FFmpeg's open() for writing then never waits
    s->pipeFd = open(pipe.get(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
    if (s->pipeFd < 0) {
        LOGE("open: pipe %s: %s", pipe.get(), strerror(errno));
        delete s;
        return ERR_OPEN_PIPE;
    }

    x264_param_t p;
    if (x264_param_default_preset(&p, presetName.get(), nullptr) < 0) {
        LOGE("open: unknown x264 preset '%s'", presetName.get());
        delete s;
        return ERR_ARGS;
    }
    p.i_csp = X264_CSP_I420;
    p.i_width = width;
    p.i_height = height;
    p.i_fps_num = static_cast<uint32_t>(fpsNum);
    p.i_fps_den = static_cast<uint32_t>(fpsDen);
    p.i_timebase_num = static_cast<uint32_t>(fpsDen);
    p.i_timebase_den = static_cast<uint32_t>(fpsNum);
    p.b_vfr_input = 0;
    p.b_annexb = 1;
    p.b_repeat_headers = 1;
    // The Annex-B stream carries no timestamps; FFmpeg re-times it at a constant rate. Without
    // B-frames decode order == display order, so those timestamps are exact.
    p.i_bframe = 0;
    p.i_keyint_max = keyint;
    p.rc.i_rc_method = X264_RC_ABR;
    p.rc.i_bitrate = bitrateKbps;
    // BT.709 limited range, matching the scale filter that feeds us and Morph's other encoders
    p.vui.i_colorprim = 1;
    p.vui.i_transfer = 1;
    p.vui.i_colmatrix = 1;
    p.vui.b_fullrange = 0;
    p.i_log_level = X264_LOG_WARNING;
    if (x264_param_apply_profile(&p, "high") < 0) {
        LOGE("open: x264_param_apply_profile(high) rejected the parameters");
        delete s;
        return ERR_ARGS;
    }

    s->encoder = x264_encoder_open(&p);
    if (!s->encoder) {
        // x264 prints the exact reason through its own logger (X264_LOG_WARNING and up)
        LOGE("open: x264_encoder_open failed for %dx%d @ %d/%d fps, %d kbps", width, height, fpsNum, fpsDen, bitrateKbps);
        delete s;
        return ERR_ENCODER;
    }
    s->out = fopen(out.get(), "wb");
    if (!s->out) {
        LOGE("open: output %s: %s", out.get(), strerror(errno));
        delete s;
        return ERR_OPEN_OUTPUT;
    }
    s->frame.resize(static_cast<size_t>(width) * height * 3 / 2);
    // p.i_threads is still 0 ("auto"); ask the encoder what it actually chose
    x264_param_t used;
    x264_encoder_parameters(s->encoder, &used);
    LOGI("x264 %s %dx%d @ %d/%d fps, %d kbps, %d threads", presetName.get(), width, height, fpsNum, fpsDen,
         bitrateKbps, used.i_threads);
    // Opaque handle. On Android 11+ arm64 the heap tags the top byte, so this is often NEGATIVE:
    // callers must only compare against the error codes above (MorphNative.isOpenError)
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_morph_app_MorphNative_nativeX264Encode(JNIEnv *, jclass, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (!s) {
        LOGE("encode: null handle");
        return ERR_ARGS;
    }

    const size_t lumaSize = static_cast<size_t>(s->width) * s->height;
    const size_t frameSize = s->frame.size();
    uint8_t *buf = s->frame.data();

    x264_picture_t pic;
    x264_picture_init(&pic);
    pic.img.i_csp = X264_CSP_I420;
    pic.img.i_plane = 3;
    pic.img.plane[0] = buf;
    pic.img.plane[1] = buf + lumaSize;
    pic.img.plane[2] = buf + lumaSize + lumaSize / 4;
    pic.img.i_stride[0] = s->width;
    pic.img.i_stride[1] = s->width / 2;
    pic.img.i_stride[2] = s->width / 2;

    x264_picture_t encoded;
    x264_nal_t *nals = nullptr;
    int nalCount = 0;
    jlong frames = 0;

    for (;;) {
        ssize_t got = readFrame(s->pipeFd, buf, frameSize);
        if (got < 0) {
            const bool cancelled = g_cancel.load();
            if (!cancelled) LOGE("encode: reading the pipe failed after %lld frames: %s", static_cast<long long>(frames), strerror(errno));
            s->closePipe();
            return cancelled ? ERR_CANCELLED : ERR_READ;
        }
        if (got == 0) break;
        if (static_cast<size_t>(got) < frameSize) {
            LOGW("dropping truncated last frame (%zd of %zu bytes)", got, frameSize);
            break;
        }
        pic.i_pts = frames;
        int bytes = x264_encoder_encode(s->encoder, &nals, &nalCount, &pic, &encoded);
        if (bytes < 0) {
            LOGE("encode: x264_encoder_encode failed at frame %lld", static_cast<long long>(frames));
            s->closePipe();
            return ERR_ENCODE;
        }
        if (!writeNals(s, nals, bytes)) {
            LOGE("encode: writing the H.264 file failed at frame %lld: %s", static_cast<long long>(frames), strerror(errno));
            s->closePipe();
            return ERR_WRITE;
        }
        frames++;
    }

    // Lookahead / frame threads still hold frames: flush them
    while (x264_encoder_delayed_frames(s->encoder) > 0) {
        int bytes = x264_encoder_encode(s->encoder, &nals, &nalCount, nullptr, &encoded);
        if (bytes < 0) {
            LOGE("encode: flushing delayed frames failed");
            return ERR_ENCODE;
        }
        if (!writeNals(s, nals, bytes)) {
            LOGE("encode: writing delayed frames failed: %s", strerror(errno));
            return ERR_WRITE;
        }
    }
    if (fflush(s->out) != 0) {
        LOGE("encode: flushing the H.264 file failed: %s", strerror(errno));
        return ERR_WRITE;
    }
    LOGI("x264 encoded %lld frames", static_cast<long long>(frames));
    return frames;
}

extern "C" JNIEXPORT void JNICALL
Java_com_morph_app_MorphNative_nativeX264Close(JNIEnv *, jclass, jlong handle) {
    delete reinterpret_cast<Session *>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_morph_app_MorphNative_nativeCancel(JNIEnv *, jclass) {
    g_cancel.store(true);
}
