package com.example.Japp.network.api;

import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.AccountLeaderProfile;
import com.example.Japp.network.models.AccountTagPref;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.ChatGroupMember;
import com.example.Japp.network.models.ServerChatMessage;
import com.example.Japp.network.models.LoginResponse;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.ProjectPage;
import com.example.Japp.network.models.Region;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.JsonElement;
import com.example.Japp.network.models.requests.CreateProjectRequest;
import com.example.Japp.network.models.requests.CreateSessionRequest;
import com.example.Japp.network.models.requests.IntroRequest;
import com.example.Japp.network.models.requests.JoinProjectRequest;
import com.example.Japp.network.models.requests.UpdateUsernameRequest;
import com.example.Japp.network.models.requests.LoginRequest;
import com.example.Japp.network.models.requests.RegisterRequest;
import com.example.Japp.network.models.requests.SendChatMessageRequest;

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

    @GET("projects/{id}")
    Call<Result<Project>> getProject(@Path("id") int projectId);

    @GET("projects/available")
    Call<Result<ProjectPage>> getAvailableProjects(@Query("pageNum") int pageNum,
                                                   @Query("pageSize") int pageSize);

    @GET("projects/filter")
    Call<Result<List<Project>>> getJoinableProjects(@Query("accountId") int accountId,
                                                    @Query("pageNum") int pageNum,
                                                    @Query("pageSize") int pageSize,
                                                    @Query("onlyAvailable") boolean onlyAvailable);

    @GET("projects/filter")
    Call<Result<List<Project>>> getOwnedJoinableProjects(@Query("accountId") int accountId,
                                                         @Query("ownerAccountId") int ownerAccountId,
                                                         @Query("pageNum") int pageNum,
                                                         @Query("pageSize") int pageSize,
                                                         @Query("hasLeader") boolean hasLeader,
                                                         @Query("onlyAvailable") boolean onlyAvailable);

    @GET("projects/filter")
    Call<Result<List<Project>>> getOwnedProjects(@Query("accountId") int accountId,
                                                 @Query("ownerAccountId") int ownerAccountId,
                                                 @Query("pageNum") int pageNum,
                                                 @Query("pageSize") int pageSize,
                                                 @Query("onlyAvailable") boolean onlyAvailable);

    @GET("regions/provinces")
    Call<Result<List<Region>>> getProvinces();

    @GET("regions/children")
    Call<Result<List<Region>>> getRegionChildren(
            @Query("parentAdcode") String parentAdcode);

    @GET("projects/filter")
    Call<Result<List<Project>>> filterProjects(@Query("accountId") int accountId,
                                              @Query("pageNum") int pageNum,
                                              @Query("pageSize") int pageSize,
                                              @Query("keyword") String keyword,
                                              @Query("regionCode") String regionAdcode,
                                              @Query("tag") String tag,
                                              @Query("status") String status,
                                              @Query("departureDateFrom") String departureDateFrom,
                                              @Query("departureDateTo") String departureDateTo,
                                              @Query("hasLeader") Boolean hasLeader,
                                              @Query("onlyAvailable") Boolean joinableOnly);

    @POST("projects")
    Call<Result> createProject(@Body CreateProjectRequest request);

    @POST("routes/{id}/publish")
    Call<Result<JsonElement>> publishRoute(@Path("id") int routeId,
                                           @Body CreateProjectRequest request);

    // 后端约定：POST /routes/ai/{memoryId}?message=...&accountId=...
    // 成功时 data 为路线 ID（数字）
    @POST("routes/ai/{memoryId}")
    Call<Result<JsonElement>> planRouteByAi(@Path("memoryId") String memoryId,
                                            @Query("message") String message,
                                            @Query("accountId") Integer accountId);

    @POST("routes/manual")
    Call<Result<JsonElement>> createManualRoute(@Body List<RouteNode> routeNodes);

    @GET("routes/{id}")
    Call<Result<List<RouteNode>>> getRouteNodes(@Path("id") int routeId);

    @GET("routes/{id}")
    Call<Result<JsonElement>> getRouteNodesRaw(@Path("id") int routeId);

    @POST("projects/{id}/leader")
    Call<Result> assignLeader(@Path("id") int projectId,
                             @Body com.example.Japp.network.models.requests.AssignLeaderRequest request);

    @POST("projects/{id}/join")
    Call<Result> joinProject(@Path("id") int projectId, @Body JoinProjectRequest request);

    @POST("projects/{id}/accept")
    Call<Result> acceptProject(@Path("id") int projectId);

    @POST("chat/sessions")
    Call<Result<ChatSession>> createChatSession(@Body CreateSessionRequest request);

    @GET("chat/sessions")
    Call<Result<List<ChatSession>>> getChatSessions();

    @GET("chat/sessions/{sessionId}/messages")
    Call<Result<List<ServerChatMessage>>> getChatMessages(@Path("sessionId") long sessionId);

    @GET("chat/sessions/{sessionId}/members")
    Call<Result<List<ChatGroupMember>>> getChatMembers(@Path("sessionId") long sessionId);

    @POST("chat/messages")
    Call<Result<Long>> sendChatMessage(@Body SendChatMessageRequest request);
}
