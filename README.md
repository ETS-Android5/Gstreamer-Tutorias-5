# Introduction
This project is android gstreamer tutorial-5.
There are some issues with this project. I also try to fix some issue.
Finally only one problem in this project that "No URI handler implemented for "https"  gstreamer".
# Platform
* Windows10
* Android Studio
* Samsung J5
# Setting
## Andorid.mk
Add GSTREAMER_ROOT_ANDROID path  
'''
include $(BUILD_SHARED_LIBRARY)
GSTREAMER_ROOT_ANDROID := $(LOCAL_PATH)/../../../../../glib
ifndef GSTREAMER_ROOT_ANDROID
''''
## Application.mk
Setting Application.mk below
'''
APP_ABI :=all
APP_STL = c++_shared
APP_PLATFORM :=android-21
APP_ALLOW_MISSING_DEPS=true
'''
## build.gradle(:app)
setting ndk version below
'''
android {
compileSdk 31
ndkVersion "21.4.7075529"
}
'''

# Build
Go to "jni" foldor
'''
$ ndk-build
'''

Copy all file(app/src/main/libs/*) to jinLibs after "$ ndk-build"

#Run project

Executing the project after completing the above steps.

#Reference
[NDK Example](https://iter01.com/37255.html)
[NDK Example](https://medium.com/@guanwu/android-%E9%96%8B%E7%99%BC%E5%AD%B8%E7%BF%92%E7%AD%86%E8%A8%98-jni-c-c-%E9%96%8B%E7%99%BC%E7%92%B0%E5%A2%83%E8%A8%AD%E5%AE%9A-271b24f2ec7d)
[Gstreamer Example](https://gstreamer.freedesktop.org/documentation/tutorials/index.html?gi-language=c)
[Gstreamer Totorial-5 Github Example](https://github.com/bipbopbee/gstreamer-android-studio-example5/tree/master/app/src/main/java/org/freedesktop/gstreamer)
[How to build GStreamer](https://nickcarter9.github.io/2019/04/03/2019/2019_04_03-build_gstreamer/)
[How to build GStreamer](https://stackoverflow.com/questions/45044210/gstreamer-examples-in-android-studio)
