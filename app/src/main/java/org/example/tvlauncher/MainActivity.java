package org.example.tvlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
  final ArrayList<App> apps = new ArrayList<>();
  final ArrayList<Watch> watches = new ArrayList<>();
  android.content.SharedPreferences prefs;
  Home home;
  Handler handler = new Handler();
  boolean homeVisible = false;
  android.database.ContentObserver watchObserver;
  Runnable refreshWatch =
      () -> {
        if (homeVisible) {
          loadWatch();
          if (home != null) home.invalidate();
        }
      };
  int row = 0, col = 0;
  String[][] catalog = {
    {"com.google.android.youtube.tv", "YouTube", "Vídeos e música"},
    {"com.netflix.ninja", "Netflix", "Filmes e séries"},
    {"com.globo.globotv", "Globoplay", "Conteúdo brasileiro"},
    {"com.crunchyroll.crunchyroid", "Crunchyroll", "Anime"},
    {"com.amazon.amazonvideo.livingroom", "Prime Video", "Filmes e séries"},
    {"com.disney.disneyplus", "Disney+", "Filmes e séries"},
    {"com.wbd.stream", "HBO Max", "Filmes e séries"},
    {"com.spotify.tv.android", "Spotify", "Música e podcasts"},
    {"tv.twitch.android.app", "Twitch", "Ao vivo"},
    {"com.apple.atve.androidtv.appletv", "Apple TV", "Filmes e séries"}
  };

  static class App {
    String pkg, name, desc;
    Bitmap icon, banner;

    App(String p, String n, String d) {
      pkg = p;
      name = n;
      desc = d;
    }
  }

  static class Watch {
    App app;
    String title, art;
    Intent intent;
    long pos, duration, last;
    int kind;

    Watch(App a, String t, Intent i, long p, long d) {
      app = a;
      title = t;
      intent = i;
      pos = p;
      duration = d;
    }
  }

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().getDecorView().setSystemUiVisibility(5894);
    prefs = getSharedPreferences("home", 0);
    loadApps();
    home = new Home();
    setContentView(home);
    watchObserver =
        new android.database.ContentObserver(handler) {
          @Override
          public void onChange(boolean selfChange) {
            if (homeVisible) {
              handler.removeCallbacks(refreshWatch);
              handler.postDelayed(refreshWatch, 500);
            }
          }
        };
    try {
      getContentResolver()
          .registerContentObserver(
              Uri.parse("content://android.media.tv/watch_next_program"), true, watchObserver);
    } catch (Exception ignored) {
    }
    if (checkSelfPermission("android.permission.READ_TV_LISTINGS")
        != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {"android.permission.READ_TV_LISTINGS"}, 11);
  }

  @Override
  public void onResume() {
    super.onResume();
    homeVisible = true;
    sortApps();
    loadWatch();
    if (home != null) home.invalidate();
    handler.post(tick);
  }

  @Override
  public void onPause() {
    super.onPause();
    homeVisible = false;
    handler.removeCallbacks(refreshWatch);
    handler.removeCallbacks(tick);
  }

  @Override
  public void onDestroy() {
    if (watchObserver != null) getContentResolver().unregisterContentObserver(watchObserver);
    handler.removeCallbacks(refreshWatch);
    images.shutdownNow();
    super.onDestroy();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] result) {
    super.onRequestPermissionsResult(requestCode, permissions, result);
    if (requestCode == 11 && result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED) {
      loadWatch();
      home.invalidate();
    }
  }

  Runnable tick =
      new Runnable() {
        public void run() {
          if (home != null) home.invalidate();
          handler.postDelayed(this, 60000);
        }
      };

  void loadApps() {
    apps.clear();
    for (String[] c : catalog)
      try {
        ApplicationInfo ai = getPackageManager().getApplicationInfo(c[0], 0);
        if (!ai.enabled) continue;
        App a = new App(c[0], c[1], c[2]);
        Drawable dr = getPackageManager().getApplicationIcon(ai);
        a.icon = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(a.icon);
        dr.setBounds(0, 0, 160, 160);
        dr.draw(cv);
        try {
          Drawable banner = getPackageManager().getApplicationBanner(ai);
          if (banner == null) {
            Intent lean = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (lean != null) {
              ResolveInfo resolved = getPackageManager().resolveActivity(lean, 0);
              if (resolved != null) banner = resolved.activityInfo.loadBanner(getPackageManager());
            }
          }
          if (banner != null) {
            a.banner = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            Canvas artCanvas = new Canvas(a.banner);
            banner.setBounds(0, 0, 320, 180);
            banner.draw(artCanvas);
          }
        } catch (Exception ignored) {
        }
        apps.add(a);
      } catch (Exception ignored) {
      }
  }

  App find(String pkg) {
    for (App a : apps) if (a.pkg.equals(pkg)) return a;
    return null;
  }

  final java.util.concurrent.ExecutorService images =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  final Map<String, Bitmap> artwork =
      Collections.synchronizedMap(new LinkedHashMap<String, Bitmap>());

  void loadWatch() {
    watches.clear();
    try (Cursor c =
        getContentResolver()
            .query(
                Uri.parse("content://android.media.tv/watch_next_program"),
                new String[] {
                  "package_name",
                  "title",
                  "intent_uri",
                  "playback_position_millis",
                  "duration_millis",
                  "last_engagement_time_utc_millis",
                  "poster_art_uri",
                  "watch_next_type"
                },
                null,
                null,
                null)) {
      if (c != null)
        while (c.moveToNext()) {
          App a = find(c.getString(0));
          String title = c.getString(1), uri = c.getString(2);
          if (a == null || title == null || uri == null || (c.getInt(7) != 0 && c.getInt(7) != 1))
            continue;
          Intent in = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
          in.setPackage(a.pkg);
          if (in.getComponent() != null && !in.getComponent().getPackageName().equals(a.pkg))
            in.setComponent(null);
          in.setSelector(null);
          in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          Watch w = new Watch(a, title, in, c.getLong(3), c.getLong(4));
          w.last = c.getLong(5);
          w.art = c.getString(6);
          w.kind = c.getInt(7);
          watches.add(w);
        }
    } catch (Exception ex) {
      android.util.Log.w("TvLauncher", "watch_next_query_failed", ex);
    }
    Collections.sort(watches, (a, b) -> Long.compare(b.last, a.last));
    java.util.HashSet<String> seenWatch = new java.util.HashSet<>();
    for (java.util.Iterator<Watch> it = watches.iterator(); it.hasNext(); ) {
      Watch item = it.next();
      if (!seenWatch.add(item.app.pkg + "|" + item.title + "|" + item.intent.getDataString()))
        it.remove();
    }
    while (watches.size() > 10) watches.remove(watches.size() - 1);
    for (Watch w : watches) loadArt(w.art);
  }

  void loadArt(String uri) {
    if (uri == null || artwork.containsKey(uri)) return;
    artwork.put(uri, null);
    images.execute(
        () -> {
          try {
            java.io.InputStream stream;
            Uri parsed = Uri.parse(uri);
            if ("content".equals(parsed.getScheme()))
              stream = getContentResolver().openInputStream(parsed);
            else if ("https".equals(parsed.getScheme())) {
              java.net.HttpURLConnection conn =
                  (java.net.HttpURLConnection) new java.net.URL(uri).openConnection();
              conn.setConnectTimeout(4000);
              conn.setReadTimeout(4000);
              conn.setInstanceFollowRedirects(false);
              stream = conn.getInputStream();
            } else return;
            if (stream == null) return;
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.InputStream input = stream) {
              byte[] buffer = new byte[8192];
              int total = 0, n;
              while ((n = input.read(buffer)) != -1) {
                total += n;
                if (total > 3 * 1024 * 1024) return;
                bytes.write(buffer, 0, n);
              }
            }
            byte[] data = bytes.toByteArray();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            int sample = 1;
            while (opts.outWidth / sample > 800 || opts.outHeight / sample > 500) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            synchronized (artwork) {
              while (artwork.size() > 12) {
                String first = artwork.keySet().iterator().next();
                artwork.remove(first);
              }
              artwork.put(uri, bitmap);
            }
            handler.post(
                () -> {
                  if (home != null) home.invalidate();
                });
          } catch (Exception ignored) {
          }
        });
  }

  void sortApps() {
    String selected = null;
    if (home != null && row == 1 && col < apps.size()) selected = apps.get(col).pkg;
    Collections.sort(
        apps,
        (a, b) -> {
          int usage =
              Integer.compare(prefs.getInt("uses_" + b.pkg, 0), prefs.getInt("uses_" + a.pkg, 0));
          if (usage != 0) return usage;
          return baseOrder(a.pkg) - baseOrder(b.pkg);
        });
    if (selected != null)
      for (int i = 0; i < apps.size(); i++)
        if (apps.get(i).pkg.equals(selected)) {
          col = i;
          home.carouselIndex = i;
          break;
        }
  }

  int baseOrder(String pkg) {
    for (int i = 0; i < catalog.length; i++) if (catalog[i][0].equals(pkg)) return i;
    return 99;
  }

  void remember(App a) {
    prefs.edit().putInt("uses_" + a.pkg, prefs.getInt("uses_" + a.pkg, 0) + 1).apply();
  }

  void launch(App a) {
    if (a == null) return;
    try {
      Intent in = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
      if (in == null) in = getPackageManager().getLaunchIntentForPackage(a.pkg);
      if (in == null) {
        toast("Este app não tem uma tela de abertura disponível.");
        return;
      }
      remember(a);
      startActivity(in);
    } catch (Exception e) {
      toast("Não foi possível abrir " + a.name);
    }
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  void settingsPanel() {
    new AlertDialog.Builder(this)
        .setTitle("Configurações")
        .setItems(
            new String[] {"Configurar perfil", "Configurações da TV"},
            (d, i) -> {
              if (i == 0) profilePanel();
              else
                try {
                  startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ex) {
                  toast("Configurações indisponíveis");
                }
            })
        .setNegativeButton("Voltar", null)
        .show();
  }

  void profilePanel() {
    final android.widget.EditText name = new android.widget.EditText(this);
    name.setSingleLine(true);
    name.setText(prefs.getString("profile_name", "Perfil"));
    name.setSelectAllOnFocus(true);
    name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(24)});
    new AlertDialog.Builder(this)
        .setTitle("Nome do perfil nesta TV")
        .setMessage("Este nome aparece na tela inicial. As contas dos apps não mudam.")
        .setView(name)
        .setPositiveButton(
            "Salvar",
            (d, i) -> {
              String value = name.getText().toString().trim();
              if (!value.isEmpty()) {
                prefs.edit().putString("profile_name", value).apply();
                home.invalidate();
              }
            })
        .setNegativeButton("Voltar", null)
        .show();
  }

  int count(int r) {
    if (r == 0) return 2;
    if (r == 1) return apps.size();
    if (r == 2) return watches.size();
    return 0;
  }

  @Override
  public boolean onKeyDown(int key, KeyEvent e) {
    if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
      int n = count(row);
      int direction = key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
      if (n > 0) {
        col = (col + direction + n) % n;
        home.ensureWindow(row, col, direction);
      }
      home.invalidate();
      return true;
    }
    if (key == KeyEvent.KEYCODE_DPAD_UP) {
      row = Math.max(0, row - 1);
      col = Math.max(0, Math.min(col, count(row) - 1));
      home.invalidate();
      return true;
    }
    if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
      if (row == 0) row = 1;
      else if (row == 1 && !watches.isEmpty()) row = 2;
      col = Math.max(0, Math.min(col, count(row) - 1));
      home.invalidate();
      return true;
    }
    if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) {
      if (e.getRepeatCount() == 0) activate();
      return true;
    }
    if (key == KeyEvent.KEYCODE_MENU) {
      settingsPanel();
      return true;
    }
    if (key == KeyEvent.KEYCODE_BACK) {
      row = 1;
      col = 0;
      home.invalidate();
      return true;
    }
    return super.onKeyDown(key, e);
  }

  @Override
  public boolean onKeyUp(int key, KeyEvent e) {
    if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) return true;
    return super.onKeyUp(key, e);
  }

  @Override
  public boolean onKeyLongPress(int key, KeyEvent e) {
    return false;
  }

  void activate() {
    if (row == 0) {
      if (col == 0) settingsPanel();
      else profilePanel();
    } else if (row == 1) {
      if (col < apps.size()) launch(apps.get(col));
    } else if (row == 2 && col < watches.size()) {
      Watch w = watches.get(col);
      try {
        remember(w.app);
        startActivity(w.intent);
      } catch (Exception ex) {
        launch(w.app);
      }
    }
  }

  class Home extends View {
    Paint p = new Paint(3);
    Canvas c;
    float scale = 1, contentOffset = 0;
    int carouselIndex = 0, watchCarouselIndex = 0, appWindowStart = 0, watchWindowStart = 0;
    int bg = Color.rgb(16, 17, 19),
        muted = Color.rgb(139, 143, 151),
        lime = Color.rgb(216, 200, 170);
    ArrayList<Hit> hits = new ArrayList<>();

    Home() {
      super(MainActivity.this);
      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();
      row = 1;
    }

    void ensureWindow(int rail, int selected, int direction) {
      int n = rail == 1 ? apps.size() : rail == 2 ? watches.size() : 0;
      if (n == 0) return;
      int capacity = Math.min(n, rail == 1 ? 5 : 3);
      int start = rail == 1 ? appWindowStart : watchWindowStart;
      start = ((start % n) + n) % n;
      int relative = (selected - start + n) % n;
      if (relative >= capacity)
        start = direction < 0 ? selected : (selected - capacity + 1 + n) % n;
      if (rail == 1) appWindowStart = start;
      else watchWindowStart = start;
    }

    void rect(float x, float y, float w, float h, int color, float radius) {
      p.setColor(color);
      p.setStyle(Paint.Style.FILL);
      c.drawRoundRect(x, y, x + w, y + h, radius, radius, p);
    }

    void text(String s, float x, float y, float size, int color, boolean bold) {
      p.setTypeface(
          bold ? Typeface.create("sans-serif-medium", 0) : Typeface.create("sans-serif", 0));
      p.setTextSize(size);
      p.setColor(color);
      c.drawText(s, x, y, p);
    }

    void center(String s, float x, float y, float size, int color) {
      p.setTypeface(Typeface.create("sans-serif", 0));
      p.setTextSize(size);
      p.setColor(color);
      c.drawText(s, x - p.measureText(s) / 2, y, p);
    }

    String fit(String s, float width, float size) {
      p.setTextSize(size);
      p.setTypeface(Typeface.create("sans-serif", 0));
      if (p.measureText(s) <= width) return s;
      while (s.length() > 1 && p.measureText(s + "…") > width) s = s.substring(0, s.length() - 1);
      return s + "…";
    }

    void icon(App a, float x, float y, float size) {
      if (a == null) return;
      int save = c.save();
      Path path = new Path();
      path.addRoundRect(
          new RectF(x, y, x + size, y + size), size * .2f, size * .2f, Path.Direction.CW);
      c.clipPath(path);
      p.setAlpha(255);
      c.drawBitmap(a.icon, null, new RectF(x, y, x + size, y + size), p);
      c.restoreToCount(save);
    }

    void focus(float x, float y, float w, float h, int r, int co) {
      hits.add(new Hit(new RectF(x, y + contentOffset, x + w, y + h + contentOffset), r, co));
      if (row == r && col == co) {
        p.setColor(lime);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        if (r == 1) {
          p.setStrokeWidth(1.6f);
          c.drawRoundRect(x - 2, y - 2, x + w + 2, y + h + 2, 8, 8, p);
        } else c.drawRoundRect(x - 3, y - 3, x + w + 3, y + h + 3, 15, 15, p);
        p.setStyle(Paint.Style.FILL);
      }
    }

    @Override
    public void onDraw(Canvas canvas) {
      c = canvas;
      scale = Math.min(getWidth() / 1280f, getHeight() / 720f);
      c.drawColor(bg);
      c.save();
      c.translate((getWidth() - 1280 * scale) / 2, (getHeight() - 720 * scale) / 2);
      c.scale(scale, scale);
      c.translate(-27, 0);
      hits.clear();
      int tone =
          appTone(
              apps.isEmpty()
                  ? ""
                  : apps.get(Math.min(row == 1 ? col : carouselIndex, apps.size() - 1)).pkg);
      p.setAlpha(255);
      p.setShader(
          new RadialGradient(
              880,
              -120,
              1150,
              new int[] {tone, Color.rgb(13, 17, 23)},
              new float[] {0f, 1f},
              Shader.TileMode.CLAMP));
      c.drawRect(27, 0, 1307, 720, p);
      p.setShader(null);
      String time = new SimpleDateFormat("HH:mm", new Locale("pt", "BR")).format(new Date());
      text(time, 1018, 34, 13, Color.rgb(210, 214, 219), false);
      String date =
          new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR")).format(new Date());
      text(date, 805, 34, 10, muted, false);
      sliders(1113, 19);
      focus(1102, 9, 40, 40, 0, 0);
      rect(1180, 14, 30, 30, Color.rgb(216, 203, 182), 15);
      String profile = prefs.getString("profile_name", "Perfil");
      center(
          profile.substring(0, 1).toUpperCase(new Locale("pt", "BR")),
          1195,
          34,
          11,
          Color.rgb(59, 51, 40));
      focus(1174, 9, 42, 40, 0, 1);

      contentOffset = 0;
      int selected = apps.isEmpty() ? 0 : Math.min(col, apps.size() - 1);
      if (row != 1) selected = carouselIndex;
      else carouselIndex = selected;
      ensureWindow(1, selected, 0);
      text("Aplicativos", 110, 324, 15, Color.rgb(222, 224, 226), false);
      if (!apps.isEmpty()) text((selected + 1) + " / " + apps.size(), 1172, 324, 10, muted, false);
      int clip = c.save();
      c.clipRect(102, 335, 1308, 461);
      int visible = Math.min(6, apps.size());
      for (int slot = 0; slot < visible; slot++) {
        int index = (appWindowStart + slot) % apps.size();
        App a = apps.get(index);
        float x = 110 + slot * 204;
        rect(x, 344, 184, 104, Color.rgb(26, 28, 34), 6);
        if (a.banner != null) {
          int save = c.save();
          Path mask = new Path();
          mask.addRoundRect(new RectF(x, 344, x + 184, 448), 6, 6, Path.Direction.CW);
          c.clipPath(mask);
          p.setAlpha(255);
          p.setFilterBitmap(true);
          c.drawBitmap(a.banner, null, new RectF(x, 344, x + 184, 448), p);
          c.restoreToCount(save);
        } else icon(a, x + 63, 367, 58);
        focus(x, 344, 184, 104, 1, index);
      }
      c.restoreToCount(clip);

      text("Continuar assistindo", 110, 507, 14, Color.rgb(222, 224, 226), false);
      int n = watches.size();
      if (n == 0) {
        rect(110, 528, 1114, 132, Color.argb(10, 255, 255, 255), 13);
        text(
            "Nenhum conteúdo recente disponibilizado pelos aplicativos.",
            130,
            587,
            12,
            Color.rgb(175, 181, 188),
            false);
        text(
            "A seção continua visível e será atualizada quando os apps compartilharem dados.",
            130,
            611,
            10,
            muted,
            false);
      } else {
        if (row == 2) watchCarouselIndex = Math.min(col, n - 1);
        watchCarouselIndex = Math.min(watchCarouselIndex, n - 1);
        ensureWindow(2, watchCarouselIndex, 0);
        text((watchCarouselIndex + 1) + " / " + n, 1172, 507, 10, muted, false);
        int railClip = c.save();
        c.clipRect(102, 520, 1308, 670);
        for (int slot = 0; slot < Math.min(4, n); slot++) {
          int i = (watchWindowStart + slot) % n;
          float x = 110 + slot * 336;
          Watch w = watches.get(i);
          rect(x, 528, 318, 132, Color.argb(18, 255, 255, 255), 13);
          int save = c.save();
          Path mask = new Path();
          mask.addRoundRect(new RectF(x, 528, x + 318, 660), 13, 13, Path.Direction.CW);
          c.clipPath(mask);
          Bitmap poster = w.art == null ? null : artwork.get(w.art);
          if (poster != null) {
            p.setAlpha(255);
            float sourceRatio = (float) poster.getWidth() / poster.getHeight(),
                targetRatio = 318f / 132;
            Rect source;
            if (sourceRatio > targetRatio) {
              int width = (int) (poster.getHeight() * targetRatio),
                  left = (poster.getWidth() - width) / 2;
              source = new Rect(left, 0, left + width, poster.getHeight());
            } else {
              int height = (int) (poster.getWidth() / targetRatio),
                  top = (poster.getHeight() - height) / 2;
              source = new Rect(0, top, poster.getWidth(), top + height);
            }
            c.drawBitmap(poster, source, new RectF(x, 528, x + 318, 660), p);
          }
          p.setShader(
              new LinearGradient(
                  x,
                  570,
                  x,
                  660,
                  new int[] {Color.TRANSPARENT, Color.argb(225, 5, 8, 13)},
                  null,
                  Shader.TileMode.CLAMP));
          rect(x, 528, 318, 132, Color.WHITE, 0);
          p.setShader(null);
          icon(w.app, x + 13, 541, 24);
          if (w.kind == 1)
            text("Próximo episódio", x + 218, 553, 9, Color.rgb(224, 228, 234), false);
          p.setColor(Color.argb(70, 0, 0, 0));
          c.drawCircle(x + 159, 589, 18, p);
          p.setColor(Color.argb(160, 255, 255, 255));
          p.setStyle(Paint.Style.STROKE);
          p.setStrokeWidth(1);
          c.drawCircle(x + 159, 589, 18, p);
          p.setStyle(Paint.Style.FILL);
          p.setColor(Color.WHITE);
          Path play = new Path();
          play.moveTo(x + 155, 583);
          play.lineTo(x + 155, 595);
          play.lineTo(x + 165, 589);
          play.close();
          c.drawPath(play, p);
          text(fit(w.title, 288, 13), x + 15, 640, 13, Color.WHITE, false);
          if (w.duration > 0 && w.pos > 0) {
            rect(x, 657, 318, 3, Color.argb(80, 255, 255, 255), 0);
            rect(x, 657, Math.min(318, 318f * w.pos / w.duration), 3, lime, 0);
          }
          c.restoreToCount(save);
          focus(x, 528, 318, 132, 2, i);
        }
        c.restoreToCount(railClip);
      }
      c.restore();
    }

    int appTone(String pkg) {
      if (pkg.contains("netflix")) return Color.rgb(78, 27, 42);
      if (pkg.contains("globo")) return Color.rgb(85, 40, 44);
      if (pkg.contains("crunch")) return Color.rgb(86, 57, 28);
      if (pkg.contains("youtube")) return Color.rgb(66, 34, 43);
      if (pkg.contains("amazon")) return Color.rgb(23, 63, 91);
      if (pkg.contains("disney")) return Color.rgb(17, 64, 76);
      if (pkg.contains("wbd") || pkg.contains("hbo")) return Color.rgb(60, 35, 94);
      if (pkg.contains("spotify")) return Color.rgb(26, 71, 49);
      if (pkg.contains("twitch")) return Color.rgb(64, 35, 92);
      return Color.rgb(39, 53, 73);
    }

    void sliders(float x, float y) {
      p.setColor(Color.rgb(178, 184, 189));
      p.setStrokeWidth(1.5f);
      p.setStyle(Paint.Style.STROKE);
      c.drawLine(x, y + 5, x + 21, y + 5, p);
      c.drawLine(x, y + 15, x + 21, y + 15, p);
      p.setStyle(Paint.Style.FILL);
      p.setColor(Color.rgb(21, 26, 30));
      c.drawCircle(x + 6, y + 5, 3, p);
      c.drawCircle(x + 15, y + 15, 3, p);
      p.setStyle(Paint.Style.STROKE);
      p.setColor(Color.rgb(178, 184, 189));
      c.drawCircle(x + 6, y + 5, 3, p);
      c.drawCircle(x + 15, y + 15, 3, p);
      p.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
      if (e.getAction() != MotionEvent.ACTION_UP) return true;
      float x = (e.getX() - (getWidth() - 1280 * scale) / 2) / scale + 27,
          y = (e.getY() - (getHeight() - 720 * scale) / 2) / scale;
      for (int i = hits.size() - 1; i >= 0; i--) {
        Hit h = hits.get(i);
        if (h.rect.contains(x, y)) {
          row = h.row;
          col = h.col;
          invalidate();
          activate();
          return true;
        }
      }
      return true;
    }
  }

  static class Hit {
    RectF rect;
    int row, col;

    Hit(RectF r, int a, int b) {
      rect = r;
      row = a;
      col = b;
    }
  }
}
