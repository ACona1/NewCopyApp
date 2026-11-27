package com.assem.copyapp.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager

class AllowlistProvider(private val context: Context) {

    fun ownPackage(): String = context.packageName

    fun systemAllowlist(): Set<String> {
        val result = linkedSetOf<String>()
        val pm: PackageManager? = try {
            context.packageManager
        } catch (t: Throwable) {
            null
        }

        fun resolveDefault(intent: Intent) {
            try {
                pm?.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
                    ?.takeIf { it.isNotBlank() }
                    ?.let { result.add(it) }
            } catch (t: Throwable) {
                // تجاهل فشل الحل
            }
        }

        resolveDefault(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        resolveDefault(Intent(Intent.ACTION_DIAL))
        resolveDefault(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))

        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val methods: List<InputMethodInfo>? = imm?.enabledInputMethodList
            methods?.forEach { info ->
                try {
                    if (info.packageName.isNotBlank()) result.add(info.packageName)
                } catch (t: Throwable) {
                    // تجاهل لوحة مفاتيح مفقودة
                }
            }
        } catch (t: Throwable) {
            // تجاهل خدمة الإدخال
        }

        try {
            result.add(context.packageName)
        } catch (t: Throwable) {
            // تجاهل
        }
        result.addAll(
            listOf(
                "com.android.systemui",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller"
            )
        )
        return result
    }

    fun isAllowed(pkg: String?, userExceptions: Set<String>): Boolean {
        if (pkg.isNullOrBlank()) return true
        return try {
            pkg in userExceptions || pkg in systemAllowlist()
        } catch (t: Throwable) {
            pkg in userExceptions
        }
    }
}
