import UIKit
import Photos

/// System photo picker used by the "file" data source on iOS.
/// Uses UIImagePickerController (instead of PHPickerViewController) which is
/// more tolerant when the photo library is unavailable.
final class SharedImagePicker: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

    static let shared = SharedImagePicker()
    private var completion: ((URL?) -> Void)?

    static func present(from viewController: UIViewController?, completion: @escaping (URL?) -> Void) {
        guard let viewController else {
            completion(nil)
            return
        }
        let handleStatus: (PHAuthorizationStatus) -> Void = { status in
            DispatchQueue.main.async {
                guard status == .authorized || status == .limited else {
                    completion(nil)
                    return
                }
                SharedImagePicker.shared.completion = completion
                let picker = UIImagePickerController()
                picker.sourceType = .photoLibrary
                picker.delegate = SharedImagePicker.shared
                viewController.present(picker, animated: true)
            }
        }
        if #available(iOS 14, *) {
            PHPhotoLibrary.requestAuthorization(for: .readWrite) { status in
                handleStatus(status)
            }
        } else {
            handleStatus(PHPhotoLibrary.authorizationStatus())
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true) { [weak self] in
            self?.completion?(nil)
        }
    }

    func imagePickerController(_ picker: UIImagePickerController,
                               didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        picker.dismiss(animated: true)
        guard let image = info[.originalImage] as? UIImage else {
            completion?(nil)
            return
        }
        // Persist the picked image to a temporary file so the native SDK
        // can process it from disk (file data source).
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("picker-\(UUID().uuidString).jpg")
        let data = image.jpegData(compressionQuality: 0.92)
        do {
            try data?.write(to: url)
            completion?(url)
        } catch {
            completion?(nil)
        }
    }
}
