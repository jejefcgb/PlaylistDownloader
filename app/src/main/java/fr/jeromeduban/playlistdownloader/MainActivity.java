package fr.jeromeduban.playlistdownloader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.QueueProcessingType;
import com.pnikosis.materialishprogress.ProgressWheel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import fr.jeromeduban.playlistdownloader.objects.Item;
import fr.jeromeduban.playlistdownloader.objects.Playlist;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static fr.jeromeduban.playlistdownloader.Utils.generateUrl;
import static fr.jeromeduban.playlistdownloader.Utils.generateUrlVideoID;
import static fr.jeromeduban.playlistdownloader.Utils.parsePlaylist;

//TODO Don't wait to received the whole playlist before displaying songs
//TODO Add permissions management

public class MainActivity extends AppCompatActivity {

    private static final int WRITE_PERMISSION = 100;


    protected static String KEY = "AIzaSyCAsga_OKjW0350A0msLolXm6-B0769unc";
    protected static int maxResults = 10;

    private OkHttpClient client;

    private Activity a;

    @BindView(R.id.button)
    Button button;

    @BindView(R.id.container)
    LinearLayout container;

    @BindView(R.id.playlist_url)
    EditText playlistUrlET;

    @BindView(R.id.progress_wheel)
    ProgressWheel progressWheel;

    @BindView(R.id.layout_download)
    View layoutDownload;

    @BindView(R.id.layout_download_all)
    View layoutDownloadAll;

    private ArrayList<Playlist> list = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        a = this;
        client = new OkHttpClient();

        // Wakelock
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //FIXME

        // Initialize ImageLoader
        ImageLoaderConfiguration.Builder config = new ImageLoaderConfiguration.Builder(this);
        config.threadPriority(Thread.NORM_PRIORITY - 2);
        config.denyCacheImageMultipleSizesInMemory();
        config.diskCacheFileNameGenerator(new Md5FileNameGenerator());
        config.diskCacheSize(50 * 1024 * 1024); // 50 MiB
        config.tasksProcessingOrder(QueueProcessingType.LIFO);

//        if (BuildConfig.DEBUG) {
//            config.writeDebugLogs();
//        }

        ImageLoader.getInstance().init(config.build());


