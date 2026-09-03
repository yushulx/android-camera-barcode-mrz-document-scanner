import UIKit
import DynamsoftCaptureVisionBundle

/// Helpers that turn a Dynamsoft parsed MRZ item into the display fields and
/// cropped portrait used by the Ionic UI. Mirrors the Android formatter.
enum IdResultFormatter {

    /// Extract parsed MRZ fields into a display-ready dictionary.
    static func fieldMap(for item: ParsedResultItem) -> [String: String] {
        let entry = item.parsedFields
        let codeType = item.codeType ?? ""

        var documentType = "PASSPORT"
        if codeType.contains("TD1") || codeType.contains("ID") {
            documentType = "ID"
        } else if codeType.contains("VISA") {
            documentType = "VISA"
        }

        let number = firstNonEmpty(entry, keys: "passportNumber", "documentNumber", "idNumber")
        let firstName = firstNonEmpty(entry, keys: "secondaryIdentifier", "givenNames")
        let lastName = firstNonEmpty(entry, keys: "primaryIdentifier", "lastName")

        var fullName = lastName
        if !firstName.isEmpty {
            fullName = fullName.isEmpty ? firstName : "\(fullName), \(firstName)"
        }
        if fullName.isEmpty {
            fullName = "—"
        }

        var fields: [String: String] = [:]
        fields["documentType"] = documentType
        fields["name"] = fullName
        fields["sex"] = formattedSex(entry["sex"])
        fields["documentNumber"] = number.isEmpty ? "—" : number
        fields["issuingState"] = entry["issuingState"] ?? "—"
        fields["nationality"] = entry["nationality"] ?? "—"
        fields["dateOfBirth"] = formattedDate(year: entry["birthYear"], month: entry["birthMonth"], day: entry["birthDay"])
        fields["dateOfExpiry"] = formattedDate(year: entry["expiryYear"], month: entry["expiryMonth"], day: entry["expiryDay"])
        fields["age"] = formattedAge(year: entry["birthYear"], month: entry["birthMonth"], day: entry["birthDay"])
        return fields
    }

    /// Convert the ImageData of a deskewed page / portrait region to a JPEG data URL.
    static func jpegDataUrl(from imageData: ImageData?) -> String? {
        guard let imageData, let ui = image(from: imageData) else { return nil }
        return jpegDataUrl(from: ui)
    }

    static func jpegDataUrl(from image: UIImage) -> String {
        let data = image.jpegData(compressionQuality: 0.9) ?? Data()
        return "data:image/jpeg;base64," + data.base64EncodedString()
    }

    /// Fallback portrait: passports/ID cards usually print the photo in the
    /// upper middle band of the document. Crops that region when the SDK did
    /// not report a dedicated PortraitZone.
    static func cropTopPortrait(from document: UIImage) -> UIImage? {
        let dw = document.size.width
        let dh = document.size.height
        let cropWidth = dw * 0.86
        let cropHeight = dh * 0.36
        let rect = CGRect(x: (dw - cropWidth) / 2, y: 0,
                          width: cropWidth, height: cropHeight)
        guard let cg = document.cgImage,
              let cropped = cg.cropping(to: rect) else { return nil }
        return UIImage(cgImage: cropped)
    }

    /// Perspective-correct a portrait rectangle found inside a scaled frame.
    static func deskewPortrait(_ source: UIImage, quad: Quadrilateral) -> UIImage? {
        guard quad.points.count == 4 else { return nil }
        let pts = quad.points.map { $0.cgPointValue }
        let width = max(hypot(pts[1].x - pts[0].x, pts[1].y - pts[0].y),
                        hypot(pts[2].x - pts[3].x, pts[2].y - pts[3].y))
        let height = max(hypot(pts[3].x - pts[0].x, pts[3].y - pts[0].y),
                         hypot(pts[2].x - pts[1].x, pts[2].y - pts[1].y))
        guard width > 0, height > 0, width < 2000, height < 2000 else { return nil }

        let targetSize = CGSize(width: width, height: height)
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { _ in
            let ctx = UIGraphicsGetCurrentContext()
            ctx?.setFillColor(UIColor.white.cgColor)
            ctx?.fill(CGRect(origin: .zero, size: targetSize))
            // Perspective-ish approximation via the affine part of the quad.
            let sx = width / max(pts[1].x - pts[0].x, 1)
            let sy = height / max(pts[3].y - pts[0].y, 1)
            ctx?.concatenate(CGAffineTransform(translationX: -pts[0].x, y: -pts[0].y)
                .scaledBy(x: sx, y: sy))
            source.draw(at: .zero)
        }
    }

