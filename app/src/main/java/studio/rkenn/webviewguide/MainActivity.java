package studio.rkenn.webviewguide;

import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static android.os.Environment.getExternalStoragePublicDirectory;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private EditText editText;
    private Button sendButton;
    private String testJS = "";
    private String filepath = "";
    @SuppressLint({"SetJavaScriptEnabled", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkDownloadPermission();
//Test Area
        editText = findViewById(R.id.edit_text);
        sendButton = findViewById(R.id.send_button);

        sendButton.setOnClickListener(v -> {
            testJS = editText.getText().toString();

            String command = testJS;
            webView.loadUrl("javascript:" + command);
        });
        Intent openFileIntent = new Intent(Intent.ACTION_VIEW);
        openFileIntent.setDataAndType(Uri.parse(filepath), "*/*");

// Create a chooser with only file managers
        Intent chooserIntent = Intent.createChooser(openFileIntent, "Open Downloads with");
        chooserIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"*/*"});
        chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        @SuppressLint("QueryPermissionsNeeded")
        List<ResolveInfo> fileManagerApps = getPackageManager().queryIntentActivities(chooserIntent, 0);
        List<Intent> fileManagerIntents = new ArrayList<>();
        for (ResolveInfo info : fileManagerApps) {
            Intent fileManagerIntent = new Intent(openFileIntent);
            fileManagerIntent.setPackage(info.activityInfo.packageName);
            fileManagerIntents.add(fileManagerIntent);
        }
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, fileManagerIntents.toArray(new Parcelable[]{}));

// Start the chooser
        startActivity(chooserIntent);

//Init WebView
        webView = findViewById(R.id.webView);
        //webView.setDownloadListener((DownloadListener) this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        //WebAppInterface jsInterface = new WebAppInterface(this);
        //webView.addJavascriptInterface(jsInterface, "AndroidInterface");
        // Set cache mode to enable caching of web pages
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Check if there is a saved session
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String email = preferences.getString("email", "");
                String password = preferences.getString("password", "");
                if (!email.isEmpty() && !password.isEmpty() && url.equals("https://chat.openai.com/")) {
                    // Automatically log in
                    view.loadUrl("javascript:document.getElementById('email').value='" + email + "';document.getElementById('password').value='" + password + "';document.forms[0].submit();");
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                //set limit to this function
                Calendar limit = Calendar.getInstance();
                limit.set(2024
                        , Calendar.MARCH, 24);

                if (Calendar.getInstance().before(limit)) { // March 23, 2023 00:00:00 UTC
                    if (url.startsWith("https://dropden.com")) {
                        // Allow URLs to proceed
                        Log.d("webView Function", "Allowed: "+url);
                        webView.loadUrl(url);
                        return false;
                    } else if (url.startsWith("javascript:")) {
                        // Allow JavaScript URLs to proceed
                        Log.d("webView Function", "JS: "+url);
                        webView.loadUrl(url);
                        return false;
                    } else {
                        Log.d("webView Function", "Ignored: "+url);
                        return true;
                    }
                } else {
                    // Limit exceeded, ignore the URL
                    Log.d("webView Function", "Ignored: "+String.valueOf(Calendar.getInstance()));
                    return true;
                }


            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                Log.d("Download", "You reached me with url: "+url);
                String filename;
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
                filename = URLUtil.guessFileName(url, null, MimeTypeMap.getFileExtensionFromUrl(url));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalPublicDir(DIRECTORY_DOWNLOADS, filename);
                    filepath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).toString();
                }
                else {
                    request.setDestinationInExternalFilesDir(MainActivity.this, DIRECTORY_DOWNLOADS, filename);
                    filepath = getExternalFilesDir(null).toString();
                }
                Log.d("Download", "Path: "+filepath);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(request);
                registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
            BroadcastReceiver onComplete = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Toast.makeText(MainActivity.this, "Resources Ready...", Toast.LENGTH_SHORT).show();
                    AlertDialog.Builder donediag = new AlertDialog.Builder(context);
                    donediag
                            .setTitle("File is Ready")
                            .setMessage("Locate your file now?")
                            .setPositiveButton("Start", (dialog, which) -> {
                                startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                                Intent run = new Intent(Intent.ACTION_VIEW);
                                run.setDataAndType(Uri.parse(String.valueOf(getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))),"*/*");    // or use * /*
                                //startActivity(Intent.createChooser(run, "Open in File Explorer"));
                                Intent openFileIntent = new Intent(Intent.ACTION_VIEW);
                                openFileIntent.setDataAndType(Uri.parse(filepath), "application/apk");

// Create a chooser with only file managers
                                Intent chooserIntent = Intent.createChooser(openFileIntent, "Open file with");
                                chooserIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"*/*"});
                                chooserIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                                @SuppressLint("QueryPermissionsNeeded")
                                List<ResolveInfo> fileManagerApps = getPackageManager().queryIntentActivities(chooserIntent, 0);
                                List<Intent> fileManagerIntents = new ArrayList<>();
                                for (ResolveInfo info : fileManagerApps) {
                                    Intent fileManagerIntent = new Intent(openFileIntent);
                                    fileManagerIntent.setPackage(info.activityInfo.packageName);
                                    fileManagerIntents.add(fileManagerIntent);
                                }
                                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, fileManagerIntents.toArray(new Parcelable[]{}));

