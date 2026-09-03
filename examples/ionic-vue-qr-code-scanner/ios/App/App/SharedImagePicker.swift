import UIKit
import PhotosUI

/// System photo picker used by the "file" data source on iOS.
/// PHPicker runs out-of-process, so no photo-library permission is required.
enum SharedImagePicker: NSObject, PHPickerViewControllerDelegate {
    private static var completion: ((URL?) -> Void)?

    static func present(from viewController: UIViewController?, completion: @escaping (URL?) -> Void) {
        guard let viewController else {
            completion(nil)
            return
        }
        self.completion = completion
        var configuration = PHPickerConfiguration()
        configuration.filter = .images
        configuration.selectionLimit = 1
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        viewController.present(picker, animated: true)
    }

    static func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let provider = results.first?.itemProvider else {
            completion?(nil)
            return
        }
        if provider.canLoadObject(ofClass: UIImage.self) {
            provider.loadObject(ofClass: UIImage.self) { object, _ in
                guard let image = object as? UIImage else {
                    DispatchQueue.main.async { self.completion?(nil) }
                    return
                }
                // Persist the picked image to a temporary file so the native SDK
                // can process it from disk (file data source).
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("picker-\(UUID().uuidString).jpg")
                let data = image.jpegData(compressionQuality: 0.92)
                do {
                    try data?.write(to: url)
                    DispatchQueue.main.async { self.completion?(url) }
                } catch {
                    DispatchQueue.main.async { self.completion?(nil) }
                }
            }
        } else {
            completion?(nil)
        }
    }
}
