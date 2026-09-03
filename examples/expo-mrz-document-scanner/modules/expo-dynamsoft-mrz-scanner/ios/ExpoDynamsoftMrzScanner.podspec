Pod::Spec.new do |s|
  s.name           = 'ExpoDynamsoftMrzScanner'
  s.version        = '1.0.0'
  s.summary        = 'Dynamsoft MRZ Scanner native bridge for Expo'
  s.description    = 'Passport/ID MRZ parsing with portrait and document capture powered by the Dynamsoft MRZ Scanner native iOS SDK.'
  s.author         = ''
  s.homepage       = 'https://docs.expo.dev/modules/'
  s.platforms      = {
    :ios => '16.4',
    :tvos => '16.4'
  }
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  # Dynamsoft MRZ Scanner native SDK for iOS. The bundle framework is
  # self-contained (it already embeds the Capture Vision, core and license
  # modules), so do NOT add separate DynamsoftCaptureVisionBundle /
  # DynamsoftCore / DynamsoftLicense pods - they register duplicate ObjC
  # classes at launch and break the camera preview.
  s.dependency 'DynamsoftMRZScannerBundle', '3.4.1300'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