    // MARK: - ImageData → UIImage

    /// Renders the raw pixel buffer as a UIImage for the common pixel formats.
    /// Every input format is decoded pixel-by-pixel into RGB888 so channel
    /// order (BGR/ARGB/ABGR) can never produce a wrong-looking image.
    static func image(from data: ImageData) -> UIImage? {
        let w = Int(data.width)
        let h = Int(data.height)
        guard w > 0, h > 0, w * h < 200_000_000 else { return nil }
        let bytes = [UInt8](data.bytes as Data)
        let rowBytesInput = Int(data.stride) > 0 ? Int(data.stride) : 0
        let fmt = data.format.rawValue

        // Pixel format constants (DSImagePixelFormat):
        // 0 Binary, 1 BinaryInverted, 2 GrayScaled, 3 NV21, 4 RGB565, 5 RGB555,
        // 6 RGB888, 7 ARGB8888, 8 RGB161616, 9 ARGB16161616, 10 ABGR8888,
        // 11 ABGR16161616, 12 BGR888, 13 Binary8, 14 NV12, 15 Binary8Inverted
        let bpp: Int
        switch fmt {
        case 2: bpp = 1          // GrayScaled
        case 6: bpp = 3          // RGB888
        case 12: bpp = 3         // BGR888
        case 7: bpp = 4          // ARGB8888
        case 10: bpp = 4         // ABGR8888
        default: return nil
        }
        let rowBytes = rowBytesInput > 0 ? rowBytesInput : w * bpp
        guard bytes.count >= rowBytes * h else { return nil }

        // Decode into a flat RGB888 buffer.
        var out = [UInt8](repeating: 0, count: w * h * 3)
        for y in 0..<h {
            let rowBase = y * rowBytes
            var op = y * w * 3
            for x in 0..<w {
                let p = rowBase + x * bpp
                let r: UInt8, g: UInt8, b: UInt8
                switch fmt {
                case 2: // gray → rgb
                    r = bytes[p]; g = r; b = r
                case 6: // RGB888
                    r = bytes[p]; g = bytes[p + 1]; b = bytes[p + 2]
                case 12: // BGR888 → swap R/B
                    b = bytes[p]; g = bytes[p + 1]; r = bytes[p + 2]
                case 7: // ARGB8888 → RGB
                    r = bytes[p + 1]; g = bytes[p + 2]; b = bytes[p + 3]
                case 10: // ABGR8888 → swap R/B
                    b = bytes[p + 1]; g = bytes[p + 2]; r = bytes[p + 3]
                default:
                    r = 0; g = 0; b = 0
                }
                out[op] = r; out[op + 1] = g; out[op + 2] = b
                op += 3
            }
        }

        guard let provider = CGDataProvider(data: Data(out) as CFData) else { return nil }
        guard let cg = CGImage(width: w,
                               height: h,
                               bitsPerComponent: 8,
                               bitsPerPixel: 24,
                               bytesPerRow: w * 3,
                               space: CGColorSpaceCreateDeviceRGB(),
                               bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
                               provider: provider,
                               decode: nil,
                               shouldInterpolate: true,
                               intent: .defaultIntent) else { return nil }
        return UIImage(cgImage: cg)
    }

    // MARK: - Field helpers

    private static func firstNonEmpty(_ map: [String: String], keys: String...) -> String {
        for key in keys where !(map[key] ?? "").isEmpty {
            return map[key] ?? ""
        }
        return ""
    }

    private static func formattedSex(_ sex: String?) -> String {
        guard let sex = sex, !sex.isEmpty else { return "—" }
        switch sex.uppercased().first {
        case "M": return "Male"
        case "F": return "Female"
        default: return sex
        }
    }

    private static func formattedDate(year: String?, month: String?, day: String?) -> String {
        guard let year, let month, let day else { return "—" }
        return "\(year)-\(month)-\(day)"
    }

    private static func formattedAge(year: String?, month: String?, day: String?) -> String {
        guard let year = year.flatMap(Int.init),
              let month = month.flatMap(Int.init),
              let day = day.flatMap(Int.init) else { return "—" }
        let calendar = Calendar.current
        let dob = calendar.date(from: DateComponents(year: year, month: month, day: day)) ?? Date()
        let age = calendar.dateComponents([.year], from: dob, to: Date()).year ?? -1
        return age >= 0 ? "\(age)" : "—"
    }
}
