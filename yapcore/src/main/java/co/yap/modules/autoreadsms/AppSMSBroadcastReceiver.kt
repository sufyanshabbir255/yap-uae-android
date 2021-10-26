package co.yap.modules.autoreadsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status


class MySMSBroadcastReceiver(private val onSmsReceiveListener: OnSmsReceiveListener) :
    BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { con ->
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent?.action) {
                val extras = intent.extras
                val status: Status? = extras!![SmsRetriever.EXTRA_STATUS] as Status?
                when (status?.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        extras.getParcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)?.apply {
                            resolveActivity(con.packageManager)?.run {
                                if (packageName == "com.google.android.gms" && className == "com.google.android.gms.auth.api.phone.ui.UserConsentPromptActivity"
                                )
                                    onSmsReceiveListener.onReceive(this@apply)
                            }
                        }
                    }
                    CommonStatusCodes.TIMEOUT -> {
                    }
                }
            }
        }
    }

    interface OnSmsReceiveListener {
        fun onReceive(code: Intent?)
    }
}

