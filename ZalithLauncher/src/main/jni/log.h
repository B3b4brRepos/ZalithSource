#ifndef ZALITH_LOG_H
#define ZALITH_LOG_H
#ifdef __ANDROID__
#include <android/log.h>

#define TAG "jrelog"
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define LOGE(...) __android_log_print(ANDROID_LOG_INFO,    TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_SILENT,    TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_SILENT,    TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_INFO,    TAG, __VA_ARGS__)

// Same priority trick as LOGE/LOGD above: INFO is what the launcher's logcat reader shows (jrelog:I)
#define LOG_TO_E(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOG_TO_W(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOG_TO_I(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOG_TO_D(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#ifdef __cplusplus
}
#endif

#endif // ZALITH_LOG_H
