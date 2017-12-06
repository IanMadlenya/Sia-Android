/*
 * Copyright (c) 2017 Nicholas van Dyke
 *
 * This file is subject to the terms and conditions defined in 'LICENSE.md'
 */

package vandyke.siamobile.backend.siad

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.os.*
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import vandyke.siamobile.R
import vandyke.siamobile.ui.MainActivity
import vandyke.siamobile.ui.settings.Prefs
import vandyke.siamobile.util.NotificationUtil
import vandyke.siamobile.util.StorageUtil
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

class SiadService : Service() {

    private val binder = LocalBinder()

    private val statusReceiver: StatusReceiver = StatusReceiver(this)
    private var siadFile: File? = null
    private var siadProcess: Process? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val SIAD_NOTIFICATION = 3
    var isSiadRunning: Boolean = false
        get() = siadProcess != null

    override fun onCreate() {
        startForeground(SIAD_NOTIFICATION, buildSiadNotification("Starting service..."))
        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
//        applicationContext.registerReceiver(statusReceiver, intentFilter)
        siadFile = StorageUtil.copyBinary("siad", this@SiadService)
        startSiad()
    }

    /**
     * should only be called from the SiadService's BroadcastReceiver or from onCreate of this service
     */
    fun startSiad() {
        if (siadProcess != null) {
            return
        }
        if (siadFile == null) {
            siadNotification("Siad unsupported")
            return
        }
        /* acquire partial wake lock to keep device CPU awake and therefore keep the Sia node active */
        if (Prefs.SiaNodeWakeLock) {
            createWakeLockAndAcquire()
        }
        val pb = ProcessBuilder(siadFile!!.absolutePath, "-M", "gctw")
        pb.redirectErrorStream(true)
        pb.directory(StorageUtil.getWorkingDirectory(this@SiadService))
        siadProcess = pb.start()
        launch(CommonPool) {
            try {
                val inputReader = BufferedReader(InputStreamReader(siadProcess?.inputStream))
                var line: String? = inputReader.readLine()
                while (line != null) {
                    output.onNext(line)
                    line = inputReader.readLine()
                }
                inputReader.close()
                output.onComplete()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        output.observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    siadNotification(it)
                }
    }

    /**
     * should only be called from the SiadService's BroadcastReceiver or from onDestroy of this service
     */
    fun stopSiad() {
        wakeLock?.release()
        // TODO: maybe shut it down using stop http request instead? Takes ages sometimes. But might fix the (sometime) long startup times
        siadProcess?.destroy()
        siadProcess = null
    }

    fun createWakeLockAndAcquire() {
        wakeLock?.release() /* first release the wakeLock in case we have one that's already active */
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sia node")
        wakeLock!!.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    override fun onDestroy() {
//        applicationContext.unregisterReceiver(statusReceiver)
        NotificationUtil.cancelNotification(applicationContext, SIAD_NOTIFICATION)
        stopSiad()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    fun siadNotification(text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SIAD_NOTIFICATION, buildSiadNotification(text))
    }

    private fun buildSiadNotification(text: String): Notification {
        val builder = Notification.Builder(this)
        builder.setSmallIcon(R.drawable.ic_local_full_node)
        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.sia_logo_transparent)
        builder.setLargeIcon(largeIcon)
        builder.setContentTitle("Sia node")
        builder.setContentText(text)
        builder.setOngoing(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setChannelId("sia")
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        builder.setContentIntent(pendingIntent)
        return builder.build()
    }

    companion object {
        /**
         * HAVE to unsubscribe from this properly, or else crashes could occur when the app is killed and
         * later restarted if the static variable hasn't been cleared, and therefore isn't recreated, and
         * will still have references to old, now non-existent subscribers.
         * Primary reason for using a static variable for it is because that way it exists independently of
         * the service, and is not destroyed when the service is, meaning that subscribers will still receive updates
         * when the service is restarted and causes the observable to emit.
         */
        val output = PublishSubject.create<String>()!!

        fun isBatteryGood(intent: Intent): Boolean {
            if (intent.action != Intent.ACTION_BATTERY_CHANGED)
                return false
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
            return (level * 100 / scale) >= Prefs.localNodeMinBattery
        }

        fun isConnectionGood(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetInfo = connectivityManager.activeNetworkInfo
            return activeNetInfo != null && activeNetInfo.type == ConnectivityManager.TYPE_WIFI || Prefs.runLocalNodeOffWifi
            // TODO: maybe this should instead check that the type is not TYPE_DATA? Depends on the behavior I want
        }

        fun singleAction(context: Context, action: (service: SiadService) -> Unit) {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    action((service as SiadService.LocalBinder).service)
                    context.unbindService(this)
                }

                override fun onServiceDisconnected(name: ComponentName) {}
            }
            context.bindService(Intent(context, SiadService::class.java), connection, Context.BIND_AUTO_CREATE)
        }

        fun getService(context: Context) =
                Single.create<SiadService> {
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            it.onSuccess((service as SiadService.LocalBinder).service)
                            context.unbindService(this)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {}
                    }
                    context.bindService(Intent(context, SiadService::class.java), connection, Context.BIND_AUTO_CREATE)
                }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        val service: SiadService
            get() = this@SiadService
    }
}
