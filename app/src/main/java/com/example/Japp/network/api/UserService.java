package com.example.Japp.network.api;

import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.AccountLeaderProfile;
import com.example.Japp.network.models.AccountTagPref;
import com.example.Japp.network.models.LoginResponse;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.network.models.requests.IntroRequest;
import com.example.Japp.network.models.requests.LoginRequest;
import com.example.Japp.network.models.requests.RegisterRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface UserService {

    @POST("login")
    Call<Result<LoginResponse>> login(@Body LoginRequest request);

    @POST("register")
    Call<Result> register(@Body RegisterRequest request);

    @GET("accounts/{id}")
    Call<Result<Account>> getAccount(@Path("id") int id);

    @GET("accounts/{id}/leaderProfile")
    Call<Result<AccountLeaderProfile>> getLeaderProfile(@Path("id") int id);

    @POST("accounts/{id}/intro")
    Call<Result> updateIntro(@Path("id") int id, @Body IntroRequest request);

    @GET("accounts/{id}/tagPrefs")
    Call<Result<List<AccountTagPref>>> getTagPrefs(@Path("id") int id);

    @POST("accounts/{id}/tagPrefs")
    Call<Result> updateTagPrefs(@Path("id") int id, @Body List<AccountTagPref> prefs);

    @GET("projects")
    Call<Result<List<Project>>> getProjects(@Query("accountId") int accountId,
                                            @Query("pageNum") int pageNum,
                                            @Query("pageSize") int pageSize);

    @GET("routes/{id}")
    Call<Result<List<RouteNode>>> getRouteNodes(@Path("id") int routeId);
}