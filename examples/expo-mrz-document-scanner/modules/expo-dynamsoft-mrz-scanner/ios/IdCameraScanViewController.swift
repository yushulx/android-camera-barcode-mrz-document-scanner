import UIKit
import DynamsoftCaptureVisionBundle

/// Full-screen native camera scanner for identity documents.
///
/// Live preview and processing are provided by the Dynamsoft MRZ Scanner native
/// iOS SDK (AVFoundation + Capture Vision). When the MRZ zone and the portrait are
/// detected the capture button becomes enabled; tapping it resolves the module
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
    private var portraitLayerId: UInt = DrawingLayerId.userDefinedBase.rawValue

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
        clearAllOverlays()
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
        configureDrawingLayers()
    }

    /// Overlay layers: preset DDN layer for the document quad, preset DLR layer
    /// for the MRZ text lines, and a custom cyan layer for the portrait zone.
    private func configureDrawingLayers() {
        cameraView.getDrawingLayer(DrawingLayerId.DDN.rawValue)?.visible = true
        cameraView.getDrawingLayer(DrawingLayerId.DLR.rawValue)?.visible = true

        let portraitStyle = DrawingStyleManager.createDrawingStyle(
            .cyan, strokeWidth: 3,
            fill: UIColor.cyan.withAlphaComponent(0.1),
            textColor: .white, font: .systemFont(ofSize: 12))
        let portraitLayer = cameraView.createDrawingLayer()
        portraitLayer.visible = true
        portraitLayer.setDefaultStyle(portraitStyle)
        portraitLayerId = portraitLayer.layerId
    }

    /// Draw the document quad and the portrait zone; clear whichever is absent.
    private func drawOverlays(result: CapturedResult, portraitZone: Quadrilateral?) {
        let ddnLayer = cameraView.getDrawingLayer(DrawingLayerId.DDN.rawValue)
        if let quad = result.processedDocumentResult?.detectedQuadResultItems?.first?.location {
            ddnLayer?.clearDrawingItems()
            ddnLayer?.addDrawingItems([QuadDrawingItem(quadrilateral: quad)])
        } else {
            ddnLayer?.clearDrawingItems()
        }

        let portraitLayer = cameraView.getDrawingLayer(portraitLayerId)
        portraitLayer?.clearDrawingItems()
        if let zone = portraitZone {
            portraitLayer?.addDrawingItems([QuadDrawingItem(quadrilateral: zone)])
        }
    }

    private func clearAllOverlays() {
        cameraView.getDrawingLayer(DrawingLayerId.DDN.rawValue)?.clearDrawingItems()
        cameraView.getDrawingLayer(DrawingLayerId.DLR.rawValue)?.clearDrawingItems()
        cameraView.getDrawingLayer(portraitLayerId)?.clearDrawingItems()
    }

    // MARK: Actions

    private func reset() {
        pendingFields = nil
        pendingPortrait = nil
        pendingDocument = nil
        confirmed = false
        captureButton.isEnabled = false
        statusLabel.text = "Initializing…"
        clearAllOverlays()
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

        let documentQuad = result.processedDocumentResult?.detectedQuadResultItems?.first?.location

        // Locate the portrait photo from the auxiliary region of the MRZ zone
        // (mirrors the Android scanner).
        var portraitZone = findPortraitZone()

        // Keep the zone only when it sits inside the detected document.
        if let zone = portraitZone, let docRegion = documentQuad {
            let allInside = zone.points.allSatisfy { docRegion.contains($0.cgPointValue) }
            let areaRatioOk = zone.area > 0 ? docRegion.area / zone.area >= 3 : false
            if !allInside || !areaRatioOk {
                portraitZone = nil
            }
        }

        // Perspective-correct crop of the portrait from the original video frame
        // via the SDK's own ImageProcessor (mirrors the reference iOS scanner).
        var portrait: UIImage?
        if let zone = portraitZone,
           let original = cvr.getIntermediateResultManager().getOriginalImage(result.originalImageHashId) {
            if let cropped = try? ImageProcessor().cropAndDeskewImage(original, quad: zone) {
                portrait = IdResultFormatter.image(from: cropped)
            }
        }

        // The same document-detection step also deskews the card; surface it so
        // the result page can show the perspective-corrected document image.
        var document: UIImage?
        if let item = result.processedDocumentResult?.deskewedImageResultItems?.first,
           let data = item.imageData {
            document = IdResultFormatter.image(from: data)
        }

        // Fallback portrait: if the SDK did not report a portrait zone (some
        // cameras/templates skip the auxiliary region), crop the upper part of
        // the deskewed document where the photo normally sits.
        if portrait == nil, let document {
            portrait = IdResultFormatter.cropTopPortrait(from: document)
        }

        DispatchQueue.main.async {
            self.pendingFields = fields
            self.pendingPortrait = portrait
            self.pendingDocument = document
            self.captureButton.isEnabled = true
            self.statusLabel.text = portrait != nil ? "Portrait found - ready" : "MRZ ready"
            self.drawOverlays(result: result, portraitZone: portraitZone)
        }
    }

    /// Draw the live MRZ text-line quads on the preset DLR layer (result level).
    func onRecognizedTextLinesReceived(_ result: RecognizedTextLinesResult) {
        DispatchQueue.main.async {
            let layer = self.cameraView.getDrawingLayer(DrawingLayerId.DLR.rawValue)
            layer?.clearDrawingItems()
            guard let items = result.items, !items.isEmpty else { return }
            layer?.addDrawingItems(items.map { QuadDrawingItem(quadrilateral: $0.location) })
        }
    }

    /// Run IdentityProcessor.findPortraitZone on the cached intermediate units,
    /// but only after the MRZ pipeline has reported a high-confidence
    /// "PortraitZone" auxiliary region (mirrors the Android scanner).
    private func findPortraitZone() -> Quadrilateral? {
        guard let scaled = scaledColourImageUnit,
              let localized = localizedTextLinesUnit,
              let recognized = recognizedTextLinesUnit,
              let quads = detectedQuadsUnit, quads.getCount() > 0,
              let deskewed = deskewedImageUnit,
              let elements = localized.getAuxiliaryRegionElements() else {
            return nil
        }

        var highConfidence = false
        for element in elements {
            if element.getName() == "PortraitZone" && element.getConfidence() > 60 {
                highConfidence = true
                break
            }
        }
        guard highConfidence else { return nil }

        return idProcessor.findPortraitZone(
            scaled,
            localizedTextLinesUnit: localized,
            recognizedTextLinesUnit: recognized,
            detectedQuadsUnit: quads,
            deskewedImageUnit: deskewed)
    }

    // MARK: IntermediateResultReceiver

    // NOTE: every callback of the IntermediateResultReceiver protocol carries an
    // `info:` parameter. Implementing them without it compiles (the methods are
    // @optional) but the SDK never invokes them, which silently starves the
    // portrait detection of its input units.

    func onScaledColourImageUnitReceived(_ unit: ScaledColourImageUnit, info: IntermediateResultExtraInfo) {
        scaledColourImageUnit = unit
    }

    func onLocalizedTextLinesReceived(_ unit: LocalizedTextLinesUnit, info: IntermediateResultExtraInfo) {
        localizedTextLinesUnit = unit
    }

    func onRecognizedTextLinesReceived(_ unit: RecognizedTextLinesUnit, info: IntermediateResultExtraInfo) {
        recognizedTextLinesUnit = unit
    }

    func onDetectedQuadsReceived(_ unit: DetectedQuadsUnit, info: IntermediateResultExtraInfo) {
        detectedQuadsUnit = unit
        if unit.getCount() == 0 {
            DispatchQueue.main.async {
                self.cameraView.getDrawingLayer(DrawingLayerId.DDN.rawValue)?.clearDrawingItems()
                self.cameraView.getDrawingLayer(self.portraitLayerId)?.clearDrawingItems()
            }
        }
    }

    func onDeskewedImageReceived(_ unit: DeskewedImageUnit, info: IntermediateResultExtraInfo) {
        deskewedImageUnit = unit
    }

    // MARK: Helpers

    private static func jpegDataUrl(_ image: UIImage) -> String {
        let data = image.jpegData(compressionQuality: 0.9) ?? Data()
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }
}
