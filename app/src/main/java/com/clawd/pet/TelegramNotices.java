package com.clawd.pet;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import androidx.core.app.NotificationManagerCompat;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class TelegramNotices extends NotificationListenerService {
    private static final Set<String> CLIENTS = new HashSet<>(Arrays.asList(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram", "com.exteragram.messenger"));

    static boolean allowed(Context context) {
        if (Build.VERSION.SDK_INT >= 27) {
            return context.getSystemService(NotificationManager.class)
                    .isNotificationListenerAccessGranted(new ComponentName(context, TelegramNotices.class));
        }
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.getPackageName());
    }

    @Override public void onNotificationPosted(StatusBarNotification item) {
        PetService pet = PetService.current;
        if (pet == null || item == null || !CLIENTS.contains(item.getPackageName())
                || !PetService.prefs(this).getBoolean("tg_events", false)) return;
        Notification notice = item.getNotification();
        if ((notice.flags & Notification.FLAG_GROUP_SUMMARY) != 0
                || !Notification.CATEGORY_MESSAGE.equals(notice.category)) return;
        CharSequence body = notice.extras == null ? null : notice.extras.getCharSequence(Notification.EXTRA_TEXT);
        pet.notificationArrived(body == null ? "" : body.subSequence(0, Math.min(body.length(), 400)).toString());
    }
}
