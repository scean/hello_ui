LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_AAPT_INCLUDES := $(call intermediates-dir-for,APPS,miui,,COMMON)/package-export.apk
LOCAL_JAVA_LIBRARIES := miuisdk
LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 android-support-v13 miuisdk_static

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res $(LOCAL_PATH)/miui_res
LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_PACKAGE_NAME := DownloadProvider
LOCAL_CERTIFICATE := media
LOCAL_PRIVILEGED_MODULE := true
LOCAL_STATIC_JAVA_LIBRARIES += guava xl_download_lib MiPush_Xunlei_141017

LOCAL_PROGUARD_ENABLED := obfuscate
LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_REQUIRED_MODULES := libxl_thunder_sdk \
                          libxl_thunder_iface \
                          libvip_channel \
                          libxl_stat

include $(BUILD_PACKAGE)

#########################################################
include $(CLEAR_VARS)
LOCAL_MODULE := libxl_thunder_sdk
LOCAL_MODULE_STEM := libxl_thunder_sdk
LOCAL_SRC_FILES := libs/armeabi/libxl_thunder_sdk.so
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
include $(BUILD_PREBUILT)
#########################################################

#########################################################
include $(CLEAR_VARS)
LOCAL_MODULE := libxl_thunder_iface
LOCAL_MODULE_STEM := libxl_thunder_iface
LOCAL_SRC_FILES := libs/armeabi/libxl_thunder_iface.so
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
include $(BUILD_PREBUILT)
#########################################################

#########################################################
include $(CLEAR_VARS)
LOCAL_MODULE := libvip_channel
LOCAL_MODULE_STEM := libvip_channel
LOCAL_SRC_FILES := libs/armeabi/libvip_channel.so
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
include $(BUILD_PREBUILT)
#########################################################

#########################################################
include $(CLEAR_VARS)
LOCAL_MODULE := libxl_stat
LOCAL_MODULE_STEM := libxl_stat
LOCAL_SRC_FILES := libs/armeabi/libxl_stat.so
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
include $(BUILD_PREBUILT)
#########################################################

include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := xl_download_lib:libs/xldownloadplatform.jar \
                                        MiPush_Xunlei_141017:libs/MiPush_Xunlei_141017.jar

include $(BUILD_MULTI_PREBUILT)
# build UI + tests
include $(call all-makefiles-under,$(LOCAL_PATH))
