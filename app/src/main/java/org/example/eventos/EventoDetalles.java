package org.example.eventos;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.HttpMethod;
import com.facebook.Profile;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.facebook.share.Sharer;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareDialog;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.perf.metrics.AddTrace;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnPausedListener;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.example.eventos.Comun.acercaDe;
import static org.example.eventos.Comun.getStorageReference;
import static org.example.eventos.Comun.mostrarDialogo;

import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;

import org.json.JSONObject;
import org.w3c.dom.Text;

/**
 * Created by pistoleta on 7/3/18.
 */

public class EventoDetalles extends AppCompatActivity {

    TextView txtEvento, txtFecha, txtCiudad;
    ImageView imgImagen;
    String evento;
    CollectionReference registros;
    Trace mTrace;

    /*subida y bajada*/
    final int SOLICITUD_SUBIR_PUTDATA = 0;
    final int SOLICITUD_SUBIR_PUTSTREAM = 1;
    final int SOLICITUD_SUBIR_PUTFILE = 2;
    final int SOLICITUD_SELECCION_STREAM = 100;
    final int SOLICITUD_SELECCION_PUTFILE = 101;
    final int SOLICITUD_FOTOGRAFIAS_DRIVE = 102;
    private ProgressDialog progresoSubida;
    Boolean subiendoDatos =false;
    Boolean bajandoDatos =false;
    static UploadTask uploadTask = null;
    static FileDownloadTask fileDownloadTask = null;
    StorageReference imagenRef;
    StorageReference imagenRefDown;
    StorageReference imagenRefBorra;
    Uri sessionUri;

    /*facebook*/
    private LoginButton loginButtonOficial;
    private Button botonLogOut;
    private Button botonEnviarFoto;
    private EditText textoConElMensaje;
    private Button botonCompartir;
    private TextView elTextoDeBienvenida;
    private ShareDialog elShareDialog;

    // gestiona los callbacks al FacebookSdk desde el método
    // onActivityResult() de una actividad
    private CallbackManager elCallbackManagerDeFacebook;

    // puntero a this para los callback
    private final Activity THIS = this;


    @Override
    @AddTrace(name = "onCreateTrace", enabled = true)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(this.getApplicationContext());

        // pongo el contenido visual de la actividad (hacer antes que
        // findViewById () y después de inicializar FacebookSDK)

        setContentView(R.layout.evento_detalles);
        txtEvento = (TextView) findViewById(R.id.txtEvento);
        txtFecha = (TextView) findViewById(R.id.txtFecha);
        txtCiudad = (TextView) findViewById(R.id.txtCiudad);
        imgImagen = (ImageView) findViewById(R.id.imgImagen);
        Bundle extras = getIntent().getExtras();
        evento = extras.getString("evento");