// Start the chooser
                                startActivity(chooserIntent);
                            })
                            .show();
                }
            };

        });
        webView.loadUrl("https://dropden.com/e30a68511eb2");
        // document.querySelector(".btn.btn-sm.btn-default").click() download all
        Button down_all = findViewById(R.id.down_all);
        down_all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                webView.loadUrl("javascript: document.querySelector(\".btn.btn-sm.btn-default\").click();");
            }
        });
        Button loadLinkButton = findViewById(R.id.loadLinkButton);
        loadLinkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboardManager.hasPrimaryClip()) {
                    ClipData.Item item = clipboardManager.getPrimaryClip().getItemAt(0);
                    String link = item.getText().toString();
                    webView.loadUrl(link);
                }
            }
        });
    }
//gather new messages from WebView JS
    public class WebAppInterface {

        private Context context;

        public WebAppInterface(Context context) {
            this.context = context;
        }
        @JavascriptInterface
        public void onMessageUpdate(String message) {
            //receives from JS AndroidInterface.onMessageUpdate('message');
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }
//go to previous page
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
//AsyncTask not used
public class DownloadTask extends AsyncTask<String, Integer, String> {
    private Context mContext;
    private PowerManager.WakeLock mWakeLock;

    public DownloadTask(Context context) {
        mContext = context;
    }

    @Override
    protected String doInBackground(String... sUrl) {
        InputStream input = null;
        OutputStream output = null;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(sUrl[0]);
            connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "Server returned HTTP " + connection.getResponseCode()
                        + " " + connection.getResponseMessage();
            }

            int fileLength = connection.getContentLength();

            input = connection.getInputStream();
            output = new FileOutputStream(Environment.getExternalStorageDirectory()
                    + "/downloadedfile.jpg");

            byte data[] = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                if (isCancelled()) {
                    input.close();
                    return null;
                }
                total += count;
                if (fileLength > 0)
                    publishProgress((int) (total * 100 / fileLength));
                output.write(data, 0, count);
            }
        } catch (Exception e) {
            return e.toString();
        } finally {
            try {
                if (output != null)
                    output.close();
                if (input != null)
                    input.close();
            } catch (IOException ignored) {
            }

            if (connection != null)
                connection.disconnect();
        }
        return null;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getClass().getName());
        mWakeLock.acquire();
    }

    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);
        // Update progress bar with percentage
    }

    @Override
    protected void onPostExecute(String result) {
        mWakeLock.release();
        if (result != null) {
            // Show error message
        } else {
            // Show success message
        }
    }
}
//Check Permission
    private void checkDownloadPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(MainActivity.this, "Write External Storage permission allows us to save files. Please allow this permission in App Settings.", Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
    }

}