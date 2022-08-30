package az.inci.pricemonitor;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private boolean localMode;
    private int resultTimeout;
    private int connectionTimeout;

    private String localURL;
    private String remoteURL;
    private AlertDialog progressDialog;
    private AlertDialog.Builder messageDialogBuilder;
    private ImageView logo;
    private EditText barcodeEdit;
    private TextView barcodeText;
    private TextView invNameText;
    private TextView priceText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        enableStorageAccess();

        buildProgressDialog();
        buildMessageDialog();

        logo = findViewById(R.id.logo);

        barcodeEdit = findViewById(R.id.barcode_edit);
        barcodeText = findViewById(R.id.barcode);
        invNameText = findViewById(R.id.inv_name);
        priceText = findViewById(R.id.price);

        barcodeEdit.requestFocus();
        barcodeEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String barcode = s.toString();
                if (barcode.endsWith("\n")) {
                    getPriceData(barcode.trim());
                    barcodeEdit.setText("");
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if (preferences.contains("server"))
        {
            String serverIp = preferences.getString("server", "192.168.0.100");
            preferences.edit().putString("local_ip", serverIp).apply();
        }
        if (preferences.contains("port"))
        {
            String serverPort = preferences.getString("port", "8080");
            preferences.edit().putString("local_port", serverPort).apply();
        }

        String localIp = preferences.getString("local_ip","192.168.0.100");
        String localPort = preferences.getString("local_port","8080");

        String remoteIp = preferences.getString("remote_ip", "185.129.0.46");
        String remotePort = preferences.getString("remote_port", "8081");

        float textSize = Float.parseFloat(preferences.getString("text_size", "40"));

        barcodeText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        invNameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        priceText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize + 10);

        localURL = "http://" + localIp + ":" + localPort;
        remoteURL = "http://" + remoteIp + ":" + remotePort;

        localMode = preferences.getBoolean("local_mode", true);

        resultTimeout = Integer.parseInt(preferences.getString("result_timeout", "5"));
        connectionTimeout = Integer.parseInt(preferences.getString("connection_timeout", "5"));
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        menu.getItem(0).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });

        menu.getItem(1).setOnMenuItemClickListener(item -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Proqram versiyasını yenilə")
                    .setMessage("Dəyişiklikdən asılı olaraq məlumatlar silinə bilər. Yeniləmək istəyirsinizmi?")
                    .setNegativeButton("Bəli", (dialogInterface, i) ->
                            checkForNewVersion())
                    .setPositiveButton("Xeyr", null)
                    .create();

            dialog.show();
            return true;
        });

        return super.onCreateOptionsMenu(menu);
    }

    private void checkForNewVersion()
    {
        showProgressDialog(true);
        new Thread(() ->
        {
            try
            {
                URL url = new URL(remoteURL + "/PriceMonitor.apk");
                URLConnection connection = url.openConnection();
                connection.connect();
                InputStream inputStream = connection.getInputStream();
                byte[] fileBytes = new byte[2048];

                File file = new File(Environment.getExternalStorageDirectory().getPath() + "/PriceMonitor.apk");
                FileOutputStream outputStream = new FileOutputStream(file);
                int count;

                while ((count = inputStream.read(fileBytes)) != -1)
                {
                    outputStream.write(fileBytes, 0, count);
                }

                outputStream.flush();
                outputStream.close();
                inputStream.close();

                runOnUiThread(() ->
                {
                    showProgressDialog(false);
                    updateVersion(file);
                });
            } catch (RuntimeException | IOException ex)
            {
                ex.printStackTrace();
                runOnUiThread(() ->
                {
                    showProgressDialog(false);
                    showMessageDialog(getString(R.string.error), ex.getMessage());
                });
            }
        }).start();
    }


    private void updateVersion(File file)
    {
        if (!file.exists())
        {
            try
            {
                boolean newFile = file.createNewFile();
                if (!newFile)
                {
                    showMessageDialog(getString(R.string.info),
                            getString(R.string.error_occurred));
                    return;
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        PackageManager pm = getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
        int version = 0;
        try
        {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }
        if (file.length() > 0 && info != null && info.versionCode > version)
        {

            Intent installIntent;
            Uri uri;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            {
                installIntent = new Intent(Intent.ACTION_VIEW);
                uri = Uri.fromFile(file);
                installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
            }
            else
            {
                installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
                installIntent.setData(uri);
                installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            startActivity(installIntent);
        }
        else
        {
            showMessageDialog(getString(R.string.info),
                    getString(R.string.no_new_version));
        }
    }

    protected void enableStorageAccess()
    {
        String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, permissions, 1);
            Intent intent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
        }
    }

    private void buildProgressDialog()
    {
        View view = getLayoutInflater().inflate(R.layout.progress_dialog_layout,
                findViewById(android.R.id.content), false);
        if (progressDialog == null)
        {
            progressDialog = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .create();
        }
    }

    private void buildMessageDialog()
    {
        messageDialogBuilder = new AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert);
    }

    private void getPriceData(String barcode)
    {
        if (barcode.isEmpty())
            return;
        logo.setVisibility(View.GONE);
        clearData();
        showProgressDialog(true);

        new Thread(() -> {
            URL url;
            try {
                url = new URL(localURL + "/app.aspx?barcode=" + barcode);
                if (!localMode)
                    url = new URL(remoteURL + "/app.aspx?barcode=" + barcode);

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(connectionTimeout, TimeUnit.SECONDS)
                        .build();

                Request request = new Request.Builder()
                        .url(url)
                        .build();
                Response response = client.newCall(request).execute();
                ResponseBody responseBody = response.body();
                PriceData priceData = new PriceData();
                if (responseBody != null)
                {
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody.string());
                        priceData.setBarcode(jsonObject.getString("barcode"));
                        priceData.setInvName(jsonObject.getString("invName"));
                        priceData.setPrice(jsonObject.getString("price"));
                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            showInitial();
                            showMessageDialog(getString(R.string.error),
                                    getString(R.string.server_error) + ": " + e.getMessage()
                            );
                        });
                        return;
                    }

                    if (priceData.getBarcode().isEmpty())
                    {
                        runOnUiThread(() -> {

                            showMessageDialog("Mal tapılmadı",
                                    "Bu barkoda uyğun mal tapılmadı: " + barcode);
                            showInitial();
                        });
                        return;
                    }

                    runOnUiThread(() -> printData(priceData));
                }
            } catch (IOException e) {
                runOnUiThread(() -> {
                    showInitial();
                    showMessageDialog(getString(R.string.error),
                            getString(R.string.server_error) + ": " + e.getMessage()
                    );
                });
            }
        }).start();
    }

    private void printData(PriceData priceData)
    {
        showProgressDialog(false);

        barcodeText.setText(priceData.getBarcode());
        invNameText.setText(priceData.getInvName());
        priceText.setText(String.format(Locale.getDefault(),
                "%.2f AZN", Double.parseDouble(priceData.getPrice())));

        new Thread(() -> {
            long start  = System.currentTimeMillis() / 1000;
            while (true) {
                if ((System.currentTimeMillis() / 1000 - start) > resultTimeout) {
                    runOnUiThread(this::showInitial);
                    break;
                }
            }
        }).start();
    }

    private void clearData()
    {
        barcodeEdit.setText("");
        barcodeText.setText("");
        invNameText.setText("");
        priceText.setText("");
    }

    public void showProgressDialog(boolean b)
    {
        if (b)
            progressDialog.show();
        else
            progressDialog.dismiss();
    }

    private void showInitial()
    {
        clearData();
        logo.setVisibility(View.VISIBLE);
        showProgressDialog(false);
    }

    protected void showMessageDialog(String title, String message)
    {
        AlertDialog messageDialog = messageDialogBuilder.setTitle(title).setMessage(message).create();
        messageDialog.show();

        new Thread(() -> {
            long start  = System.currentTimeMillis() / 1000;
            while (true) {
                if ((System.currentTimeMillis() / 1000 - start) > resultTimeout) {
                    runOnUiThread(messageDialog::dismiss);
                    break;
                }
            }
        }).start();
    }
}