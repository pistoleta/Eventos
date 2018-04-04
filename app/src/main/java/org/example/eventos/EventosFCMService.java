package org.example.eventos;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Created by pistoleta on 6/3/18.
 */

public class EventosFCMService extends FirebaseMessagingService {
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        if (remoteMessage.getData().size() > 0) {
            String evento="";
            evento ="Evento: "+remoteMessage.getData().get("evento")+ "\n";
            evento = evento + "Día: "+ remoteMessage.getData().get("dia")+ "\n";
            evento = evento +"Ciudad: "+ remoteMessage.getData().get("ciudad")+"\n";
            evento = evento +"Comentario: " +remoteMessage.getData().get("comentario");
            String idEvento=remoteMessage.getData().get("evento");
            Comun.mostrarDialogo(getApplicationContext(), evento, idEvento);
            Log.d("AAA","AQUIIIIII");
            Log.d("EVENTOSFCM","evento "+evento);

        } else {
            if (remoteMessage.getNotification() != null) {
                Comun.mostrarDialogo(getApplicationContext(), remoteMessage.getNotification().getBody(),"");
            }
        }
    }
}