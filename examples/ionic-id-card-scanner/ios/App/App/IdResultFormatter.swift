import Foundation
import UIKit
import DynamsoftCaptureVisionBundle

/// Helpers that turn a Dynamsoft parsed MRZ item into the display fields and
/// cropped portrait used by the Ionic UI. Mirrors the Android formatter.
enum IdResultFormatter {

    /// Extract parsed MRZ fields into a display-ready dictionary.
    static func fieldMap(for item: DSMrzParsedResultItem) -> [String: String] {
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

    /// Perspective-correct a portrait rectangle found inside a scaled frame.
    static func deskewPortrait(_ source: UIImage, quad: Quadrilateral) -> UIImage? {
        guard quad.points.count == 4 else { return nil }
        let points = quad.points
        let width = max(hypot(points[1].x - points[0].x, points[1].y - points[0].y),
                        hypot(points[2].x - points[3].x, points[2].y - points[3].y))
        let height = max(hypot(points[3].x - points[0].x, points[3].y - points[0].y),
                         hypot(points[2].x - points[1].x, points[2].y - points[1].y))
        guard width > 0, height > 0 else { return nil }

        let targetSize = CGSize(width: width, height: height)
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        return renderer.image { _ in
            let context = UIGraphicsGetCurrentContext()
            context?.setFillColor(UIColor.white.cgColor)
            context?.fill(CGRect(origin: .zero, size: targetSize))
            let transform = perspectiveTransform(
                from: points.map { CGPoint(x: $0.x, y: $0.y) },
                to: [
                    CGPoint(x: 0, y: 0),
                    CGPoint(x: width, y: 0),
                    CGPoint(x: width, y: height),
                    CGPoint(x: 0, y: height)
                ])
            context?.concatenate(transform)
            source.draw(at: .zero)
        }
    }

    private static func perspectiveTransform(from source: [CGPoint], to target: [CGPoint]) -> CGAffineTransform {
        // Simple approximation using the quad's bounding box plus a projective-ish
        // skew via CGAffineTransform. For the sample app this matches the Android
        // setPolyToPoly approach closely enough for portrait thumbnails.
        let sx = target[1].x / max(source[1].x - source[0].x, 1)
        let sy = target[3].y / max(source[3].y - source[0].y, 1)
        return CGAffineTransform(translationX: -source[0].x, y: -source[0].y)
            .scaledBy(x: sx, y: sy)
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

/// The parsed MRZ item type as surfaced by the Dynamsoft iOS SDK.
typealias DSMrzParsedResultItem = ParsedResultItem
