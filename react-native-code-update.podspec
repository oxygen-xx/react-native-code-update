
Pod::Spec.new do |spec|
  spec.name         = "react-native-code-update"
  spec.version      = "0.0.1"
  spec.summary      = "react native code update with aliyun, and I just create podspec file"
  spec.license      = 'MIT'
  spec.homepage     = "https://github.com/oxygen-xx/react-native-code-update"
  spec.authors      = { 'Alive' => 'appreach@icloud.com' }
  spec.source       = { :git => "https://github.com/oxygen-xx/react-native-code-update.git" }
  spec.source_files = ['ios/*.m', 'ios/*.h', 'ios/SSZipArchive/*.m', 'ios/SSZipArchive/*.h', 'ios/SSZipArchive/aes/*', 'ios/SSZipArchive/minizip/*']
  spec.platform     = :ios, "7.0"
  spec.dependency 'React'
end
