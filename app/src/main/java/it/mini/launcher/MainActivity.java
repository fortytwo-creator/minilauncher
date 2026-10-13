package it.mini.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Schermata Home minimale: solo una lista con icona a sinistra e nome a destra.
 * Niente orologio, niente barra delle app in basso.
 * Lo sfondo è quello impostato nel telefono.
 */
public class MainActivity extends Activity {

    private static final int INK = Color.parseColor("#2B2B2B");

    private static final class Entry {
        final String label;      // nome mostrato in lista
        final int icon;          // icona (cartella res/drawable-nodpi)
        final String keyword;    // se nessun pacchetto è installato, cerca un'app con questo nome
        final String[] packages; // pacchetti da provare, nell'ordine

        Entry(String label, int icon, String keyword, String... packages) {
            this.label = label;
            this.icon = icon;
            this.keyword = keyword;
            this.packages = packages;
        }
    }

    // ===== LISTA DELLE APP =====
    // Per cambiare ordine o nomi basta modificare questo elenco.
    // Contatti e orologio non sono qui: si trovano in "altre app".
    private final Entry[] entries = new Entry[] {
        new Entry("telefono", R.drawable.ic_telefono, "telefono",
                "org.fossify.phone", "com.google.android.dialer", "com.android.dialer"),
        new Entry("messaggi", R.drawable.ic_messaggi, "messaggi",
                "org.fossify.messages", "com.google.android.apps.messaging", "com.android.mms"),
        new Entry("whatsapp", R.drawable.ic_chat, "whatsapp",
                "com.whatsapp"),
        new Entry("calendario", R.drawable.ic_calendario, "calendario",
                "org.fossify.calendar", "com.google.android.calendar", "com.android.calendar"),
        new Entry("calcolatrice", R.drawable.ic_calcolatrice, "calcolatrice",
                "org.fossify.calculator", "com.google.android.calculator", "com.android.calculator2"),
        new Entry("fotocamera", R.drawable.ic_fotocamera, "fotocamera",
                "com.android.camera2", "com.android.camera"),
        new Entry("kiwix", R.drawable.ic_wiki, "kiwix",
                "org.kiwix.kiwixmobile"),
        new Entry("paint", R.drawable.ic_paint, "paint",
                "org.fossify.paint"),
        new Entry("impostazioni", R.drawable.ic_impostazioni, "impostazioni",
                "com.android.settings"),
    };

    // ===== APP NASCOSTE DA "ALTRE APP" =====
    // Oltre a quelle già presenti nella lista principale, queste non compaiono mai in "altre app".
    private static final String[] HIDDEN_PACKAGES = {
        "org.fossify.camera",           // fotocamera di Fossify
    };
    // Parole (in minuscolo) contenute nel nome dell'app: se ci sono, l'app viene nascosta.
    private static final String[] HIDDEN_WORDS = {
        "uptodown",                     // Uptodown App Store
    };

    private ScrollView scroll;
    private LinearLayout list;
    private boolean showingAll = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(36), dp(24), dp(24), dp(32));
        scroll.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!showingAll) {
            showMain();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showMain(); // premendo Home si torna sempre alla lista principale
    }

    @Override
    public void onBackPressed() {
        if (showingAll) {
            showMain();
        }
        // altrimenti non fa nulla: una schermata Home non si chiude
    }

    // ---------------------------------------------------------------- lista principale

    private void showMain() {
        showingAll = false;
        list.removeAllViews();
        list.setGravity(Gravity.BOTTOM);

        PackageManager pm = getPackageManager();
        for (Entry e : entries) {
            final Intent launch = findLaunchIntent(pm, e);
            final String name = e.label;
            addRow(name, e.icon, launch != null, () -> {
                if (launch == null) {
                    Toast.makeText(this, name + ": non installata", Toast.LENGTH_SHORT).show();
                } else {
                    open(launch);
                }
            });
        }
        addRow("altre app", 0, true, this::showAll);

        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private Intent findLaunchIntent(PackageManager pm, Entry e) {
        for (String p : e.packages) {
            Intent i = pm.getLaunchIntentForPackage(p);
            if (i != null) {
                return i;
            }
        }
        // nessun pacchetto noto: cerca un'app il cui nome contiene la parola chiave
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(Locale.ITALIAN);
            if (label.contains(e.keyword) && !ri.activityInfo.packageName.equals(getPackageName())) {
                return pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- tutte le altre app

    private void showAll() {
        showingAll = true;
        list.removeAllViews();
        list.setGravity(Gravity.TOP);

        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);

        // pacchetti già presenti nella lista principale: qui non servono
        Set<String> inHome = new HashSet<>();
        for (Entry e : entries) {
            Intent li = findLaunchIntent(pm, e);
            if (li != null && li.getComponent() != null) {
                inHome.add(li.getComponent().getPackageName());
            }
        }

        final List<String[]> items = new ArrayList<>(); // {nome, pacchetto}
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            String label = String.valueOf(ri.loadLabel(pm));
            if (pkg.equals(getPackageName()) || inHome.contains(pkg) || isHidden(pkg, label)
                    || !seen.add(pkg)) {
                continue;
            }
            items.add(new String[] { label, pkg });
        }
        final Collator collator = Collator.getInstance(Locale.ITALIAN);
        Collections.sort(items, (a, b) -> collator.compare(a[0], b[0]));

        addRow("indietro", -1, true, this::showMain);
        for (String[] item : items) {
            final String pkg = item[1];
            addRow(item[0].toLowerCase(Locale.ITALIAN), -1, true, () -> {
                Intent li = pm.getLaunchIntentForPackage(pkg);
                if (li != null) {
                    open(li);
                }
            });
        }
        scroll.post(() -> scroll.scrollTo(0, 0));
    }

    private boolean isHidden(String pkg, String label) {
        for (String p : HIDDEN_PACKAGES) {
            if (p.equals(pkg)) {
                return true;
            }
        }
        String l = label.toLowerCase(Locale.ITALIAN);
        for (String w : HIDDEN_WORDS) {
            if (l.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- utilità

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "impossibile aprire", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * iconRes > 0: icona + nome; iconRes == 0: spazio vuoto al posto dell'icona; iconRes < 0: solo nome.
     */
    private void addRow(String label, int iconRes, boolean available, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setBackgroundResource(selectableBackground());

        if (iconRes > 0) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(iconRes);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(iv, new LinearLayout.LayoutParams(dp(40), dp(40)));
        } else if (iconRes == 0) {
            row.addView(new View(this), new LinearLayout.LayoutParams(dp(40), dp(40)));
        }

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(INK);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tv.setPadding(iconRes >= 0 ? dp(20) : 0, 0, 0, 0);
        row.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setAlpha(available ? 1f : 0.35f);
        row.setOnClickListener(v -> action.run());

        list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int selectableBackground() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
