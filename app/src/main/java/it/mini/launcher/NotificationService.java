package it.mini.launcher;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
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

    // ===== SUONI DI BLOCCO E SBLOCCO =====
    // Scegli la coppia: "interruttore", "lucchetto", "cricchetto" oppure "macchina".
    private static final String SOUND_SET = "interruttore";

    private SoundPool pool;
    private int lockSound;
    private int unlockSound;
    private boolean soundsOn;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (pool == null || intent.getAction() == null) {
                return;
            }
            switch (intent.getAction()) {
                case Intent.ACTION_SCREEN_OFF:
                    play(lockSound);
                    break;
                case Intent.ACTION_USER_PRESENT:
                    play(unlockSound);
                    break;
                default:
                    break;
            }
        }
    };

    private void play(int id) {
        if (id != 0) {
            pool.play(id, 1f, 1f, 1, 0, 1f);
        }
    }

    private int load(String suffix) {
        int res = getResources().getIdentifier(SOUND_SET + "_" + suffix, "raw", getPackageName());
        return res == 0 ? 0 : pool.load(this, res, 1);
    }

    private void startSounds() {
        if (soundsOn) {
            return;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build();
        lockSound = load("blocco");
        unlockSound = load("sblocco");

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        soundsOn = true;
    }

    private void stopSounds() {
        if (!soundsOn) {
            return;
        }
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {
            // già rimosso
        }
        if (pool != null) {
            pool.release();
            pool = null;
        }
        soundsOn = false;
    }

    @Override
    public void onListenerConnected() {
        refresh();
        startSounds();
    }

    @Override
    public void onListenerDisconnected() {
        stopSounds();
    }

    @Override
    public void onDestroy() {
        stopSounds();
        super.onDestroy();
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
