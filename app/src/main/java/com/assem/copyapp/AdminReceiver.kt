package com.assem.copyapp

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "تم تفعيل حماية الجهاز", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "إيقاف الصلاحية سيسمح بإيقاف حظر التطبيقات أثناء جلسة التركيز."

    override fun onDisabled(context: Context, intent: Intent) {
        SessionManager(context).stop()
    }
}
