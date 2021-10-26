package co.yap.widgets.keyboardvisibilityevent

import android.app.Activity
import android.app.Application
import android.os.Bundle

abstract class AutoActivityLifecycleCallback internal constructor(
    private val targetActivity: Activity
) : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        // no-op
    }

    override fun onActivityStarted(activity: Activity) {
        // no-op
    }

    override fun onActivityResumed(activity: Activity) {
        // no-op
    }

    override fun onActivityPaused(activity: Activity) {
        // no-op
    }

    override fun onActivityStopped(activity: Activity) {
        // no-op
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        // no-op
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === targetActivity) {
            targetActivity.application.unregisterActivityLifecycleCallbacks(this)
            onTargetActivityDestroyed()
        }
    }

    protected abstract fun onTargetActivityDestroyed()
}
