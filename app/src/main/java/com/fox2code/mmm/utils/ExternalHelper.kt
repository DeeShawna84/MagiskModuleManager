package com.fox2code.mmm.utils

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Supplier
import com.fox2code.mmm.Constants
import com.topjohnwu.superuser.internal.UiThreadHandler
import timber.log.Timber

class ExternalHelper private constructor() {
    private var fallback: ComponentName? = null
    private var label: CharSequence? = null
    private var multi = false
    fun refreshHelper(context: Context) {
        val intent = Intent(
            FOX_MMM_OPEN_EXTERNAL,
            Uri.parse("https://very-invalid-prefix-for-testing.androidacy.com")
        )
        @Suppress("DEPRECATION") val resolveInfos =
            context.packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
        if (resolveInfos.isEmpty()) {
            Timber.i("No external provider installed!")
            label = if (TEST_MODE) "External" else null
            multi = TEST_MODE
            fallback = null
        } else {
            val resolveInfo = resolveInfos[0]
            Timber.i("Found external provider: %s", resolveInfo.activityInfo.packageName)
            fallback =
                ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
            label = resolveInfo.loadLabel(context.packageManager)
            multi = resolveInfos.size >= 2
        }
    }

    fun openExternal(context: Context, uri: Uri?, repoId: String?): Boolean {
        if (label == null) return false
        val param =
            ActivityOptionsCompat.makeCustomAnimation(context, com.google.android.material.R.anim.abc_fade_in, com.google.android.material.R.anim.abc_fade_out).toBundle()
        var intent = Intent(FOX_MMM_OPEN_EXTERNAL, uri)
        intent.flags = IntentHelper.FLAG_GRANT_URI_PERMISSION
        intent.putExtra(FOX_MMM_EXTRA_REPO_ID, repoId)
        if (multi) {
            intent = Intent.createChooser(intent, label)
        } else {
            intent.putExtra(Constants.EXTRA_FADE_OUT, true)
        }
        try {
            if (multi) {
                context.startActivity(intent)
            } else {
                context.startActivity(intent, param)
            }
            return true
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
        }
        if (fallback != null) {
            if (multi) {
                intent = Intent(FOX_MMM_OPEN_EXTERNAL, uri)
                intent.putExtra(FOX_MMM_EXTRA_REPO_ID, repoId)
                intent.putExtra(Constants.EXTRA_FADE_OUT, true)
            }
            intent.component = fallback
            try {
                context.startActivity(intent, param)
                return true
            } catch (e: ActivityNotFoundException) {
                Timber.e(e)
            }
        }
        return false
    }

    fun injectButton(builder: AlertDialog.Builder, uriSupplier: Supplier<Uri?>, repoId: String?) {
        if (label == null) return
        builder.setNeutralButton(label) { dialog: DialogInterface, _: Int ->
            val context = (dialog as Dialog).context
            object : Thread("Async downloader") {
                override fun run() {
                    val uri = uriSupplier.get()
                    if (uri == null) {
                        UiThreadHandler.run {
                            Toast.makeText(
                                context,
                                "Failed to get uri",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }
                    UiThreadHandler.run {
                        if (!openExternal(context, uri, repoId)) {
                            Toast.makeText(
                                context,
                                "Failed to launch external activity",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }.start()
        }
    }

    companion object {
        @JvmField
        val INSTANCE = ExternalHelper()
        private const val TEST_MODE = false
        private const val FOX_MMM_OPEN_EXTERNAL =
            "com.fox2code.mmm.utils.intent.action.OPEN_EXTERNAL"
        private const val FOX_MMM_EXTRA_REPO_ID = "extra_repo_id"
    }
}