package org.example.eventos;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class EventosWeb extends Activity {

    private String evento, descuento;
    WebView navegador;
    WebView navegador2;
    ProgressDialog dialogo;
    TextView tvDescuento;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.eventos_web);
        Bundle extras = getIntent().getExtras();
        evento = extras.getString("evento");

        if (evento==null || evento.equals("")) {
            Log.d("AAAA", "dentro");
            android.net.Uri url = getIntent().getData();
            Log.d("AAA", "url: "+url.toString());
            evento= url.getQueryParameter("evento");
            Log.d("AAA", "evento: "+evento);
            descuento = url.getQueryParameter("descuento");
            tvDescuento = (TextView)findViewById(R.id.tvDescuento);
            tvDescuento.setText("Has obtenido un descuento del "+descuento+"% en "+evento);
        }
        Log.d("DESC", "descuento   "+descuento);
        navegador = (WebView)findViewById(R.id.webkit);
        navegador.getSettings().setJavaScriptEnabled(true);
        navegador.getSettings().setBuiltInZoomControls(false);
        navegador2 = (WebView)findViewById(R.id.webkit2);
        navegador2.getSettings().setJavaScriptEnabled(true);
        navegador2.getSettings().setBuiltInZoomControls(false);
        navegador.loadUrl("https://eventos-99e65.firebaseapp.com/index.html");
        navegador2.loadUrl("https://us-central1-eventos-99e65.cloudfunctions.net/mostrarEventosHtml?evento="+evento);
        //navegador.loadUrl("file:///android_asset/index.html ");
        /*Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri = Uri.parse(
                "https://eventos-99e65.firebaseapp.com/index.html");
        intent.setData(uri);
        startActivity(intent);*/
       /* ActivityCompat.requestPermissions(EventosWeb.this,
                new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);*/
        ActivityCompat.requestPermissions(EventosWeb.this,
                new String[]{android.Manifest.permission.ACCESS_NETWORK_STATE}, 2);

        final InterfazComunicacion miInterfazJava = new InterfazComunicacion(this);
        navegador.addJavascriptInterface(miInterfazJava, "jsInterfazNativa");

        navegador.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url){
                /*filtrar enlaces
                String url_filtro = "http://www.androidcurso.com/";
                if(!url.toString().equals(url_filtro)){
                    view.loadUrl(url_filtro);
                }*/
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                dialogo = new ProgressDialog(EventosWeb.this);
                dialogo.setMessage("Cargando...");
                dialogo.setCancelable(true);
                dialogo.show();

            }
            @Override
            public void onPageFinished(WebView view, String url) {
                dialogo.dismiss();
                navegador.loadUrl("javascript:colorFondo(\""+Comun.colorFondo+"\")");
                navegador2.loadUrl("javascript:colorFondo(\""+Comun.colorFondo+"\")");
                navegador.loadUrl("javascript:muestraEvento(\""+evento+"\");");
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                AlertDialog.Builder builder = new AlertDialog.Builder(EventosWeb.this);
                builder.setMessage(description).setPositiveButton("Aceptar", null)
                        .setTitle("onReceivedError");
                builder.show();
            }
        });



        navegador.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progreso) {

            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(EventosWeb.this)
                        .setTitle("Mensaje")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, new AlertDialog.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                result.confirm();
                            }
                        }).setCancelable(false).create().show();
                return true;
            }
        });

        navegador.setDownloadListener(new DownloadListener() {//detecta lo que son descargas y las procesa
            public void onDownloadStart(final String url, String userAgent, String contentDisposition,
                                        String mimetype,long contentLength) {
                AlertDialog.Builder builder = new AlertDialog.Builder(EventosWeb.this);
                builder.setTitle("Descarga");
                builder.setMessage("¿Deseas guardar el archivo?");
                builder.setCancelable(false)
                        .setPositiveButton("Aceptar", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                URL urlDescarga;
                                try {
                                    urlDescarga = new URL(url);
                                    new DescargarFichero().execute(urlDescarga);
                                } catch (MalformedURLException e) {
                                    e.printStackTrace();
                                }
                            }
                        }).setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                builder.create().show();
            }
        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //permiso otorgado
                } else {
                    Toast.makeText(EventosWeb.this,
                            "Permiso denegado para escribir en el almacenamiento.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case 2: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(EventosWeb.this,
                            "Permiso denegado para conocer el estado de la red.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private boolean comprobarConectividad() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if ((info == null || !info.isConnected() || !info.isAvailable())) {
            Toast.makeText(EventosWeb.this, "Oops! No tienes conexión a internet", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private class DescargarFichero extends AsyncTask<URL, Integer, Long> {

        private String mensaje;
        @Override
        protected Long doInBackground(URL... url) {
            String urlDescarga = url[0].toString();
            mensaje = "";
            InputStream inputStream =null;
            try {
                URL direccion = new URL(urlDescarga); HttpURLConnection conexion =
                        (HttpURLConnection)direccion.openConnection();
                if (conexion.getResponseCode()== HttpURLConnection.HTTP_OK) {
                    inputStream = conexion.getInputStream();
                    String fileName = android.os.Environment
                            .getExternalStorageDirectory().getAbsolutePath() +
                            "/descargas";
                    File directorio = new File(fileName); directorio.mkdirs();
                    File file = new File(directorio,
                            urlDescarga.substring(urlDescarga.lastIndexOf("/"),
                                    (urlDescarga.indexOf("?")==-1 ? urlDescarga.length():urlDescarga.indexOf("?"))));
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    byte[] buffer = new byte[1024];
                    int len = 0;
                    int bytesRead = -1;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead);
                    }
                    fileOutputStream.close();
                    inputStream.close();
                    mensaje = "Guardado en: " + file.getAbsolutePath();
                } else {
                    throw new Exception(conexion.getResponseMessage());
                }
            } catch (Exception ex) {
                mensaje = ex.getClass().getSimpleName() + " " + ex.getMessage();
            }
            finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                    }
                }
            }

            return (long) 0;
        }

        protected void onPostExecute(Long result) {
            AlertDialog.Builder builder = new
                    AlertDialog.Builder(EventosWeb.this);
            builder.setTitle("Descarga");
            builder.setMessage(mensaje);
            builder.setCancelable(true);
            builder.create().show();
        }
    }
    /*Interfaz comunicacion webView-Android*/
    public class InterfazComunicacion {
        Context mContext;
        InterfazComunicacion(Context c) {
            mContext = c;
        }
        @JavascriptInterface
        public void volver(){
            finish(); }
    }

}

