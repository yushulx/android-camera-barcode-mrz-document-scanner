Pod::Spec.new do |s|
  s.name           = 'ExpoDynamsoftBarcodeScanner'
  s.version        = '1.0.0'
  s.summary        = 'Dynamsoft Capture Vision barcode scanner native bridge for Expo'
  s.description    = 'Live camera and still-image barcode scanning powered by the Dynamsoft Capture Vision native iOS SDK.'
  s.author         = ''
  s.homepage       = 'https://docs.expo.dev/modules/'
  s.platforms      = {
    :ios => '16.4',
    :tvos => '16.4'
  }
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  # Dynamsoft Capture Vision native SDK for iOS. The bundle framework is
  # self-contained (it already embeds the Core and License modules), so do NOT
  # add separate DynamsoftCore/DynamsoftLicense pods - they register duplicate
  # ObjC classes at launch and break the camera preview.
  s.dependency 'DynamsoftCaptureVisionBundle', '3.6.2000'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end