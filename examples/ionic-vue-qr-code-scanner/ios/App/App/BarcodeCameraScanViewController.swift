import UIKit
import DynamsoftCaptureVisionBundle

/// Full-screen native camera scanner for barcodes.
///
/// Live preview and decoding are provided by the Dynamsoft Capture Vision native
/// iOS SDK. Decoded barcodes are highlighted on the preview; tapping Confirm
/// resolves the Capacitor call with the decoded items.
final class BarcodeCameraScanViewController: UIViewController, CapturedResultReceiver {

    var onResult: (([String: Any]) -> Void)?
    var onCancel: (() -> Void)?

    private let cameraView = CameraView()
    private let dce = CameraEnhancer()
    private let cvr = CaptureVisionRouter()

    private var latestItems: [BarcodeResultItem] = []
    private var captureButton: UIButton!
    private var statusLabel: UILabel!
    private var overlayView: BarcodeOverlayView!
    private var confirmed = false

    private let TEMPLATE_NAME = "ReadBarcodes_Default"

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
        setupUi()
        try? cvr.setInput(dce)
        cvr.addResultReceiver(self)
        // Load the built-in barcode templates shipped inside
        // DynamsoftCaptureVisionBundle.framework (the SDK requires templates to
        // be initialized before startCapturing).
        if let templates = Bundle.allFrameworks.lazy
            .compactMap({ $0.path(forResource: "dbr-bundle-mobile-templates", ofType: "json") })
            .first {
            try? cvr.initSettingsFromFile(templates)
        }
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        latestItems = []
        confirmed = false
        captureButton.isEnabled = false
        statusLabel.text = "Scanning…"
        dce.open()
        try? cvr.startCapturing(TEMPLATE_NAME) { [weak self] success, error in
            DispatchQueue.main.async {
                if !success {
                    self?.statusLabel.text = error?.localizedDescription ?? "Failed to start capturing"
                }
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        dce.close()
        cvr.stopCapturing()
    }

    private func setupCamera() {
        cameraView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(cameraView)
        NSLayoutConstraint.activate([
            cameraView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            cameraView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            cameraView.topAnchor.constraint(equalTo: view.topAnchor),
            cameraView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        dce.cameraView = cameraView
    }

    private func setupUi() {
        statusLabel = UILabel()
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.textColor = .white
        statusLabel.font = .systemFont(ofSize: 15)
        statusLabel.textAlignment = .center
        statusLabel.backgroundColor = UIColor.black.withAlphaComponent(0.55)
        statusLabel.layer.cornerRadius = 8
        statusLabel.clipsToBounds = true
        statusLabel.numberOfLines = 0
        view.addSubview(statusLabel)

        overlayView = BarcodeOverlayView()
        overlayView.translatesAutoresizingMaskIntoConstraints = false
        overlayView.isUserInteractionEnabled = false
        view.addSubview(overlayView)
        NSLayoutConstraint.activate([
            overlayView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlayView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlayView.topAnchor.constraint(equalTo: view.topAnchor),
            overlayView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        let cancelButton = UIButton(type: .system)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        cancelButton.setTitle("Cancel", for: .normal)
        cancelButton.addTarget(self, action: #selector(cancelTapped), for: .touchUpInside)

        captureButton = UIButton(type: .system)
        captureButton.translatesAutoresizingMaskIntoConstraints = false
        captureButton.setTitle("Confirm", for: .normal)
        captureButton.isEnabled = false
        captureButton.addTarget(self, action: #selector(confirmTapped), for: .touchUpInside)

        let bar = UIStackView(arrangedSubviews: [cancelButton, captureButton])
        bar.translatesAutoresizingMaskIntoConstraints = false
        bar.axis = .horizontal
        bar.distribution = .fillEqually
        bar.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        view.addSubview(bar)

        NSLayoutConstraint.activate([
            statusLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            bar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            bar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            bar.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
            bar.heightAnchor.constraint(equalToConstant: 56)
        ])
    }

    // MARK: CapturedResultReceiver

    func onDecodedBarcodesReceived(_ result: DecodedBarcodesResult) {
        let items = result.items ?? []
        DispatchQueue.main.async {
            self.latestItems = items
            self.overlayView.items = items
            if items.isEmpty {
                self.statusLabel.text = "Scanning…"
                self.captureButton.isEnabled = false
            } else {
                self.statusLabel.text = items.count == 1 ? "1 barcode found" : "\(items.count) barcodes found"
                self.captureButton.isEnabled = true
            }
        }
    }

    // MARK: Actions

    @objc private func cancelTapped() {
        cvr.stopCapturing()
        onCancel?()
    }

    @objc private func confirmTapped() {
        guard !confirmed, !latestItems.isEmpty else { return }
        confirmed = true
        cvr.stopCapturing()
        var array: [[String: Any]] = []
        for item in latestItems {
            var entry: [String: Any] = [
                "text": item.text ?? "",
                "formatString": item.formatString ?? ""
            ]
            var points: [[String: Any]] = []
            for value in item.location.points {
                let p = value.cgPointValue
                points.append(["x": p.x, "y": p.y])
            }
            entry["points"] = points
            array.append(entry)
        }
        onResult?(["results": array])
    }
}

/// Simple preview overlay that draws the decoded barcode contours.
final class BarcodeOverlayView: UIView {
    var items: [BarcodeResultItem] = [] {
        didSet { setNeedsDisplay() }
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.setLineWidth(2)
        context.setStrokeColor(UIColor.green.cgColor)
        for item in items {
            guard item.location.points.count == 4 else { continue }
            let points = item.location.points.map { $0.cgPointValue }
            context.move(to: points[0])
            for i in 1..<points.count {
                context.addLine(to: points[i])
            }
            context.closePath()
            context.strokePath()
        }
    }
}
