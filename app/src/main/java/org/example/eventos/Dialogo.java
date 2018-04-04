package org.example.eventos;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

/**
 * Created by pistoleta on 6/3/18.
 */

public class Dialogo extends AppCompatActivity{
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle extras = getIntent().getExtras();
        if (getIntent().hasExtra("mensaje")) {
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Mensaje:");
            alertDialog.setMessage(extras.getString("mensaje"));
            alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "CERRAR",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            finish();

                            if (getIntent().hasExtra("evento")) {
                                String idEvento = extras.getString("evento");
                                Context context = ActividadPrincipal.getAppContext();
                                Intent intent = new Intent(context, EventoDetalles.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra("evento", idEvento);
                                context.startActivity(intent);
                            }
                        }
                    });
            alertDialog.show();
            extras.remove("mensaje");
        }
    }
}
