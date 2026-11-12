package my.autotainment.app.media

import android.service.notification.NotificationListenerService

/**
 * Kosong secara sengaja. Android hanya bagi akses senarai sesi media (lagu yang sedang main)
 * kepada app yang mempunyai Notification Listener aktif. Kita tak baca kandungan notifikasi.
 */
class MediaListenerService : NotificationListenerService()
