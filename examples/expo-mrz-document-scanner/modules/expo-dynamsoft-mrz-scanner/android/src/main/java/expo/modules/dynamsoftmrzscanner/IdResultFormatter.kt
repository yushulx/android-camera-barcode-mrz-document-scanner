package expo.modules.dynamsoftmrzscanner

import android.util.Log
import com.dynamsoft.dcp.ParsedResultItem
import java.util.Calendar
import java.util.HashMap

/**
 * Turns a [com.dynamsoft.dcp.ParsedResultItem] produced by the native
 * `ReadPassportAndId` template into the friendly field set shown by the UI.
 * The logic mirrors the reference IdScanner example.
 */
object IdResultFormatter {

  private const val TAG = "IdResultFormatter"
  private const val DASH = "\u2014" // em dash placeholder for missing values

  /**
   * Extract the parsed MRZ fields into a display-ready map.
   */
  fun toFieldMap(item: ParsedResultItem): Map<String, String> {
    val entry: HashMap<String, String> = item.parsedFields
    val out = HashMap<String, String>()

    val codeType = item.codeType
    var docType = "PASSPORT"
    if (codeType != null) {
      if (codeType.contains("TD1") || codeType.contains("ID")) {
        docType = "ID"
      } else if (codeType.contains("VISA")) {
        docType = "VISA"
      }
    }
    out["documentType"] = docType

    val number = firstNonNull(entry, "passportNumber", "documentNumber", "idNumber")
    val firstName = firstNonNull(entry, "secondaryIdentifier", "givenNames")
    val lastName = firstNonNull(entry, "primaryIdentifier", "lastName")
    val nationality = entry["nationality"]
    val issuingState = entry["issuingState"]
    val sex = entry["sex"]

    var fullName = lastName
    if (firstName.isNotEmpty()) {
      fullName = if (fullName.isEmpty()) firstName else "$fullName, $firstName"
    }
    if (fullName.isEmpty()) {
      fullName = DASH
    }

    out["name"] = fullName
    out["sex"] = formatSex(sex)
    out["documentNumber"] = number.ifEmpty { DASH }
    out["issuingState"] = nationalityOfState(issuingState)
    out["nationality"] = nationalityOfState(nationality)
    out["dateOfBirth"] = formatDate(entry["birthYear"], entry["birthMonth"], entry["birthDay"])
    out["dateOfExpiry"] = formatDate(entry["expiryYear"], entry["expiryMonth"], entry["expiryDay"])
    out["age"] = formatAge(entry["birthYear"], entry["birthMonth"], entry["birthDay"])
    return out
  }

  private fun firstNonNull(map: Map<String, String>, vararg keys: String): String {
    for (key in keys) {
      val value = map[key]
      if (!value.isNullOrEmpty()) {
        return value
      }
    }
    return ""
  }

  private fun nationalityOfState(value: String?): String {
    if (value.isNullOrEmpty()) {
      return DASH
    }
    // The native parser already expands country codes to names; guard just in case.
    return value
  }

  private fun formatSex(sex: String?): String {
    if (sex.isNullOrEmpty()) {
      return DASH
    }
    return when (sex.uppercase()[0]) {
      'M' -> "Male"
      'F' -> "Female"
      else -> sex
    }
  }

  private fun formatDate(year: String?, month: String?, day: String?): String {
    if (year == null || month == null || day == null) {
      return DASH
    }
    return "$year-$month-$day"
  }

  private fun formatAge(year: String?, month: String?, day: String?): String {
    if (year == null || month == null || day == null) {
      return DASH
    }
    return try {
      val age = calculateAge(year.toInt(), month.toInt(), day.toInt())
      if (age >= 0) age.toString() else DASH
    } catch (e: Exception) {
      Log.e(TAG, "Failed to compute age", e)
      DASH
    }
  }

  private fun calculateAge(birthYear: Int, birthMonth: Int, birthDay: Int): Int {
    val dob = Calendar.getInstance().apply { set(birthYear, birthMonth - 1, birthDay) }
    val today = Calendar.getInstance()
    var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
      age--
    }
    return age
  }
}
