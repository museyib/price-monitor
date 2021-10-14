package az.inci.pricemonitor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class MainActivity extends AppCompatActivity {

    private String server;
    private String port;
    private int resultTimeout;
    private int connectionTimeout;
    private AlertDialog progressDialog;
    private AlertDialog.Builder messageDialogBuilder;
    private ImageView logo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buildProgressDialog();
        buildMessageDialog();

        EditText barcodeEdit = findViewById(R.id.barcode_edit);
        logo = findViewById(R.id.logo);

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
        server = preferences.getString("server", "localhost");
        port = preferences.getString("port", "80");

        resultTimeout = Integer.parseInt(preferences.getString("result_timeout", "5"));
        connectionTimeout = Integer.parseInt(preferences.getString("connection_timeout", "5"));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        menu.getItem(0).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });

        return super.onCreateOptionsMenu(menu);
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
                url = new URL("http://" + server + ":" + port + "/app.aspx?barcode=" + barcode);
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

        ((TextView)findViewById(R.id.barcode)).setText(priceData.getBarcode());
        ((TextView)findViewById(R.id.inv_name)).setText(priceData.getInvName());
        ((TextView)findViewById(R.id.price)).setText(String.format(Locale.getDefault(),
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
        ((EditText)findViewById(R.id.barcode_edit)).setText("");
        ((TextView)findViewById(R.id.barcode)).setText("");
        ((TextView)findViewById(R.id.inv_name)).setText("");
        ((TextView)findViewById(R.id.price)).setText("");
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