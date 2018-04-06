package org.example.eventos;

import org.example.eventos.POJOs.InstagramResponse;
import org.example.eventos.POJOs.User;

import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

import retrofit2.Call;

public interface RetrofitService {

    @GET("v1/users/self/")
    Call<InstagramResponse> getUsuario(@Query("access_token") String access_token);
}
