package com.example.Japp.network.api;

import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.AccountLeaderProfile;
import com.example.Japp.network.models.AccountTagPref;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.LoginResponse;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Region;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.JsonElement;
import com.example.Japp.network.models.requests.CreateProjectRequest;
import com.example.Japp.network.models.requests.CreateSessionRequest;
import com.example.Japp.network.models.requests.PlanRouteRequest;
import com.example.Japp.network.models.requests.IntroRequest;
import com.example.Japp.network.models.requests.UpdateUsernameRequest;
import com.example.Japp.network.models.requests.LoginRequest;
import com.example.Japp.network.models.requests.RegisterRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;
import okhttp3.MultipartBody;

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

    @POST("accounts/{id}/username")
    Call<Result> updateUsername(@Path("id") int id, @Body UpdateUsernameRequest request);

    @GET("accounts/{id}/tagPrefs")
    Call<Result<List<AccountTagPref>>> getTagPrefs(@Path("id") int id);

    @POST("accounts/{id}/tagPrefs")
    Call<Result> updateTagPrefs(@Path("id") int id, @Body List<AccountTagPref> prefs);

    @Multipart
    @POST
    Call<Result<String>> uploadAvatar(@Url String url, @Part MultipartBody.Part image);

    @GET("projects")
    Call<Result<List<Project>>> getProjects(@Query("accountId") int accountId,
                                            @Query("pageNum") int pageNum,
                                            @Query("pageSize") int pageSize);

    @GET("projects/filter")
    Call<Result<List<Project>>> filterProjects(@Query("accountId") int accountId,
                                              @Query("pageNum") int pageNum,
                                              @Query("pageSize") int pageSize,
                                              @Query("keyword") String keyword,
                                              @Query("regionCode") String regionCode,
                                              @Query("tag") String tag,
                                              @Query("status") String status,
                                              @Query("departureDateFrom") String departureDateFrom,
                                              @Query("departureDateTo") String departureDateTo,
                                              @Query("hasLeader") Boolean hasLeader,
                                              @Query("onlyAvailable") Boolean onlyAvailable);

    @GET("projects/{id}")
    Call<Result<Project>> getProject(@Path("id") int id);

    @GET("regions/provinces")
    Call<Result<List<Region>>> getProvinces();

    @GET("regions/children")
    Call<Result<List<Region>>> getRegionChildren(@Query("parentAdcode") String parentAdcode);

    @POST("projects")
    Call<Result> createProject(@Body CreateProjectRequest request);

    @POST("routes/plan")
    Call<Result<JsonElement>> planRouteByAi(@Query("accountId") int accountId,
                                            @Query("memoryId") String memoryId,
                                            @Query("text") String text,
                                            @Body PlanRouteRequest request);

    @GET("routes/{id}")
    Call<Result<List<RouteNode>>> getRouteNodes(@Path("id") int routeId);

    @GET("routes/{id}")
    Call<Result<JsonElement>> getRouteNodesRaw(@Path("id") int routeId);

    @POST("projects/{id}/leader")
    Call<Result> assignLeader(@Path("id") int projectId,
                             @Body com.example.Japp.network.models.requests.AssignLeaderRequest request);

    @POST("projects/{id}/join")
    Call<Result> joinProject(@Path("id") int projectId);

    @POST("chat/sessions")
    Call<Result<ChatSession>> createChatSession(@Body CreateSessionRequest request);
}
