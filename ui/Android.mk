LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res $(LOCAL_PATH)/miui_res
LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_INCLUDES := $(call intermediates-dir-for,APPS,miui,,COMMON)/package-export.apk
LOCAL_JAVA_LIBRARIES := miuisdk
LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 android-support-v13 miuisdk_static

LOCAL_SRC_FILES := $(call all-java-files-under, src)


LOCAL_PACKAGE_NAME := DownloadProviderUi
LOCAL_PRODUCT_AAPT_CONFIG := xhdpi
LOCAL_CERTIFICATE := media
LOCAL_PROGUARD_FLAG_FILES := proguard.flags
LOCAL_STATIC_JAVA_LIBRARIES += miui_account_lib \
                               xl_account_lib \
                               miui_pay_lib


include $(BUILD_PACKAGE)
include $(CLEAR_VARS)
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := miui_account_lib:libs/xiaomiopenauth.jar \
                               xl_account_lib:libs/xunleiopenapi.jar \
                               miui_pay_lib:libs/mibisdk.jar
include $(BUILD_MULTI_PREBUILT)
