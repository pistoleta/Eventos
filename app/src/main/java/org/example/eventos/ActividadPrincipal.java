package org.example.eventos;

import android.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.firebase.ui.firestore.FirestoreRecyclerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;

import static org.example.eventos.Comun.mFirebaseAnalytics;
import static org.example.eventos.Comun.mFirebaseRemoteConfig;
import static org.example.eventos.Comun.storage;

import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;

public class ActividadPrincipal extends AppCompatActivity {

    private AdaptadorEventos adaptador;
    private static ActividadPrincipal current;

    public static ActividadPrincipal getCurrentContext() {
        return current;
    }

        @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.actividad_principal);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

       // EventosFirestore.crearEventos(); comento para que no se creen mas eventos
        Query query = FirebaseFirestore.getInstance().collection(EventosFirestore.EVENTOS)
                .limit(50);

        FirestoreRecyclerOptions<Evento> opciones = new FirestoreRecyclerOptions
                .Builder<Evento>().setQuery(query, Evento.class).build();
        adaptador = new AdaptadorEventos(opciones);
        final RecyclerView recyclerView = findViewById(R.id.reciclerViewEventos);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adaptador);

        final SharedPreferences preferencias =
                getApplicationContext().getSharedPreferences("Temas", Context.MODE_PRIVATE);
        if (preferencias.getBoolean("Inicializado", false)==false){
            final SharedPreferences prefs =
                    getApplicationContext().getSharedPreferences("Temas", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("Inicializado", true);
            editor.commit();
            FirebaseMessaging.getInstance().subscribeToTopic("Todos");
        }

        adaptador.setOnItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int position = recyclerView.getChildAdapterPosition(view);
                Evento currentItem = (Evento) adaptador.getItem(position);
                String idEvento = adaptador.getSnapshots().getSnapshot(position).getId();
                Context context = getAppContext();
                Intent intent = new Intent(context, EventoDetalles.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("evento", idEvento); context.startActivity(intent);
        } });

        Comun.storage = FirebaseStorage.getInstance();
        Comun.storageRef = storage.getReferenceFromUrl("gs://eventos-99e65.appspot.com/");
        Comun.mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        String[] PERMISOS = {android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.GET_ACCOUNTS,
                            android.Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_NETWORK_STATE};
        ActivityCompat.requestPermissions(this, PERMISOS, 1);

        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                            .setDeveloperModeEnabled(BuildConfig.DEBUG)
                            .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        mFirebaseRemoteConfig.setDefaults(R.xml.remote_config_default);

        long cacheExpiration = 3600;
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        mFirebaseRemoteConfig.activateFetched();
                        getColorFondo();
                        getAcercaDe();
                        getPerformanceMonitoring();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Comun.colorFondo=mFirebaseRemoteConfig.getString("color_fondo");
                        Comun.acercaDe=mFirebaseRemoteConfig.getBoolean("acerca_de");
                        FirebasePerformance.getInstance().setPerformanceCollectionEnabled(mFirebaseRemoteConfig.getBoolean("PerformanceMonitoring"));
                    }
                });

        Fabric.with(this, new Crashlytics());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Bundle extras = getIntent().getExtras();
        if (extras!=null && extras.keySet().size()>4) {
            String evento="";
            evento ="Evento: "+extras.getString("evento")+ "\n";
            evento = evento + "Día: "+ extras.getString("dia")+ "\n";
            evento = evento +"Ciudad: "+extras.getString("ciudad")+ "\n";
            evento = evento +"Comentario: "+extras.getString("comentario");
            Comun.mostrarDialogo(getApplicationContext(), evento,"");
            for (String key : extras.keySet()) {
                getIntent().removeExtra(key);
            }
            extras = null; }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_actividad_principal, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        if (id == R.id.action_temas) {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "suscripciones");
            mFirebaseAnalytics.logEvent("menus",bundle);  //capturamos evento
            Intent intent = new Intent(getBaseContext(), Temas.class);
            startActivity(intent);
            return true;
        }
        if (id == R.id.action_error) {
            Crashlytics.getInstance().crash();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                if (!(grantResults.length > 0 &&  grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(ActividadPrincipal.this,
                            "Has denegado algún permiso de la aplicación.", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    public static Context getAppContext(){
        return ActividadPrincipal.getCurrentContext();
    }

    @Override public void onStart() {
        super.onStart();
        adaptador.startListening();
        current =this;
    }
    @Override public void onStop() {
        super.onStop();
        adaptador.stopListening();
    }

    private void getColorFondo() {
        Comun.colorFondo = mFirebaseRemoteConfig.getString("color_fondo");
    }
    private void getAcercaDe() {
        Comun.acercaDe = mFirebaseRemoteConfig.getBoolean("acerca_de"); }
    private void getPerformanceMonitoring(){
        FirebasePerformance.getInstance().setPerformanceCollectionEnabled(mFirebaseRemoteConfig.getBoolean("PerformanceMonitoring"));
    }
}
