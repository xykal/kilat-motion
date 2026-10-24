// Jembatan JNI untuk SoundTouch: ubah pitch audio tanpa mengubah durasi.
// Sampel 16-bit (interleaved) masuk dan keluar; SoundTouch bekerja di float.
#include <jni.h>
#include <algorithm>
#include <vector>
#include "SoundTouch.h"

namespace {
struct VoiceCtx {
    soundtouch::SoundTouch st;
    int channels = 1;
    std::vector<float> scratch;
};

inline short toShort(float v) {
    float s = v * 32768.0f;
    if (s > 32767.0f) s = 32767.0f;
    if (s < -32768.0f) s = -32768.0f;
    return static_cast<short>(s);
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_fluxa_motion_core_audio_VoiceNative_create(JNIEnv*, jclass, jint sampleRate, jint channels) {
    auto* c = new VoiceCtx();
    c->channels = channels > 0 ? channels : 1;
    c->st.setSampleRate(static_cast<unsigned int>(sampleRate));
    c->st.setChannels(static_cast<unsigned int>(c->channels));
    c->st.setRate(1.0);
    c->st.setTempo(1.0);
    c->st.setPitchSemiTones(0.0);
    return reinterpret_cast<jlong>(c);
}

JNIEXPORT void JNICALL
Java_com_fluxa_motion_core_audio_VoiceNative_setPitch(JNIEnv*, jclass, jlong h, jfloat semitones) {
    auto* c = reinterpret_cast<VoiceCtx*>(h);
    if (c) c->st.setPitchSemiTones(static_cast<double>(semitones));
}

JNIEXPORT void JNICALL
Java_com_fluxa_motion_core_audio_VoiceNative_put(JNIEnv* env, jclass, jlong h, jshortArray samples, jint frames) {
    auto* c = reinterpret_cast<VoiceCtx*>(h);
    if (!c || !samples) return;
    const jsize total = env->GetArrayLength(samples);
    int n = frames;
    if (static_cast<jsize>(n) * c->channels > total) n = total / c->channels;
    if (n <= 0) return;
    const size_t count = static_cast<size_t>(n) * c->channels;
    c->scratch.resize(count);
    jshort* p = env->GetShortArrayElements(samples, nullptr);
    for (size_t i = 0; i < count; ++i) c->scratch[i] = p[i] / 32768.0f;
    env->ReleaseShortArrayElements(samples, p, JNI_ABORT);
    c->st.putSamples(c->scratch.data(), static_cast<unsigned int>(n));
}

JNIEXPORT jint JNICALL
Java_com_fluxa_motion_core_audio_VoiceNative_receive(JNIEnv* env, jclass, jlong h, jshortArray out, jint maxFrames) {
    auto* c = reinterpret_cast<VoiceCtx*>(h);
    if (!c || !out) return 0;
    const jsize total = env->GetArrayLength(out);
    int cap = total / c->channels;
    int want = std::min<int>(maxFrames, cap);
    if (want <= 0) return 0;
    c->scratch.resize(static_cast<size_t>(want) * c->channels);
    const unsigned int got = c->st.receiveSamples(c->scratch.data(), static_cast<unsigned int>(want));
    const size_t count = static_cast<size_t>(got) * c->channels;
    jshort* p = env->GetShortArrayElements(out, nullptr);
    for (size_t i = 0; i < count; ++i) p[i] = toShort(c->scratch[i]);
    env->ReleaseShortArrayElements(out, p, 0);
    return static_cast<jint>(got);
}

JNIEXPORT void JNICALL
Java_com_fluxa_motion_core_audio_VoiceNative_flush(JNIEnv*, jclass, jlong h) {
    auto* c = reinterpret_cast<VoiceCtx*>(h);
    if (c) c->st.flush();
}

JNIEXPORT void JNICALL
Java_com_fluxa_motion_core_audio_VoiceNative_destroy(JNIEnv*, jclass, jlong h) {
    delete reinterpret_cast<VoiceCtx*>(h);
}

}  // extern "C"