        checkPermission();

    }

    private boolean checkPermission() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(a,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(a,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                new MaterialDialog.Builder(this)
                        .title(R.string.app_name)
                        .content(R.string.permission_explanation)
                        .positiveText("Accepter")
                        .negativeText("Refuser")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                ActivityCompat.requestPermissions(a,
                                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                        WRITE_PERMISSION);
                            }
                        })
                        .show();



            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(a,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        WRITE_PERMISSION);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }else {
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case WRITE_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(a, "Nickel !", Toast.LENGTH_SHORT).show();

                } else {
                    Toast.makeText(a, "Tu n'aurais pas du faire ça !", Toast.LENGTH_SHORT).show();
                }
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    /**
     * Will be called when the user clicks on Start
     */
    @OnClick(R.id.button)
    public void getPlaylist() {

        progressWheel.setVisibility(View.VISIBLE);

        // Parse playlist from youtube link
        String url = playlistUrlET.getText().toString().trim();
        String id = url.split("list=")[1].split("&")[0];
        LogHelper.d(id);

        if (id.length() != 34) { //TODO Check if id length is always 34
            LogHelper.d("id might be wrong");
            Toast.makeText(this, "La playlist renseignée est peut être incorrecte", Toast.LENGTH_SHORT).show();
        }

        getPlaylistItems(id, null);
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(playlistUrlET.getWindowToken(), 0);
    }

    /**
     * Get all playlist videos information
     *
     * @param id Id of the playlist
     */
    private void getPlaylistItems(final String id, final String pageToken) {

        String url;
        if (pageToken == null) {
            url = generateUrl(id);
        } else {
            url = generateUrl(id, pageToken);
        }


        LogHelper.i("Getting videos from : " + url);

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {


            @Override
            public void onFailure(Call call, IOException e) {
                LogHelper.e(e.getMessage(), e);
                getPlaylistItems(id,pageToken); //TODO limit retries
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                LogHelper.d("Got response from " + call.request().url());

                if (!response.isSuccessful()) {
                    LogHelper.d(String.valueOf(response.code()));
                    Utils.ToastOnUIThread(a, "Une erreur réseau est survenue");
                    if (response.code() == 404) {
                        LogHelper.d("Error 404");
                    } else {
                        throw new IOException("Unexpected code " + response);
                    }
                } else {
                    Playlist pl = parsePlaylist(response.body().string());

                    if (pl != null) {

                        list.add(pl);

                        // If there are another page, get next playlist items
                        if (pl.nextPageToken != null) {
                            getPlaylistItems(id, pl.nextPageToken);
                        } else {
                            playlistCallback(list);
                        }
                    } else {
                        LogHelper.d("PL null");
                    }
                }
            }
        });
    }

    /**
     * Get videos' data
     *
     * @param list
     */
    private void playlistCallback(ArrayList<Playlist> list) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        a.runOnUiThread(new Runnable() {
            public void run() {
                layoutDownload.setVisibility(View.GONE);
                layoutDownloadAll.setVisibility(View.VISIBLE);
                progressWheel.setVisibility(View.GONE);
            }
        });

        for (Playlist playList : list) {
            for (final Item item : playList.items) {
                final String call = generateUrlVideoID(item.contentDetails.videoId);

                final Request request = new Request.Builder()
                        .url(call)
                        .build();

                client.newCall(request).enqueue(new Callback() {

                    @Override
                    public void onFailure(Call call, IOException e) {
                        LogHelper.e(e.getMessage(), e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {

                        final Playlist playlist = parsePlaylist(response.body().string());

                        if (playlist != null) {
                            mainHandler.post(new Runnable() {

                                @Override
                                public void run() {
                                    if (playlist.items != null && playlist.items.size() > 0) {
                                        displayCard(playlist.items.get(0));
                                    } else {
                                        LogHelper.i("Vidéo supprimée");
                                    }
                                }
                            });
                        }
                    }
                });
            }
        }
    }

    private void displayCard(Item item) {

        final String title = item.snippet.title;
        String url = item.snippet.thumbnails.medium.url;

        // Load Image
        ImageLoader imageLoader = ImageLoader.getInstance();
        LayoutInflater in = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View card = in.inflate(R.layout.card, container, false);
        card.setTag(item);
        ImageView iv = (ImageView) card.findViewById(R.id.thumbnail);
        imageLoader.displayImage(url, iv);

        // Display video Name
        TextView videoNameTV = (TextView) card.findViewById(R.id.video_name);
        videoNameTV.setText(title);

        // Try to guess artist & song name
        String[] parts = title.split("-");

        TextView songArtistTV = (TextView) card.findViewById(R.id.song_artist);
        songArtistTV.setText(parts[0].trim());

        if (parts.length > 1){
            String songTitle = parts[1].contains("(") ? parts[1].split("\\(")[0]:parts[1];
            TextView songTitleTV = (TextView) card.findViewById(R.id.song_title);
            songTitleTV.setText(songTitle.trim());
        }


        // Download management
        ProgressBar pb = (ProgressBar) card.findViewById(R.id.progressBar);
        if (new File(Environment.getExternalStorageDirectory().getPath() + "/PlaylistDownloader", title + ".mp3").exists()){
            pb.setVisibility(View.VISIBLE);
            pb.setProgress(100);
        }else{
            pb.setVisibility(View.GONE);
        }

        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
               return downloadSong(v);
            }
        });

        container.addView(card);
    }

    @OnClick(R.id.button_download_all)
    void downloadAll(){

        final int childcount = container.getChildCount();
        for (int i = 0; i < childcount; i++) {
            View v = container.getChildAt(i);
            downloadSong(v);
        }
    }

    private boolean downloadSong(View v){
        if (checkPermission()){

            TextView videoNameTV = (TextView) v.findViewById(R.id.video_name);
            String title = videoNameTV.getText().toString().trim();

            if (!new File(Environment.getExternalStorageDirectory().getPath() + "/PlaylistDownloader", title + ".mp3").exists()){
                final DownloadTask downloadTask = new DownloadTask(MainActivity.this, v);
                Item item = (Item)v.getTag();
                downloadTask.execute(item.id,title);
            }

        }else{
            Utils.ToastOnUIThread(a, "Impossible de télécharger ce fichier tant que l'autorisation d'écrire sur le téléphone n'aura pas été donnée.");
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
