package it.mini.launcher;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tiene traccia delle app che hanno una notifica da leggere.
 * Il launcher usa questa lista per mostrare il pallino rosso accanto al nome.
 * Le notifiche "in corso" (chiamata attiva, USB collegato, ecc.) non contano.
 */
public class NotificationService extends NotificationListenerService {

    /** Pacchetti con almeno una notifica non "in corso". */
    static final Set<String> active = Collections.synchronizedSet(new HashSet<String>());

    /** Chiamato (sul thread principale) quando la lista cambia. */
    static volatile Runnable onChange;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void onListenerConnected() {
        refresh();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        refresh();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        refresh();
    }

    private void refresh() {
        Set<String> now = new HashSet<>();
        try {
            StatusBarNotification[] all = getActiveNotifications();
            if (all != null) {
                for (StatusBarNotification sbn : all) {
                    int flags = sbn.getNotification().flags;
                    if ((flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR)) != 0) {
                        continue;
                    }
                    now.add(sbn.getPackageName());
                }
            }
        } catch (Exception ignored) {
            // il sistema può rifiutare la richiesta se il servizio si sta fermando
        }
        synchronized (active) {
            active.clear();
            active.addAll(now);
        }
        Runnable r = onChange;
        if (r != null) {
            MAIN.post(r);
        }
    }
}
