import UIKit
import DynamsoftCaptureVisionBundle

/// Full-screen native camera scanner for identity documents.
///
/// Live preview and processing are provided by the Dynamsoft MRZ Scanner native
/// iOS SDK (AVFoundation + Capture Vision). When the MRZ zone and the portrait are
/// detected the capture button becomes enabled; tapping it resolves the Capacitor
/// call with the parsed fields and the cropped portrait photo.
final class IdCameraScanViewController: UIViewController, CapturedResultReceiver, IntermediateResultReceiver {

    var templateName = "ReadPassportAndId"
    var onResult: (([String: Any]) -> Void)?
    var onCancel: (() -> Void)?

    private let cameraView = CameraView()
    private let dce = CameraEnhancer()
    private let cvr = CaptureVisionRouter()
    private let idProcessor = IdentityProcessor()

    private var scaledColourImageUnit: ScaledColourImageUnit?
    private var localizedTextLinesUnit: LocalizedTextLinesUnit?
    private var recognizedTextLinesUnit: RecognizedTextLinesUnit?
    private var detectedQuadsUnit: DetectedQuadsUnit?
    private var deskewedImageUnit: DeskewedImageUnit?

    private var pendingFields: [String: String]?
    private var pendingPortrait: UIImage?
    private var pendingDocument: UIImage?
    private var captureButton: UIButton!
    private var statusLabel: UILabel!
    private var confirmed = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
        setupUi()
        setupRouter()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        reset()
        dce.open()
        try? cvr.startCapturing(templateName) { [weak self] success, error in
            DispatchQueue.main.async {
                if success {
                    self?.statusLabel.text = "Scanning… point the camera at the MRZ zone"
                } else {
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

    // MARK: Setup

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
        dce.enableEnhancedFeatures(.frameFilter)
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

    private func setupRouter() {
        // The MRZ template ships inside the DynamsoftMRZScannerBundle.framework.
        let bundleCandidates = Bundle.allFrameworks + Bundle.allBundles
        if let templatePath = bundleCandidates.lazy
            .compactMap({ $0.path(forResource: "mrz-mobile", ofType: "json") })
            .first {
            try? cvr.initSettingsFromFile(templatePath)
        }
        try? cvr.setInput(dce)
        cvr.getIntermediateResultManager().addResultReceiver(self)
        cvr.addResultReceiver(self)
    }

    // MARK: Actions

    private func reset() {
        pendingFields = nil
        pendingPortrait = nil
        pendingDocument = nil
        confirmed = false
        captureButton.isEnabled = false
        statusLabel.text = "Initializing…"
    }

    @objc private func cancelTapped() {
        cvr.stopCapturing()
        onCancel?()
    }

    @objc private func confirmTapped() {
        guard !confirmed, let fields = pendingFields else { return }
        confirmed = true
        cvr.stopCapturing()
        var payload: [String: Any] = ["fields": fields]
        if let portrait = pendingPortrait {
            payload["portraitBase64"] = Self.jpegDataUrl(portrait)
        }
        if let document = pendingDocument {
            payload["documentImageBase64"] = Self.jpegDataUrl(document)
        }
        onResult?(payload)
    }

    // MARK: CapturedResultReceiver

    func onCapturedResultReceived(_ result: CapturedResult) {
        guard let parsed = result.parsedResult,
              let item = parsed.items?.first else { return }
        let fields = IdResultFormatter.fieldMap(for: item)
        guard !fields.isEmpty else { return }

        // Try to locate the portrait zone reported in the MRZ auxiliary region.
        var portraitZone: Quadrilateral?
        if let localized = localizedTextLinesUnit,
           let quads = detectedQuadsUnit,
           quads.getCount() > 0,
           let scaled = scaledColourImageUnit,
           let recog = recognizedTextLinesUnit,
           let deskewed = deskewedImageUnit {
            portraitZone = idProcessor.findPortraitZone(
                scaled,
                localizedTextLinesUnit: localized,
                recognizedTextLinesUnit: recog,
                detectedQuadsUnit: quads,
                deskewedImageUnit: deskewed)
        }

        var portrait: UIImage?
        if let zone = portraitZone,
           let scaledData = scaledColourImageUnit?.getImageData(),
           let scaled = IdResultFormatter.image(from: scaledData) {
            portrait = IdResultFormatter.deskewPortrait(scaled, quad: zone)
        }

        // The same document-detection step also deskews the card; surface it so
        // the result page can show the perspective-corrected document image.
        var document: UIImage?
        if let item = result.processedDocumentResult?.deskewedImageResultItems?.first,
           let data = item.imageData {
            document = IdResultFormatter.image(from: data)
        }

        DispatchQueue.main.async {
            self.pendingFields = fields
            self.pendingPortrait = portrait
            self.pendingDocument = document
            self.captureButton.isEnabled = true
            self.statusLabel.text = portrait != nil ? "Portrait found - ready" : "MRZ ready"
        }
    }

    // MARK: IntermediateResultReceiver

    func onDetectedQuadsReceived(_ unit: DetectedQuadsUnit) {
        detectedQuadsUnit = unit
    }

    func onLocalizedTextLinesReceived(_ unit: LocalizedTextLinesUnit) {
        localizedTextLinesUnit = unit
    }

    func onRecognizedTextLinesReceived(_ unit: RecognizedTextLinesUnit) {
        recognizedTextLinesUnit = unit
    }

    func onDeskewedImageReceived(_ unit: DeskewedImageUnit) {
        deskewedImageUnit = unit
    }

    func onScaledColourImageUnitReceived(_ unit: ScaledColourImageUnit) {
        scaledColourImageUnit = unit
    }

    // MARK: Helpers

    private static func jpegDataUrl(_ image: UIImage) -> String {
        let data = image.jpegData(compressionQuality: 0.9) ?? Data()
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }
}
