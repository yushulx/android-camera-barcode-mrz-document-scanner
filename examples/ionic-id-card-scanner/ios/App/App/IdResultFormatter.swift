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
    static func image(from data: ImageData) -> UIImage? {
        let w = Int(data.width)
        let h = Int(data.height)
        guard w > 0, h > 0, w * h < 200_000_000 else { return nil }
        let bytes = data.bytes as Data

        var colorSpace = CGColorSpaceCreateDeviceRGB()
        var bitmapInfo: UInt32 = CGImageAlphaInfo.noneSkipLast.rawValue
        var bitsPerPixel = 32
        var bytesPerRow = Int(data.stride)
        var start = 0
        let fmt = data.format.rawValue

        // Dynamsoft pixel format constants (DSImagePixelFormat enum order):
        // Binary=0, BinaryInverted=1, GrayScaled=2, NV21=3, RGB565=4, RGB555=5,
        // RGB888=6, ARGB8888=7, RGB161616=8, ARGB16161616=9, ABGR8888=10,
        // ABGR16161616=11, BGR888=12, Binary8=13, NV12=14, Binary8Inverted=15
        switch fmt {
        case 2: // GrayScaled
            colorSpace = CGColorSpaceCreateDeviceGray()
            bitmapInfo = CGImageAlphaInfo.none.rawValue
            bitsPerPixel = 8
        case 6: // RGB888
            bitmapInfo = CGImageAlphaInfo.none.rawValue | CGBitmapInfo.byteOrderDefault.rawValue
            bitsPerPixel = 24
        case 12: // BGR888
            bitmapInfo = CGImageAlphaInfo.none.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
            bitsPerPixel = 24
        case 7: // ARGB8888
            bitmapInfo = CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
            bitsPerPixel = 32
        case 10: // ABGR8888
            bitmapInfo = CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
            bitsPerPixel = 32
        default:
            return nil
        }
        if bytesPerRow == 0 {
            bytesPerRow = w * bitsPerPixel / 8
        }
        guard bytes.count >= start + bytesPerRow * h else { return nil }
        let providerData = bytes.subdata(in: start..<bytes.count) as CFData
        guard let provider = CGDataProvider(data: providerData) else { return nil }
        guard let cg = CGImage(width: w,
                               height: h,
                               bitsPerComponent: 8,
                               bitsPerPixel: bitsPerPixel,
                               bytesPerRow: bytesPerRow,
                               space: colorSpace,
                               bitmapInfo: CGBitmapInfo(rawValue: bitmapInfo),
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
