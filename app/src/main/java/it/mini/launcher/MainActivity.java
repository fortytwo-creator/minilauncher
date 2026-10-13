package it.mini.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Schermata Home minimale: solo una lista con icona a sinistra e nome a destra.
 * Niente orologio, niente barra delle app in basso.
 * Lo sfondo è quello impostato nel telefono.
 *
 * Nella lista principale:
 *  - tieni premuta una riga e trascinala per cambiare l'ordine;
 *  - tieni premuta una riga e rilasciala senza spostarla per rinominare l'app.
 * L'ordine e i nomi vengono ricordati.
 */
public class MainActivity extends Activity {

    private static final int INK = Color.parseColor("#2B2B2B");

    private static final class Entry {
        final String label;      // nome originale (serve anche come chiave per salvare ordine e nome)
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

    // ===== LISTA DELLE APP (ordine iniziale) =====
    // Dopo il primo avvio l'ordine lo decidi tu trascinando le righe.
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

    // pallini rossi delle notifiche, uno per riga della lista principale
    private final Map<Entry, View> dots = new LinkedHashMap<>();

    // stato del trascinamento
    private View draggedRow;
    private String orderAtDragStart = "";

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
        NotificationService.onChange = this::updateDots;
        updateDots();
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotificationService.onChange = null;
    }

    // ---------------------------------------------------------------- pallino rosso

    private View makeDot() {
        View dot = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.parseColor("#E03131"));
        dot.setBackground(shape);
        dot.setVisibility(View.INVISIBLE);
        return dot;
    }

    /** Pacchetti le cui notifiche accendono il pallino di questa riga. */
    private List<String> dotPackages(Entry e) {
        List<String> result = new ArrayList<>();
        Collections.addAll(result, e.packages);
        if (e.label.equals("telefono")) {
            // le chiamate perse vengono pubblicate dal sistema, non sempre dall'app telefono
            result.add("com.android.server.telecom");
            result.add("com.android.incallui");
        }
        return result;
    }

    private void updateDots() {
        for (Map.Entry<Entry, View> item : dots.entrySet()) {
            boolean on = false;
            synchronized (NotificationService.active) {
                for (String pkg : dotPackages(item.getKey())) {
                    if (NotificationService.active.contains(pkg)) {
                        on = true;
                        break;
                    }
                }
            }
            item.getValue().setVisibility(on ? View.VISIBLE : View.INVISIBLE);
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

    // ---------------------------------------------------------------- ordine e nomi salvati

    private SharedPreferences prefs() {
        return getSharedPreferences("mini_launcher", MODE_PRIVATE);
    }

    /** Le app nell'ordine scelto dall'utente; quelle nuove vanno in fondo. */
    private List<Entry> orderedEntries() {
        List<Entry> result = new ArrayList<>();
        String saved = prefs().getString("order", "");
        if (!saved.isEmpty()) {
            for (String key : saved.split(",")) {
                for (Entry e : entries) {
                    if (e.label.equals(key) && !result.contains(e)) {
                        result.add(e);
                    }
                }
            }
        }
        for (Entry e : entries) {
            if (!result.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }

    private String displayName(Entry e) {
        return prefs().getString("name_" + e.label, e.label);
    }

    private void rename(Entry e, String text) {
        String t = text.trim();
        SharedPreferences.Editor ed = prefs().edit();
        if (t.isEmpty()) {
            ed.remove("name_" + e.label);   // torna al nome originale
        } else {
            ed.putString("name_" + e.label, t);
        }
        ed.apply();
        showMain();
    }

    private void showRenameDialog(final Entry e) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(e.label);
        input.setText(displayName(e));
        input.setSelectAllOnFocus(true);

        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("rinomina")
                .setView(box)
                .setPositiveButton("salva", (d, w) -> rename(e, input.getText().toString()))
                .setNeutralButton("nome originale", (d, w) -> rename(e, ""))
                .setNegativeButton("annulla", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    // ---------------------------------------------------------------- lista principale

    private void showMain() {
        showingAll = false;
        list.removeAllViews();
        list.setGravity(Gravity.CENTER_VERTICAL);
        // più spazio sotto che sopra: la lista resta centrata, ma un po' più in alto
        list.setPadding(dp(36), dp(24), dp(24), dp(48));
        dots.clear();

        PackageManager pm = getPackageManager();
        for (Entry e : orderedEntries()) {
            final Entry entry = e;
            final Intent launch = findLaunchIntent(pm, e);
            final String name = displayName(e);
            LinearLayout row = addRow(name, e.icon, launch != null, () -> {
                if (launch == null) {
                    Toast.makeText(this, name + ": non installata", Toast.LENGTH_SHORT).show();
                } else {
                    open(launch);
                }
            });
            row.setTag(entry);
            View dot = makeDot();
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(9), dp(9));
            dotParams.leftMargin = dp(10);
            row.addView(dot, dotParams);
            dots.put(entry, dot);
            row.setOnLongClickListener(v -> {
                startDrag(v, entry);
                return true;
            });
            row.setOnDragListener(this::onRowDrag);
        }
        // "altre app": area invisibile ma toccabile, subito sotto l'ultima voce
        LinearLayout hidden = addRow("altre app", 0, true, this::showAll);
        hidden.setMinimumHeight(dp(64));
        hidden.setAlpha(0f);

        updateDots();
    }

    // ---------------------------------------------------------------- trascinamento

    private void startDrag(View row, Entry e) {
        draggedRow = row;
        orderAtDragStart = currentRowOrder();
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        ClipData data = ClipData.newPlainText("app", e.label);
        row.startDragAndDrop(data, new View.DragShadowBuilder(row), e, 0);
    }

    private boolean onRowDrag(View v, DragEvent ev) {
        if (!(ev.getLocalState() instanceof Entry)) {
            return false;
        }
        switch (ev.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (v == draggedRow) {
                    v.setAlpha(0.35f);          // la riga che stai spostando diventa sbiadita
                }
                return true;
            case DragEvent.ACTION_DRAG_ENTERED:
                if (draggedRow != null && v != draggedRow) {
                    int to = list.indexOfChild(v);
                    list.removeView(draggedRow);
                    list.addView(draggedRow, to);   // le righe si spostano mentre trascini
                }
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                if (v == draggedRow) {
                    finishDrag(ev.getResult());
                }
                return true;
            default:
                return true;
        }
    }

    private void finishDrag(boolean dropped) {
        final Entry entry = draggedRow != null ? (Entry) draggedRow.getTag() : null;
        String now = currentRowOrder();
        final boolean changed = !now.equals(orderAtDragStart);
        if (changed) {
            prefs().edit().putString("order", now).apply();
        }
        draggedRow = null;
        // si ricostruisce la lista dopo la fine del trascinamento, non durante
        list.post(() -> {
            showMain();
            if (!changed && dropped && entry != null) {
                showRenameDialog(entry);        // rilasciata senza spostarla: rinomina
            }
        });
    }

    private String currentRowOrder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.getChildCount(); i++) {
            Object tag = list.getChildAt(i).getTag();
            if (tag instanceof Entry) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(((Entry) tag).label);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- ricerca delle app

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
        list.setPadding(dp(36), dp(24), dp(24), dp(32));
        dots.clear();

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
    private LinearLayout addRow(String label, int iconRes, boolean available, final Runnable action) {
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
        return row;
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
