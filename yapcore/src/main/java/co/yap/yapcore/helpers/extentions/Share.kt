package co.yap.yapcore.helpers.extentions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.annotation.Keep
import androidx.fragment.app.FragmentActivity
import co.yap.yapcore.constants.Constants
import co.yap.yapcore.constants.Constants.URL_SHARE_PLAY_STORE
import com.github.florent37.inlineactivityresult.kotlin.startForResult

@Keep
        /**
         * Opens the url in the available application
         * @return A boolean representing if the action was successful or not
         */
fun Context.openUrl(url: String, newTask: Boolean = false): Boolean {
    return try {
        Intent().apply {
            action = ACTION_VIEW
            data = Uri.parse(url)
            if (newTask) addFlags(FLAG_ACTIVITY_NEW_TASK)
        }.also {
            val possibleActivitiesList: List<ResolveInfo> =
                packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL)
            if (possibleActivitiesList.size > 1) {
                it.resolveActivity(packageManager)?.run {
                    startActivity(createChooser(it, ""))
                }
            } else {
                it.resolveActivity(packageManager)?.run {
                    startActivity(it)
                }
            }

        }
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Opens the share context menu
 * @return A boolean representing if the action was successful or not
 */
fun Context.share(
    text: String?,
    subject: String? = "",
    title: String? = null
) {
    try {
        Intent(ACTION_SEND).apply {
            type = "text/plain"
            subject?.let { putExtra(EXTRA_SUBJECT, subject) }
            text?.let { putExtra(EXTRA_TEXT, text) }
        }.also {
            val possibleActivitiesList: List<ResolveInfo> =
                packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL)
            if (possibleActivitiesList.size > 1) {
                it.resolveActivity(packageManager)?.run {
                    startActivity(createChooser(it, title ?: ""))
                }
            } else {
                it.resolveActivity(packageManager)?.run {
                    startActivity(it)
                }
            }
        }
        // success.invoke(true)
    } catch (e: ActivityNotFoundException) {

    }
}

/**
 * Opens the email application
 * @param email A recipient email
 * @param subject An optional subject of email
 * @param text An option body of the email
 * @return A boolean representing if the action was successful or not
 */
fun Context.sendEmail(
    email: String? = null,
    subject: String? = null,
    text: String? = null
): Boolean {
    return try {
        Intent().apply {
            action = ACTION_SENDTO
            data = Uri.parse("mailto:")
            putExtra(EXTRA_EMAIL, arrayOf(email))
            subject?.let { putExtra(EXTRA_SUBJECT, it) }
            text?.let { putExtra(EXTRA_TEXT, it) }
        }.also {
            val possibleActivitiesList: List<ResolveInfo> =
                packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL)
            if (possibleActivitiesList.size > 1) {
                it.resolveActivity(packageManager)?.run {
                    startActivity(createChooser(it, "Send Email"))
                }

            } else {
                it.resolveActivity(packageManager)?.run {
                    startActivity(it)
                }
            }
        }
        return true
    } catch (e: Exception) {
        false
    }
}

/**
 * Opens the dialer that handles the given number
 * @param number A phone number to open in the dialer
 * @return A boolean representing if the action was successful or not
 */
fun Context.makeCall(number: String?): Boolean {
    return try {
        Intent(ACTION_DIAL, Uri.parse("tel:$number")).also {
            it.resolveActivity(packageManager)?.run {
                startActivity(it)
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun Context.isWhatsAppInstalled(): Boolean {
    val pm: PackageManager = this.packageManager
    val app_installed: Boolean
    app_installed = try {
        pm.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
    return app_installed
}

fun Context.openWhatsApp() {
    val contact = "+971 4 365 3789" // use country code with your phone number
    val url =
        "https://api.whatsapp.com/send?phone=$contact"
    val i = Intent(ACTION_VIEW)
    i.data = Uri.parse(url)
    if (i.resolveActivity(packageManager) != null)
        startActivity(i)
}

/**
 * Opens the SMS application to send an SMS
 * @param number A phone number to send an SMS
 * @param text An optional predefined text message for the SMS
 * @return A boolean representing if the action was successful or not
 */
fun Context.sendSms(number: String, text: String = ""): Boolean {
    return try {
        val intent = Intent(ACTION_VIEW, Uri.parse("sms:$number")).apply {
            putExtra("sms_body", text)
        }.also {
            it.resolveActivity(packageManager)?.run {
                startActivity(it)
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Opens your application page inside the play store
 * @return A boolean representing if the action was successful or not
 */

fun Context.openPlayStore(): Boolean =
    openUrl(URL_SHARE_PLAY_STORE)

fun Context.openTwitter() {
    if (isPackageInstalled("com.twitter.android")) {
        Intent(ACTION_VIEW).apply {
            data = Uri.parse(Constants.URL_TWITTER)
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }.also { intent ->
            intent.resolveActivity(packageManager)?.let {
                startActivity(intent)
            }
        }
    } else {
        openUrl(Constants.URL_TWITTER)
    }
}

fun Context.openFacebook() {
    if (isPackageInstalled("com.facebook.katana")) {
        Intent(ACTION_VIEW).apply {
            data = Uri.parse("fb://page/288432705359181")
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }.also { intent ->
            intent.resolveActivity(packageManager)?.let {
                startActivity(intent)
            }
        }
    } else {
        openUrl(Constants.URL_FACEBOOK)
    }
}

fun Context.openInstagram() {
    if (isPackageInstalled("com.instagram.android")) {
        Intent(ACTION_VIEW).apply {
            data = Uri.parse(Constants.URL_INSTAGRAM)
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }.also { intent ->
            intent.resolveActivity(packageManager)?.let {
                startActivity(intent)
            }
        }
    } else {
        openUrl(Constants.URL_INSTAGRAM)
    }
}

fun Context.isPackageInstalled(packageName: String): Boolean {
    return try {
        packageManager.getPackageInfo(packageName, 0) != null
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

fun Context.isApplicationInstalledAndEnable(packageName: String): Boolean {
    return try {
        packageManager.getApplicationInfo(packageName, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

inline fun FragmentActivity.openFilePicker(
    title: String? = "",
    noinline completionHandler: ((resultCode: Int, data: Intent?) -> Unit)? = null
) {

    try {
        Intent(ACTION_OPEN_DOCUMENT).apply {
            val mimeTypes = arrayOf("image/*", "application/pdf")
            putExtra(EXTRA_ALLOW_MULTIPLE, false)
            addCategory(CATEGORY_OPENABLE)
            addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra(EXTRA_MIME_TYPES, mimeTypes)
            type = "*/*"
        }.also {
            val possibleActivitiesList: List<ResolveInfo> =
                packageManager.queryIntentActivities(it, PackageManager.MATCH_ALL)
            if (possibleActivitiesList.size > 1) {
                it.resolveActivity(packageManager)?.run {
                    this@openFilePicker.startForResult(createChooser(it, title ?: "")) { result ->
                        completionHandler?.invoke(result.resultCode, result.data)
                    }.onFailed { result ->
                        completionHandler?.invoke(result.resultCode, result.data)
                    }
                }
            } else {
                it.resolveActivity(packageManager)?.run {
                    this@openFilePicker.startForResult(createChooser(it, title ?: "")) { result ->
                        completionHandler?.invoke(result.resultCode, result.data)
                    }.onFailed { result ->
                        completionHandler?.invoke(result.resultCode, result.data)
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

}
