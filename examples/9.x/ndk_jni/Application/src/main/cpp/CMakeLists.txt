cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_VERBOSE_MAKEFILE on)
set(CMAKE_ANDROID_ARCH_ABI armeabi-v7a)

link_directories("${CMAKE_CURRENT_SOURCE_DIR}")

add_library(dynamsoft_barcode SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/android_main.cpp)

# add include path
target_include_directories(dynamsoft_barcode PRIVATE ${COMMON_SOURCE_DIR})

# add lib dependencies
target_link_libraries(dynamsoft_barcode dl android log m DynamsoftBarcodeReaderAndroid)
