package com.example.tamagotchi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import java.util.concurrent.TimeUnit

class NotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val CHANNEL_ID = "pet_care_channel"
        const val NOTIFICATION_ID = 1001
        private const val PERIODIC_WORK_TAG = "pet_care_periodic_worker"
        private const val CRITICAL_WORK_TAG = "pet_care_critical_worker"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = context.getString(R.string.notification_channel_name)
                val descriptionText = context.getString(R.string.notification_channel_description)
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 100, 250)
                }
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun schedulePeriodicCareCheck(context: Context) {
            createNotificationChannel(context)
            val constraints = Constraints.Builder()
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<NotificationWorker>(
                repeatInterval = 30,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
                flexTimeInterval = 10,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        fun scheduleCriticalAlert(context: Context, delayMinutes: Long = 15) {
            createNotificationChannel(context)
            val alertRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                CRITICAL_WORK_TAG,
                ExistingWorkPolicy.REPLACE,
                alertRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val petPrefs = PetPreferences(context)
        val (pet, _) = petPrefs.loadPet()

        // Don't alert if pet is an unhatched egg or fully sleeping
        if (pet.isEgg || pet.isSleeping) {
            return Result.success()
        }

        var alertTitle: String? = null
        var alertMessage: String? = null

        when {
            pet.isSick -> {
                alertTitle = "💊 ${pet.name} is sick!"
                alertMessage = "Your virtual pet caught a bug! Tap to give medicine."
            }
            pet.hunger <= 25f -> {
                alertTitle = "🍖 ${pet.name} is hungry!"
                alertMessage = "Hunger is down to ${pet.hunger.toInt()}%! Feed your pet some treats."
            }
            pet.happiness <= 25f -> {
                alertTitle = "🎾 ${pet.name} wants to play!"
                alertMessage = "Happiness is low (${pet.happiness.toInt()}%). Come play a mini-game!"
            }
            pet.poopCount > 0 -> {
                alertTitle = "🧹 Pet needs cleaning!"
                alertMessage = "${pet.name}'s room is messy! Clean it to keep your pet healthy."
            }
            pet.canEvolve -> {
                alertTitle = "✨ Ready to Evolve!"
                alertMessage = "${pet.name} is shining with cosmic energy! Tap to trigger evolution!"
            }
        }

        if (alertTitle != null && alertMessage != null) {
            showNotification(alertTitle, alertMessage)
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission might have been revoked at runtime
        }
    }
}
