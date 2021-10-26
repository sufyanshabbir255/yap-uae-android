package co.yap.yapcore.helpers.extentions

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.annotation.StringRes
import com.google.android.material.textfield.TextInputLayout
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Checks if a string is a valid email
 * @return a boolean representing true if email is valid else false
 */
fun String.isEmail() = android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

/**
 * Checks if String is Number.
 * Checks against regex `^[0-9]+$`
 * @return a boolean representing true if all the characters are numeric else false
 */
fun String.isNumeric(): Boolean {
    val p = "^[0-9]+$".toRegex()
    return matches(p)
}

fun Context?.string(@StringRes idRes: Int): String = Resources.getSystem().getString(idRes)

fun isWhiteSpaces(@Nullable s: String?) = s != null && s.matches("\\s+".toRegex())

fun isEmpty(@Nullable text: String?) =
    text == null || TextUtils.isEmpty(text) || isWhiteSpaces(text) || text.equals(null)

fun isEmpty(@Nullable text: Any?) = text == null || isEmpty(text.toString())

fun isEmpty(@Nullable text: EditText?) = text == null || isEmpty(text.text.toString())

fun isEmpty(@Nullable text: TextView?) = text == null || isEmpty(text.text.toString())

fun isEmpty(@Nullable txt: TextInputLayout?) = txt == null || isEmpty(txt!!.getEditText())

fun toString(@NonNull editText: EditText) = editText.text.toString()

fun shortName(cardFullName: String): String {
    var cardFullName = cardFullName
    cardFullName = cardFullName.trim { it <= ' ' }
    var shortName = ""
    if (cardFullName.isNotEmpty() && cardFullName.contains(" ")) {
        val nameStr =
            cardFullName.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val firstName = nameStr[0]
        val lastName = nameStr[nameStr.size - 1]
        shortName = firstName.substring(0, 1) + lastName.substring(0, 1)
        return shortName.toUpperCase()
    } else if (cardFullName.length > 0) {
        val nameStr =
            cardFullName.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val firstName = nameStr[0]
        shortName = firstName.substring(0, 1)
        return shortName.toUpperCase()
    }
    return shortName.toUpperCase()
}

@SuppressLint("DefaultLocale")
fun String.toCamelCase(): String = split(" ").joinToString(" ") { it.toLowerCase().capitalize() }

fun String.getQRCode(): String {
    if ((this.contains("yap-app:"))) {
        return this.replace("yap-app:", "")
    }
    return this
}

fun String.generateQRCode(): String {
    return "yap-app:$this"
}

fun String?.encodeToUTF8(): String {
    this?.let {
        return URLEncoder.encode(it, StandardCharsets.UTF_8.name())
    } ?: return ""
}

fun String?.decodeToUTF8(): String {
    this?.let {
        return URLDecoder.decode(it, StandardCharsets.UTF_8.name())
    } ?: return ""

}

fun String?.getOtpFromMessage(): String? {
    var otpCode = ""
    this?.let {
        val pattern: Pattern = Pattern.compile("(|^)\\d{6}")
        val matcher: Matcher = pattern.matcher(it)
        if (matcher.find()) {
            otpCode = matcher.group(0) ?: ""
        }
    }
    return otpCode
}