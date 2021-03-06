LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	bootanimation_main.cpp \
	BootAnimation.cpp

# need "-lrt" on Linux simulator to pick up clock_gettime
ifeq ($(TARGET_SIMULATOR),true)
	ifeq ($(HOST_OS),linux)
		LOCAL_LDLIBS += -lrt
	endif
endif

LOCAL_CFLAGS += -DGL_GLEXT_PROTOTYPES -DEGL_EGLEXT_PROTOTYPES

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \
	libbinder \
    libui \
	libskia \
    libmedia \
    libEGL \
    libGLESv1_CM \
    libsurfaceflinger_client

LOCAL_C_INCLUDES := \
	$(call include-path-for, corecg graphics)

ifeq ($(TARGET_BOOTANIMATION_PRELOAD),true)
    LOCAL_CFLAGS += -DPRELOAD_BOOTANIMATION
endif

ifeq ($(TARGET_BOOTANIMATION_TEXTURE_CACHE),true)
    LOCAL_CFLAGS += -DNO_TEXTURE_CACHE=0
endif
	
ifeq ($(TARGET_BOOTANIMATION_TEXTURE_CACHE),false)
    LOCAL_CFLAGS += -DNO_TEXTURE_CACHE=1
endif

LOCAL_MODULE:= bootanimation


include $(BUILD_EXECUTABLE)
