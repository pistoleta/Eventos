package org.example.eventos;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Created by pistoleta on 6/3/18.
 */

public class EventosFCMInstanceIDService extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {//se llama cuando el identificador se ha vuelto invalido
        String idPush;
        idPush = FirebaseInstanceId.getInstance().getToken(); //dame identificdor
        Comun.guardarIdRegistro(getApplicationContext(), idPush);
    }
}