        if (evento==null || evento.equals("")) {
            Log.d("AAAA", "dentro");
            android.net.Uri url = getIntent().getData();
            Log.d("AAA", "url: "+url.toString());
            evento= url.getQueryParameter("evento");
            Log.d("AAA", "evento: "+evento);
        }
        registros = FirebaseFirestore.getInstance().collection("eventos");
        if(!evento.equals("")) {
            registros.document(evento).get().addOnCompleteListener(
                    new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if (task.isSuccessful()) {
                                txtEvento.setText(task.getResult().get("evento").toString());
                                txtCiudad.setText(task.getResult().get("ciudad").toString());
                                txtFecha.setText(task.getResult().get("fecha").toString());
                                new DownloadImageTask(
                                        (ImageView) imgImagen).execute(task.getResult()
                                        .get("imagen").toString());
                            }
                        }
                    });

        }
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if(Comun.mFirebaseAnalytics!=null)
            Comun.mFirebaseAnalytics.setUserProperty("evento_detalle", evento);

        mTrace = FirebasePerformance.getInstance().newTrace("trace_EventoDetalles");
        mTrace.start();

        onCreateSocialMedia();
    }

    private void onCreateSocialMedia() {
        //boton oficial facebook, obtengo referencia y declaro persmisos que debe pedir
        loginButtonOficial = (LoginButton)findViewById(R.id.login_button);
        loginButtonOficial.setPublishPermissions("publish_actions");

        elTextoDeBienvenida = (TextView)findViewById(R.id.elTextoDeBienvenida);

        botonCompartir =(Button)findViewById(R.id.boton_EnviarAFB);
        botonEnviarFoto = (Button)findViewById(R.id.boton_EnviarFoto);

        // crear callback manager de Facebook
        this.elCallbackManagerDeFacebook = CallbackManager.Factory.create();
        textoConElMensaje = (EditText)findViewById(R.id.txt_mensajeFB);
        textoConElMensaje.setText("ECHA UN VISTAZO A ESTE EVENTO: "+evento.toUpperCase()+ "!!");

        // registro un callback para saber cómo ha ido el login
        LoginManager.getInstance().registerCallback(this.elCallbackManagerDeFacebook,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        Toast.makeText(THIS, "Login onSuccess()", Toast.LENGTH_LONG).show();
                        actualizarVentanita();
                    }

                    @Override
                    public void onCancel() {
                        Toast.makeText(THIS, "Login onCancel()",
                                Toast.LENGTH_LONG).show();
                        actualizarVentanita();
                    }

                    @Override
                    public void onError(FacebookException error) {
                        Toast.makeText(THIS, "Login onError(): " + error.getMessage(),Toast.LENGTH_LONG).show();
                        actualizarVentanita();
                    }
                });


        //crear objeto share dialog
        this.elShareDialog = new ShareDialog(this);

        this.elShareDialog.registerCallback(this.elCallbackManagerDeFacebook, new FacebookCallback<Sharer.Result>() {
            @Override
            public void onSuccess(Sharer.Result result) {
                Toast.makeText(THIS,"Sharer onSuccess()", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onCancel() {
                Toast.makeText(THIS,"Sharer onCANCEL()", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(FacebookException error) {
                Toast.makeText(THIS, "Sharer onError(): " +error.toString(), Toast.LENGTH_LONG).show();
            }
        });
    }




    private void actualizarVentanita(){
        Log.d("ACTUALIZARVENT", "empiezo");
        // obtengo el access token para ver si hay sesión
        AccessToken accessToken = this.obtenerAccessToken();
        if(accessToken == null){
            Log.d("ACTUALIZARVENT","no hay sesion, deshabilito");
            // sesion con facebook cerrada
            //this.botonHacerLogin.setEnabled(true);

            this.textoConElMensaje.setEnabled(false);
            this.botonCompartir.setEnabled(false);
            this.botonEnviarFoto.setEnabled(false);
            this.elTextoDeBienvenida.setText("haz login");
            return;
        }
        // sí hay sesión
        Log.d("ACTUALIZARVENT", "hay sesion habilito");
      //  this.botonHacerLogin.setEnabled(false);

        this.textoConElMensaje.setEnabled(true);
        this.botonCompartir.setEnabled(true);
        this.botonEnviarFoto.setEnabled(true);

        // averiguo los datos básicos del usuario acreditado
        Profile profile = Profile.getCurrentProfile();
        if (profile != null) {
            this.textoConElMensaje.setText(profile.getName());
        }

        // otra forma de averiguar los datos básicos:
        // hago una petición con "graph api" para obtener datos del usuario acreditado
        this.obtenerPublicProfileConRequest_async(new GraphRequest.GraphJSONObjectCallback(){
            @Override
            public void onCompleted(JSONObject datosJSON, GraphResponse response){
                // muestro los datos
                String nombre= "nombre desconocido";
                try {
                    nombre = datosJSON.getString("name");
                } catch (org.json.JSONException ex) {
                    Log.d("ACTUALIZARVENT", "callback de obtenerPublicProfileConRequest_async: excepcion: " + ex.getMessage());
                }catch (NullPointerException ex) {
                    Log.d("ACTUALIZARVENT", "callback de obtenerPublicProfileConRequest_async: excepcion: " + ex.getMessage());
                }
                elTextoDeBienvenida.setText("bienvenido 2018: "+nombre);
            }
        });

    }
    private void obtenerPublicProfileConRequest_async ( GraphRequest.GraphJSONObjectCallback callback) {
        if(!this.hayRed()){
            Toast.makeText(this,"¿No hay red?", Toast.LENGTH_LONG).show();
        }

        // obtengo access token y compruebo que hay sesión
        AccessToken accessToken = obtenerAccessToken();
        if (accessToken == null) {
            Toast.makeText(THIS, "no hay sesión con Facebook", Toast.LENGTH_LONG).show();
            return;
        }
        // monto la petición: /me
        GraphRequest request = GraphRequest.newMeRequest(accessToken, callback);
        Bundle params = new Bundle ();
        params.putString("fields", "id, name");
        request.setParameters(params);
        // la ejecuto (asíncronamente)
        request.executeAsync();
    }

    private AccessToken obtenerAccessToken() {
        return AccessToken.getCurrentAccessToken();
    }

    private boolean hayRed(){
        //comprobar que estamos conectados a internet antess de hacer el login con FB, sino da problemas
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    private boolean sePuedePublicar(){
        //compruebo red
        if(!this.hayRed()){
            Toast.makeText(this, "¿no hay red? No se puede publicar", Toast.LENGTH_LONG).show();
            return false;
        }

        // compruebo permisos
        if (! this.tengoPermisoParaPublicar()) {
            Toast.makeText(this, "¿no tengo permisos para publicar? Los pido.", Toast.LENGTH_LONG).show();
            // pedirPermisoParaPublicar();
            LoginManager.getInstance().logInWithPublishPermissions(this, Arrays.asList("publish_actions"));

            return false;
        }

        return true;
    }
    private boolean tengoPermisoParaPublicar() {
        AccessToken accessToken = this.obtenerAccessToken();
        return accessToken != null && accessToken.getPermissions().contains("publish_actions");
    }

    public void boton_enviarTextoAFB_pulsado(View quien) {
        // cojo el mensaje que ha escrito el usuario
        String mensaje = "msg:" + this.textoConElMensaje.getText() + " :" + System.currentTimeMillis();
        // borro lo escrito
        this.textoConElMensaje.setText("");
        // cierro el soft-teclado
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(this.textoConElMensaje.getWindowToken(), 0);
        // llamo al método que publica
        enviarTextoAFacebook_async(mensaje);
    }

    public void enviarTextoAFacebook_async (final String textoQueEnviar) {
        // si no se puede publicar no hago nada
        if (!sePuedePublicar()) {
            return;
        }
        // hago la petición a través del API Graph

        Bundle params = new Bundle();
        params.putString("message", textoQueEnviar);

        GraphRequest request = new GraphRequest(AccessToken.getCurrentAccessToken(),
                "/me/feed",
                params,
                HttpMethod.POST,
                new GraphRequest.Callback() {
                    @Override
                    public void onCompleted(GraphResponse response) {
                        Toast.makeText(THIS, "Publicación realizada: " +
                                textoQueEnviar, Toast.LENGTH_LONG).show();
                    }
                });

        request.executeAsync();
    }

    public void boton_enviarFotoAFB_pulsado(View quien){
        imgImagen.setDrawingCacheEnabled(true);
        imgImagen.buildDrawingCache();
        Bitmap bitmapImagen = imgImagen.getDrawingCache();
        enviarFotoAFacebook_async(bitmapImagen, "Esta es una imagen de "+ evento);
    }

    public void enviarFotoAFacebook_async (Bitmap image, String comentario) {
        Log.d("cuandrav.envFotoFBasync", "llamado");
        if (image == null) {
            Toast.makeText(this, "Enviar foto: la imagen está vacía.", Toast.LENGTH_LONG).show();
            Log.d("ENVFOTOASYNC", "acabo porque la imagen es null");
            return;
        }
        //si no se puede publicar no hago nada
        if (!sePuedePublicar()) {
            return;
        }

        // convierto el bitmap a array de bytes
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        //image.recycle ();
        final byte[] byteArray = stream.toByteArray();
        try {
            stream.close();
        } catch (IOException e) {
        }

        //hago la peticion a traves del Graph API
        Bundle params = new Bundle();
        params.putByteArray("source", byteArray); // bytes de la imagen
        params.putString("caption", comentario); // comentario
        // si se quisiera publicar una imagen de internet: params.putString("url", "{image-url}");
        GraphRequest request = new GraphRequest(
                AccessToken.getCurrentAccessToken(),
                "/me/photos",
                params,
                HttpMethod.POST,
                new GraphRequest.Callback() {
                    public void onCompleted(GraphResponse response) {
                        Toast.makeText(THIS, "" + byteArray.length + " Foto enviada: "
                                        + response.toString(),
                                Toast.LENGTH_LONG).show();
                        //textoConElMensaje.setText(response.toString())
                    }
                }
        );
        request.executeAsync();
    }

    public void btnComentShareDialogClick(View view) {
        this.publicarMensajeConShareDialog();
    }
    public void btnImagenShareDialogClick(View view) {
        this.publicarFotoConShareDialog();
    }
    private boolean puedoUtilizarShareDialogParaPublicarMensaje () {
        return puedoUtilizarShareDialogParaPublicarLink();
    }
    private boolean puedoUtilizarShareDialogParaPublicarLink () {
        return ShareDialog.canShow(ShareLinkContent.class);
    }
    private boolean puedoUtilizarShareDialogParaPublicarFoto () {
        return ShareDialog.canShow(SharePhotoContent.class);
    }

    private void publicarMensajeConShareDialog () {
        // https://developers.facebook.com/docs/android/share -> Using the Share Dialog
        if ( ! puedoUtilizarShareDialogParaPublicarMensaje() ) {
            Log.d("MAINACTIVITY"," publicarMensajeConShareDialog > ¡¡¡ No puedo utilizar share dialog !!!");
            return;
        }

        // llamar a share dialog aunque utilizamos ShareLinkContent,
        // al no poner link publica un mensaje
        ShareLinkContent content = new ShareLinkContent.Builder().build();
        this.elShareDialog.show(content);
    }

    private void publicarFotoConShareDialog () {
        // https://developers.facebook.com/docs/android/share -> Using the Share Dialog
        if ( ! puedoUtilizarShareDialogParaPublicarFoto() ) {
            return;
        }
        // cojo la imagen del evento para publicarla

        imgImagen.setDrawingCacheEnabled(true);
        imgImagen.buildDrawingCache();
        Bitmap bitmapImagen = imgImagen.getDrawingCache();
        // monto la petición
        SharePhoto photo = new SharePhoto.Builder().setBitmap(bitmapImagen).build();
        SharePhotoContent content = new SharePhotoContent.Builder().addPhoto(photo).build();
        this.elShareDialog.show(content);
    }



    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }
                protected Bitmap doInBackground(String... urls) {
                    String urldisplay = urls[0];
                    Bitmap mImagen = null;
                    try {
                        InputStream in = new java.net.URL(urldisplay).openStream();
                        mImagen = BitmapFactory.decodeStream(in); } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return mImagen;
                }
                protected void onPostExecute(Bitmap result) {
                    bmImage.setImageBitmap(result);
                }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu_detalles,menu);
        if(!acercaDe)
            menu.removeItem(R.id.action_acercaDe);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        View vista = (View) findViewById(android.R.id.content);
        Bundle bundle = new Bundle();
        int id = item.getItemId();
        switch (id) {
            case R.id.action_putData: // (subir imagen)
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "subir_imagen");
                Comun.mFirebaseAnalytics.logEvent("menus", bundle);
                subirAFirebaseStorage(SOLICITUD_SUBIR_PUTDATA,null);
                break;
            case R.id.action_streamData: // (subir stream)
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "subir_stream");
                Comun.mFirebaseAnalytics.logEvent("menus", bundle);
                seleccionarFotografiaDispositivo(vista, SOLICITUD_SELECCION_STREAM);
                break;
            case R.id.action_putFile: // (subir fichero)
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "subir_fichero");
                Comun.mFirebaseAnalytics.logEvent("menus", bundle);
                seleccionarFotografiaDispositivo(vista,SOLICITUD_SUBIR_PUTFILE);
                break;
            case R.id.action_getFile: //Descargar fichero
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "descargar_fichero");
                Comun.mFirebaseAnalytics.logEvent("menus", bundle);
                descargarDeFirebaseStorage(evento);
                break;
            case R.id.action_eliminar: //Descargar fichero
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "eliminar_fichero");
                Comun.mFirebaseAnalytics.logEvent("menus", bundle);
                eliminarDeFirebaseStorage(evento);
                break;
            case R.id.action_fotografiasDrive:
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "fotografias_drive");
                Comun.mFirebaseAnalytics.logEvent("menus", bundle);
                Intent intent = new Intent(getBaseContext(), FotografiasDrive.class);
                intent.putExtra("evento", evento);
                startActivity(intent);
                break;
            case R.id.action_acercaDe:
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "acerca_de");
                Comun.mFirebaseAnalytics.logEvent("menus", bundle);
                Intent intentWeb = new Intent(getBaseContext(), EventosWeb.class);
                intentWeb.putExtra("evento", evento);
                startActivity(intentWeb);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void eliminarDeFirebaseStorage(final String evento) {

        StorageReference imagenRefBorra = getStorageReference().child(evento);
        imagenRefBorra.delete() .addOnSuccessListener(new OnSuccessListener() {
            @Override
            public void onSuccess(Object o) {
                Comun.mostrarDialogo(getApplicationContext(), "Eliminado con exito", evento);
            }

             })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    Comun.mostrarDialogo(getApplicationContext(), "Error al eliminar", evento);
                }});
    }

    public void seleccionarFotografiaDispositivo(View v, Integer solicitud) {
        Intent seleccionFotografiaIntent = new Intent(Intent.ACTION_PICK);
        seleccionFotografiaIntent.setType("image/*");
        startActivityForResult(seleccionFotografiaIntent, solicitud);
    }


    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode, final Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        // avisar a Facebook (a su callback manager) por si le afecta
        this.elCallbackManagerDeFacebook.onActivityResult(requestCode, resultCode, data);

        Log.d("ONACTY", "ONACTIVITYRESULT ,result:"+resultCode+"  reqCode: "+requestCode);
        try {
            Uri ficheroSeleccionado;
            Cursor cursor;
            String rutaImagen;
            if (resultCode == Activity.RESULT_OK) {
                switch (requestCode) {
                    case SOLICITUD_SELECCION_STREAM:
                        Log.d("ONACTY", "SOLICITUD_SELECCION_STREAM");
                        ficheroSeleccionado = data.getData();
                        String[] proyeccionStream = {MediaStore.Images.Media.DATA};
                        cursor = getContentResolver()
                                .query(ficheroSeleccionado, proyeccionStream, null, null, null);
                        cursor.moveToFirst();
                        rutaImagen = cursor.getString(cursor.getColumnIndex(proyeccionStream[0]));
                        cursor.close();
                        subirAFirebaseStorage(SOLICITUD_SUBIR_PUTSTREAM, rutaImagen);
                        break;
                    case SOLICITUD_SELECCION_PUTFILE:
                        Log.d("ONACTY", "SOLICITUD_SELECCION_PUTFILE");
                        ficheroSeleccionado = data.getData();
                        String[] proyeccionFile = {MediaStore.Images.Media.DATA};
                        cursor = getContentResolver()
                                .query(ficheroSeleccionado, proyeccionFile, null, null, null);
                        cursor.moveToFirst();
                        rutaImagen = cursor.getString(cursor.getColumnIndex(proyeccionFile[0]));
                        cursor.close();
                        subirAFirebaseStorage(SOLICITUD_SUBIR_PUTFILE, rutaImagen);
                        break;
                }
            }
        }catch(Exception ex){
            Log.e("ERROR", ex.getMessage());
        }
    }

    public void subirAFirebaseStorage(Integer opcion,
                                      String ficheroDispositivo) {

        Log.d("SUBIR", "subirAFirebaseStorage");
        String fichero = evento;
        imagenRef = getStorageReference().child(fichero); //referencia al fichero q vamos a subir
        try {
            switch (opcion) {
                case SOLICITUD_SUBIR_PUTDATA:
                    Log.d("SUBIR", "SOLICITUD_SUBIR_PUTDATA");
                    imgImagen.setDrawingCacheEnabled(true);
                    imgImagen.buildDrawingCache();
                    Bitmap bitmap = imgImagen.getDrawingCache();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    byte[] data = baos.toByteArray();
                    uploadTask = imagenRef.putBytes(data);
                    break;
                case SOLICITUD_SUBIR_PUTSTREAM:
                    Log.d("SUBIR", "SOLICITUD_SUBIR_PUTSTREAM");
                    InputStream stream = new FileInputStream(new File(ficheroDispositivo));
                    uploadTask = imagenRef.putStream(stream);
                    break;
                case SOLICITUD_SUBIR_PUTFILE:
                    Log.d("SUBIR", "SOLICITUD_SUBIR_PUTFILE");
                    Uri file = Uri.fromFile(new File(ficheroDispositivo));
                    uploadTask = imagenRef.putFile(file);
                    sessionUri = uploadTask.getSnapshot().getUploadSessionUri();

                    guardarSessionUri(sessionUri);

                    break;
            }

            uploadTask.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            subiendoDatos=false;
                            mostrarDialogo(getApplicationContext(),"Ha ocurrido un error al subir la imagen o el usuario ha " +
                                    "cancelado la subida","");
                        }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                uploadTaskExito(taskSnapshot);
                            }
            }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                            upload_progreso(taskSnapshot);
                        }
            }).addOnPausedListener(new OnPausedListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onPaused(UploadTask.TaskSnapshot taskSnapshot) {
                            subiendoDatos=false;
                            mostrarDialogo(getApplicationContext(), "La subida ha sido pausada.","");
                        }
            });

        } catch (IOException e) {
            Comun.mostrarDialogo(getApplicationContext(), e.toString(),""); }
    }

    private void guardarSessionUri(Uri sessionUri) {
        final SharedPreferences prefs = getApplicationContext().getSharedPreferences( "sessionUri", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("sessionUri", sessionUri.toString());
        editor.commit();
    }

    private String obtenerSessionUri(){
        final SharedPreferences preferencias = getApplicationContext().getSharedPreferences("sessionUri", Context.MODE_PRIVATE);
        return preferencias.getString("sessionUri", "");
    }

    private void uploadTaskExito(UploadTask.TaskSnapshot taskSnapshot) {

         Log.d("EVDETALL", "uploadTaskExito");
        Map<String, Object> datos = new HashMap<>();
        datos.put("imagen",taskSnapshot.getDownloadUrl().toString());
        FirebaseFirestore.getInstance().collection("eventos")
                .document(evento).set(datos, SetOptions.merge());

        new DownloadImageTask((ImageView) imgImagen)
                .execute(taskSnapshot.getDownloadUrl().toString());

        progresoSubida.dismiss();
        subiendoDatos=false;
        Comun.mostrarDialogo(getApplicationContext(), "Imagen subida correctamente.",evento);

    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d("EVDETALLE", "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        if (imagenRef != null) {
            outState.putString("EXTRA_STORAGE_REFERENCE_KEY", imagenRef.toString());
            //outState.putString("EXTRA_STORAGE_SESSIONURI", sessionUri.toString());
        }
        if(imagenRefDown != null){
            outState.putString("EXTRA_STORAGE_DOWNLOAD_KEY", imagenRefDown.toString());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.d("EVDETALLE", "onRestoreInstanceState");
        super.onRestoreInstanceState(savedInstanceState);
        final String stringRef = savedInstanceState
                .getString("EXTRA_STORAGE_REFERENCE_KEY");
        final String stringRefDown = savedInstanceState
                .getString("EXTRA_STORAGE_DOWNLOAD_KEY");
        if (stringRef == null && stringRefDown==null) {
            return;
        }

        if(stringRef!=null) {
            Log.d("EVDETALLE", "RestoreUpload");
            imagenRef = FirebaseStorage.getInstance().getReferenceFromUrl(stringRef);
            List<UploadTask> tasks = imagenRef.getActiveUploadTasks();
            for (UploadTask task : tasks) {
                task.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        upload_error(exception);
                    }
                })
                        .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                upload_exito(taskSnapshot);
                            }
                        })
                        .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onProgress(UploadTask.TaskSnapshot taskSnapshot) {
                                upload_progreso(taskSnapshot);
                            }
                        })
                        .addOnPausedListener(new OnPausedListener<UploadTask.TaskSnapshot>() {
                            @Override
                            public void onPaused(UploadTask.TaskSnapshot taskSnapshot) {
                                upload_pausa(taskSnapshot);
                            }
                        });
            }
        }
        if(stringRefDown!=null)
        {
            Log.d("EVDETALLE", "RestoreDownload");
            imagenRefDown = FirebaseStorage.getInstance().getReferenceFromUrl(stringRefDown);
            List<FileDownloadTask> tasks = imagenRefDown.getActiveDownloadTasks();
            for (FileDownloadTask task : tasks) {
                task.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            bajandoDatos=false;
                            mostrarDialogo(getApplicationContext(), "Error al descargar el fichero.", evento);
                            }
                        })
                        .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                progresoSubida.dismiss();
                                bajandoDatos=false;
                                mostrarDialogo(getApplicationContext(), "Fichero descargado con éxito: " ,evento);
                            }
                        })
                        .addOnProgressListener(new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onProgress(FileDownloadTask.TaskSnapshot taskSnapshot) {
                                download_progreso(taskSnapshot);
                            }
                        })
                        .addOnPausedListener(new OnPausedListener<FileDownloadTask.TaskSnapshot>() {
                            @Override
                            public void onPaused(FileDownloadTask.TaskSnapshot taskSnapshot) {
                               //upload_pausa(taskSnapshot);
                                bajandoDatos=false;
                            }
                        });
            }

        }
    }

    private void upload_exito(UploadTask.TaskSnapshot taskSnapshot) {
        Log.d("EVDETALL", "upload_exito");
        Map<String, Object> datos = new HashMap<>();
        datos.put("imagen", taskSnapshot.getDownloadUrl().toString());
        FirebaseFirestore.getInstance().collection("eventos")
                .document(evento).set(datos, SetOptions.merge());
        new DownloadImageTask((ImageView) imgImagen)
                .execute(taskSnapshot.getDownloadUrl().toString());
        progresoSubida.dismiss();
        subiendoDatos = false;
        mostrarDialogo(getApplicationContext(),
                "Imagen subida correctamente.", evento);
    }

    private void upload_progreso(UploadTask.TaskSnapshot taskSnapshot){
        Log.d("EVDETALL", "upload_progreso");
        if (!subiendoDatos) {
            progresoSubida = new ProgressDialog(EventoDetalles.this);
            progresoSubida.setTitle("Subiendo...");
            progresoSubida.setMessage("Espere...");
            progresoSubida.setCancelable(true);
            progresoSubida.setCanceledOnTouchOutside(false);
            progresoSubida.setButton(
                    DialogInterface.BUTTON_NEGATIVE, "Cancelar", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            uploadTask.cancel();
                        }
                    });
            progresoSubida.show();
            subiendoDatos=true;
        } else {
            if (taskSnapshot.getTotalByteCount()>0)
                progresoSubida.setMessage("Espere... " +
                        String.valueOf(100*taskSnapshot.getBytesTransferred() /taskSnapshot.getTotalByteCount())+"%");
        }
    }
    private void download_progreso(FileDownloadTask.TaskSnapshot taskSnapshot){
        Log.d("EVDETALL", "upload_progreso");
        if (!bajandoDatos) {
            progresoSubida = new ProgressDialog(EventoDetalles.this);
            progresoSubida.setTitle("Bajando...");
            progresoSubida.setMessage("Espere...");
            progresoSubida.setCancelable(true);
            progresoSubida.setCanceledOnTouchOutside(false);
            progresoSubida.setButton(
                    DialogInterface.BUTTON_NEGATIVE, "Cancelar", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            fileDownloadTask.cancel();
                        }
                    });
            progresoSubida.show();
            bajandoDatos=true;
        } else {
            if (taskSnapshot.getTotalByteCount()>0)
                progresoSubida.setMessage("Espere... " +
                        String.valueOf(100*taskSnapshot.getBytesTransferred() /taskSnapshot.getTotalByteCount())+"%");
        }
    }

    private void upload_error(Exception exception){
        subiendoDatos=false;
        Comun.mostrarDialogo(getApplicationContext(), "Ha ocurrido un error al subir la imagen o " +
            "el usuario ha cancelado la subida.",evento);
    }

    private void upload_pausa(UploadTask.TaskSnapshot taskSnapshot){
        subiendoDatos=false;
        Comun.mostrarDialogo(getApplicationContext(), "La subida ha sido pausada.",evento);
    }



    public void descargarDeFirebaseStorage(String fichero) {
        imagenRefDown = getStorageReference().child(fichero);
        File rootPath = new File(Environment.getExternalStorageDirectory(), "Eventos");
        if (!rootPath.exists()) {
            rootPath.mkdirs();
        }
       final File localFile = new File(rootPath, evento + ".jpg");

        fileDownloadTask = (FileDownloadTask) imagenRefDown.getFile(localFile)
            .addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    progresoSubida.dismiss();
                    bajandoDatos=false;
                    mostrarDialogo(getApplicationContext(), "Fichero descargado con éxito: " ,evento);
                }
            }).addOnProgressListener(new OnProgressListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                    public void onProgress(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    download_progreso(taskSnapshot);
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception exception) {
                    bajandoDatos=false;
                    mostrarDialogo(getApplicationContext(), "Error al descargar el fichero.", evento);
                }
            })
            .addOnPausedListener(new OnPausedListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onPaused(FileDownloadTask.TaskSnapshot taskSnapshot) {
                //upload_pausa(taskSnapshot);
                    bajandoDatos=false;
                }
            });
    }


    //destruye el dialogo de progreso si se esta mostrando
    @Override
    protected void onDestroy()
    {
        if (progresoSubida != null && progresoSubida.isShowing()) {
            progresoSubida.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume(){
        super.onResume();
        mTrace.start();
    }

    @Override
    protected void onStop(){
        super.onStop();
        mTrace.stop();
    }
}


