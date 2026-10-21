#include <jni.h>
#include <stdint.h>
#include <dlfcn.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

/*
 * Timestamp buffer berikutnya yang dikirim ke encoder (input Surface).
 * Simbolnya tidak ada di stub NDK publik, jadi dicari saat runtime lewat dlsym.
 * Kalau tidak ditemukan di perangkat, semua fungsi di sini mengembalikan false
 * dan Exporter memakai jalur real-time biasa.
 */
typedef int32_t (*set_ts_fn)(ANativeWindow *, int64_t);

static set_ts_fn g_set_ts = NULL;
static int g_probed = 0;

static set_ts_fn resolve_set_ts(void) {
    if (!g_probed) {
        g_probed = 1;
        void *h = dlopen("libnativewindow.so", RTLD_NOW);
        if (h != NULL) {
            g_set_ts = (set_ts_fn) dlsym(h, "ANativeWindow_setBuffersTimestamp");
        }
    }
    return g_set_ts;
}

JNIEXPORT jboolean JNICALL
Java_com_kilat_motion_core_export_NativeTiming_nativeProbe(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return resolve_set_ts() != NULL ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kilat_motion_core_export_NativeTiming_nativeSetPresentationNs(
        JNIEnv *env, jobject thiz, jobject surface, jlong ns) {
    (void) thiz;
    set_ts_fn fn = resolve_set_ts();
    if (fn == NULL) return JNI_FALSE;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == NULL) return JNI_FALSE;
    int32_t r = fn(window, (int64_t) ns);
    ANativeWindow_release(window);
    return r == 0 ? JNI_TRUE : JNI_FALSE;
}
