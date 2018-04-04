package org.example.eventos;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

/**
 * Created by pistoleta on 9/3/18.
 * https://drive.google.com/drive/folders/1_dmnGlFKInCwKxE_jNBndLJdtKZOXAAG?usp=sharing
 */

public class FotografiasDrive extends AppCompatActivity {
    static String TAG = "FOTODRIVE";

    String evento;
    static Drive servicio = null;
    static GoogleAccountCredential credencial = null;
    static String nombreCuenta = null;
    static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    static final String DISPLAY_MESSAGE_ACTION = "org.example.eventos.DISPLAY_MESSAGE";
    private static Handler manejador = new Handler();
    private static Handler carga = new Handler();
    private static ProgressDialog dialogo;
    private Boolean noAutoriza=false;
    static final int SOLICITUD_SELECCION_CUENTA = 1;
    static final int SOLICITUD_AUTORIZACION = 2;
    static final int SOLICITUD_SELECCIONAR_FOTOGRAFIA = 3;
    static final int SOLICITUD_HACER_FOTOGRAFIA = 4;
    private static Uri uriFichero;
    private String idCarpeta="";        //identificador de la carpeta base llamada “EventosDrive”
    private String idCarpetaEvento="";  //identificador de la carpeta del evento seleccionado
    private String idCarpetaCompartida = "1_dmnGlFKInCwKxE_jNBndLJdtKZOXAAG";
    private boolean modoCompartir = false;
    static WebView mDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fotografias_drive);
        registerReceiver(mHandleMessageReceiver, new IntentFilter(DISPLAY_MESSAGE_ACTION));

        mDisplay = (WebView) findViewById(R.id.display);
        mDisplay.getSettings().setJavaScriptEnabled(true);
        mDisplay.getSettings().setBuiltInZoomControls(false);
        mDisplay.loadUrl("file:///android_asset/fotografias.html");

        Bundle extras = getIntent().getExtras();
        evento =extras.getString("evento");
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        /*nicializamos la variable credencial de la clase GoogleAccountCredential. Indicamos que se va a usar OAuth2.0 con usingOAuth2*/
        credencial = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
        /*Consultamos si ya hemos pedido la cuenta,*/
        SharedPreferences prefs = getSharedPreferences("Preferencias", Context.MODE_PRIVATE);
        nombreCuenta = prefs.getString("nombreCuenta", null);
        noAutoriza = prefs.getBoolean("noAutoriza",false);
        idCarpeta = prefs.getString("idCarpeta", null);
        idCarpetaEvento = prefs.getString("idCarpeta_"+evento, null);

        if (!noAutoriza){
            if (nombreCuenta == null) {
                PedirCredenciales();
            } else {
                /* leemos la cuenta y activamos el acceso al servicio en Drive*/
                credencial.setSelectedAccountName(nombreCuenta);
                servicio = obtenerServicioDrive(credencial);
                if (idCarpetaEvento==null){
                    crearCarpetaEnDrive(evento,idCarpeta);
                } else {
                    listarFicheros(this.findViewById(android.R.id.content));
                }
            }
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_drive, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        modoCompartir=false;
        View vista = (View) findViewById(android.R.id.content);
        int id = item.getItemId();
        switch (id) {
            case R.id.action_camara:
                if (!noAutoriza) {
                    hacerFoto(vista);
                }
                break;
            case R.id.action_galeria:
                if (!noAutoriza) {
                    seleccionarFoto(vista);
                }
                break;
            case R.id.action_compartir_camara:
                modoCompartir=true;
                hacerFoto(vista);
                break;
            case R.id.action_compartir_galeria:
                modoCompartir=true;
                seleccionarFoto(vista);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    static void mostrarMensaje(final Context context, final String mensaje) {
        manejador.post(new Runnable() {
        public void run() {
            Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show();
        }
        });
    }

    static void mostrarCarga(final Context context, final String mensaje) {
        Log.d(TAG,"mostrarCarga()");
        carga.post(new Runnable() {
        public void run() {
            dialogo = new ProgressDialog(context);
            dialogo.setMessage(mensaje);
            dialogo.show();
        }
        });
    }
    static void ocultarCarga(final Context context) {
        Log.d(TAG,"ocultarCarga()");
        carga.post(new Runnable() {
            public void run() {
                dialogo.dismiss();
            }
        });
    }

    private void PedirCredenciales() {
        Log.d(TAG,"PedirCredenciales()");
        if (nombreCuenta == null) {
            startActivityForResult(credencial.newChooseAccountIntent(), SOLICITUD_SELECCION_CUENTA);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        Log.d(TAG,"obtenerServicioDrive() result: "+resultCode);
        switch (requestCode) {
            case SOLICITUD_SELECCION_CUENTA:
                Log.d(TAG,"case SOLICITUD_SELECCION_CUENTA");
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    nombreCuenta = data .getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (nombreCuenta != null) {
                            credencial.setSelectedAccountName(nombreCuenta);
                            servicio = obtenerServicioDrive(credencial);
                            SharedPreferences prefs = getSharedPreferences("Preferencias", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("nombreCuenta", nombreCuenta);
                            editor.commit();
                            crearCarpetaEnDrive(evento, idCarpeta);
                    }
                }
                break;
            case SOLICITUD_HACER_FOTOGRAFIA:
                Log.d(TAG,"case SOLICITUD_HACER_FOTOGRAFIA");
                if (resultCode == Activity.RESULT_OK) {
                    guardarFicheroEnDrive(this.findViewById(android.R.id.content));
                }else{
                    modoCompartir=false;
                }
                break;
            case SOLICITUD_SELECCIONAR_FOTOGRAFIA:
                Log.d(TAG,"case SOLICITUD_SELECCIONAR_FOTOGRAFIA");
                if (resultCode == Activity.RESULT_OK) {
                    Uri ficheroSeleccionado = data.getData();
                    String[] proyeccion = { MediaStore.Images.Media.DATA };
                    Cursor cursor = managedQuery(ficheroSeleccionado, proyeccion, null, null, null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();
                    uriFichero = Uri.fromFile( new java.io.File(cursor.getString(column_index)));
                    guardarFicheroEnDrive(this.findViewById(android.R.id.content));
                }
                break;
            case SOLICITUD_AUTORIZACION:
                Log.d(TAG,"case SOLICITUD_AUTORIZACION");
                if (resultCode == Activity.RESULT_OK) {
                    crearCarpetaEnDrive(evento, idCarpeta);
                } else {
                    noAutoriza=true;
                    SharedPreferences prefs = getSharedPreferences("Preferencias", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("noAutoriza", true);
                    editor.commit();
                    mostrarMensaje(this,"El usuario no autoriza usar Google Drive");
                }
                break;
        }
    }

    private void crearCarpetaEnDrive(final String nombreCarpeta, final String carpetaPadre) {
        Log.d(TAG,"crearCarpetaEnDrive() nom: "+nombreCarpeta+ " padre:"+carpetaPadre);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                        String idCarpetaPadre = carpetaPadre;
                        mostrarCarga(FotografiasDrive.this, "Creando carpeta...");

                        //Crear carpeta EventosDrive (la padre)
                        if (idCarpeta==null){
                            File metadataFichero = new File();
                            metadataFichero.setName("EventosDrive");
                            metadataFichero.setMimeType("application/vnd.google-apps.folder");
                            File fichero = servicio.files().create(metadataFichero).setFields("id").execute();

                            if (fichero.getId() != null) {
                                SharedPreferences prefs = getSharedPreferences("Preferencias",
                                        Context.MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("idCarpeta", fichero.getId());
                                editor.commit();
                                idCarpetaPadre=fichero.getId();
                            }
                        }

                        //crea carpeta hija (carnaval, fallas...)
                        File metadataFichero = new File();
                        metadataFichero.setName(nombreCarpeta);
                        metadataFichero.setMimeType("application/vnd.google-apps.folder");
                        if (!idCarpetaPadre.equals("")){
                            metadataFichero.setParents(Collections.singletonList (idCarpetaPadre));
                        }
                        File fichero = servicio.files().create(metadataFichero).setFields("id") .execute();
                        if (fichero.getId() != null) {
                            SharedPreferences prefs = getSharedPreferences("Preferencias", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("idCarpeta_"+evento, fichero.getId());
                            editor.commit();
                            idCarpetaEvento=fichero.getId();
                            mostrarMensaje(FotografiasDrive.this, "¡Carpeta creada!");
                        }
                        ocultarCarga(FotografiasDrive.this);
                    }catch (UserRecoverableAuthIOException e){
                        ocultarCarga(FotografiasDrive.this);
                        startActivityForResult(e.getIntent(), SOLICITUD_AUTORIZACION);
                    } catch (IOException e) {
                        mostrarMensaje(FotografiasDrive.this, "Error;" + e.getMessage());
                        ocultarCarga(FotografiasDrive.this);
                        e.printStackTrace();
                    }
            }
        });
        t.start();
    }

    private void guardarFicheroEnDrive(final View view) {
        Log.d(TAG,"guardarFicheroEnDrive() uri: "+uriFichero.getPath()+ " carpeta:"+idCarpetaEvento);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mostrarCarga(FotografiasDrive.this,"Subiendo imagen...");
                    java.io.File ficheroJava = new java.io.File(uriFichero.getPath());
                    FileContent contenido = new FileContent("image/jpeg", ficheroJava);

                    File ficheroDrive = new File();
                    ficheroDrive.setName(ficheroJava.getName()); // nombre archivo
                    ficheroDrive.setMimeType("image/jpeg");

                    if(modoCompartir)
                        ficheroDrive.setParents(Collections.singletonList(idCarpetaCompartida));
                    else
                        ficheroDrive.setParents(Collections.singletonList(idCarpetaEvento)); //en que carpeta lo queremos

                    File ficheroSubido = servicio.files().create(ficheroDrive, contenido).setFields("id").execute(); //lo subimos

                    if (ficheroSubido.getId() != null) {
                        mostrarMensaje(FotografiasDrive.this, "¡Foto subida!");
                        listarFicheros(view);
                    }
                    ocultarCarga(FotografiasDrive.this);
                    modoCompartir=false;
                }catch (UserRecoverableAuthIOException e) {
                    ocultarCarga(FotografiasDrive.this);
                    modoCompartir=false;
                    startActivityForResult(e.getIntent(), SOLICITUD_AUTORIZACION);
                } catch (IOException e) {
                    mostrarMensaje(FotografiasDrive.this, "Error;"+e.getMessage());
                    ocultarCarga(FotografiasDrive.this);
                    modoCompartir=false;
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    public void hacerFoto(View v) {
        if (nombreCuenta == null ) {//autenticados en drive?
            mostrarMensaje(this, "Debes seleccionar una cuenta de Google Drive");
        } else {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                java.io.File ficheroFoto = null;
                try {
                    ficheroFoto = crearFicheroImagen(); //fichero unico + ruta de la carpeta publica SD donde se guardan las fotos
                    if (ficheroFoto != null) {
                        Uri fichero = FileProvider.getUriForFile(
                                FotografiasDrive.this, BuildConfig.APPLICATION_ID + ".provider", ficheroFoto);
                        uriFichero = Uri.parse("content://" + ficheroFoto.getAbsolutePath());
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, fichero);
                        startActivityForResult(takePictureIntent, SOLICITUD_HACER_FOTOGRAFIA); //lanzar intencion hacer foto
                    }
                } catch (IOException ex) {
                    modoCompartir=false;
                    return;
                }
            }
        }
    }

    private java.io.File crearFicheroImagen() throws IOException {
        String tiempo = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String nombreFichero = "JPEG_" + tiempo + "_";
        java.io.File dirAlmacenaje = new java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
        java.io.File ficheroImagen = java.io.File.createTempFile(nombreFichero, ".jpg", dirAlmacenaje);
        return ficheroImagen;
    }

    public void seleccionarFoto(View v) {
        if (nombreCuenta == null) {
            mostrarMensaje(this,"Debes seleccionar una cuenta de Google Drive");
        } else {
            Intent seleccionFotografiaIntent = new Intent();
            seleccionFotografiaIntent.setType("image/*");
            seleccionFotografiaIntent.setAction(Intent.ACTION_PICK);
            startActivityForResult(Intent.createChooser(seleccionFotografiaIntent,
                    "Seleccionar fotografía"),SOLICITUD_SELECCIONAR_FOTOGRAFIA);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private final BroadcastReceiver mHandleMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String nuevoMensaje = intent.getExtras().getString("mensaje");

        }
    };

    static void mostrarTexto(Context contexto, String mensaje) {
        Intent intent = new Intent(DISPLAY_MESSAGE_ACTION); intent.putExtra("mensaje", mensaje); contexto.sendBroadcast(intent);
    }

    public void listarFicheros(View v) {
        if (nombreCuenta == null) {
            mostrarMensaje(this, "Debes seleccionar una cuenta de Google Drive");
        }else {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        mostrarCarga(FotografiasDrive.this,"Listando archivos...");
                        vaciarLista(getBaseContext());
                        FileList ficheros = servicio.files().list()
                            .setQ("'"+idCarpetaEvento+"' in parents") .setFields("*")
                            .execute();
                        for (File fichero : ficheros.getFiles()) {
                            addItem(FotografiasDrive.this, fichero.getOriginalFilename(),fichero.getThumbnailLink());
                        }
                        mostrarMensaje(FotografiasDrive.this, "¡Archivos listados!");
                        ocultarCarga(FotografiasDrive.this);
                    } catch (UserRecoverableAuthIOException e) {
                        ocultarCarga(FotografiasDrive.this);
                        startActivityForResult(e.getIntent(),SOLICITUD_AUTORIZACION);
                    } catch (IOException e) {
                        mostrarMensaje(FotografiasDrive.this, "Error;" + e.getMessage());
                        ocultarCarga(FotografiasDrive.this);
                        e.printStackTrace();
                    }
                }
            });
            t.start();
        }
    }

    static void addItem(final Context context, final String fichero, final String imagen){
        carga.post(new Runnable() {
            public void run() {
                mDisplay.loadUrl("javascript:add(\"" + fichero + "\",\"" + imagen + "\");");
            }
        });
    }

    static void vaciarLista(final Context context) {
        carga.post(new Runnable() {
            public void run() {
                mDisplay.loadUrl("javascript:vaciar()");
            }
        });
    }


    /*Retorna el servicio Google Drive que utilizaremos para hacer las llamadas a la API*/
    private Drive obtenerServicioDrive(GoogleAccountCredential credencial) {
        Log.d(TAG,"obtenerServicioDrive()");
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(),credencial).build();
    }
}
