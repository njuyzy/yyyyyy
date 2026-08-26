package com.example.Japp.network.api;

import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.AccountLeaderProfile;
import com.example.Japp.network.models.AccountTagPref;
import com.example.Japp.network.models.ChatSession;
import com.example.Japp.network.models.ChatGroupMember;
import com.example.Japp.network.models.LeaderProfile;
import com.example.Japp.network.models.LeaderReview;
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
import com.example.Japp.network.models.requests.UpdateAccountProfileRequest;
import com.example.Japp.network.models.requests.UpdateAccountRoleRequest;
import com.example.Japp.network.models.requests.UpdatePasswordRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.PUT;
import retrofit2.http.Url;
import okhttp3.MultipartBody;

public interface UserService {

    @POST("login")
    Call<Result<LoginResponse>> login(@Body LoginRequest request);

    @POST("register")
    Call<Result> register(@Body RegisterRequest request);

    @GET("accounts/{id}")
    Call<Result<Account>> getAccount(@Path("id") int id);

    @PUT("accounts/{id}")
    Call<Result<Account>> updateAccountProfile(@Path("id") int id,
                                               @Body UpdateAccountProfileRequest request);

    @PUT("accounts/{id}/role")
    Call<Result<JsonElement>> updateAccountRole(@Path("id") int id,
                                                @Body UpdateAccountRoleRequest request);

    @PUT("accounts/{id}/password")
    Call<Result> updatePassword(@Path("id") int id,
                                @Body UpdatePasswordRequest request);

    @PUT("accounts/{id}/userIntro")
    Call<Result> updateUserIntro(@Path("id") int id,
                                 @Body IntroRequest request);

    @GET("accounts/{id}/leaderProfile")
    Call<Result<AccountLeaderProfile>> getLeaderProfile(@Path("id") int id);

    @GET("leader/profile")
    Call<Result<LeaderProfile>> getLeaderDashboard();

    /**
     * 领队收到的用户评价列表（USER_TO_LEADER），按时间倒序。
     * 响应是普通列表（非分页），服务端按 pageNum/pageSize 切片即可。
     */
    @GET("leader/reviews")
    Call<Result<List<LeaderReview>>> getLeaderReviews(@Query("pageNum") int pageNum,
                                                     @Query("pageSize") int pageSize);

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

    @Multipart
    @POST("upload")
    Call<Result<String>> uploadFile(@Part MultipartBody.Part image);

    @GET("projects")
    Call<Result<List<Project>>> getProjects(@Query("accountId") int accountId,
                                            @Query("pageNum") int pageNum,
                                            @Query("pageSize") int pageSize);

    @GET("projects/{id}")
    Call<Result<Project>> getProject(@Path("id") int projectId);

    @GET("projects/available")
    Call<Result<ProjectPage>> getAvailableProjects(@Query("pageNum") int pageNum,
                                                   @Query("pageSize") int pageSize);

    @GET("projects/mine")
    Call<Result<ProjectPage>> getMyProjects(@Query("relation") String relation,
                                            @Query("status") String status,
                                            @Query("pageNum") int pageNum,
                                            @Query("pageSize") int pageSize);

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
                                              @Query("ownerAccountId") Integer ownerAccountId,
                                              @Query("leaderAccountId") Integer leaderAccountId,
                                              @Query("hasLeader") Boolean hasLeader,
                                              @Query("onlyAvailable") Boolean joinableOnly);

    @POST("projects")
    Call<Result> createProject(@Body CreateProjectRequest request);

    @POST("routes/{id}/publish")
    Call<Result<JsonElement>> publishRoute(@Path("id") int routeId,
                                           @Body CreateProjectRequest request);

    // 统一 AI 路线接口：不传请求体时生成路线，传入已有地点时优化路线。
    // 两种方式成功时 data 均为路线 ID（数字）。
    @POST("routes/ai/{memoryId}")
    Call<Result<JsonElement>> planRouteByAi(@Path("memoryId") String memoryId,
                                            @Query("message") String message,
                                            @Query("accountId") Integer accountId);

    @POST("routes/ai/{memoryId}")
    Call<Result<JsonElement>> optimizeRouteByAi(@Path("memoryId") String memoryId,
                                                @Query("message") String message,
                                                @Query("accountId") Integer accountId,
                                                @Body List<RouteNode> routeNodes);

    @POST("routes/manual")
    Call<Result<JsonElement>> createManualRoute(@Body List<RouteNode> routeNodes);

    @POST("attractions/sync/{poiId}")
    Call<Result<JsonElement>> syncAttraction(@Path("poiId") String poiId);

    @GET("routes/{id}")
    Call<Result<List<RouteNode>>> getRouteNodes(@Path("id") int routeId);

    @GET("routes/{id}")
    Call<Result<JsonElement>> getRouteNodesRaw(@Path("id") int routeId);

    @POST("projects/{id}/leader")
    Call<Result> assignLeader(@Path("id") int projectId,
                             @Body com.example.Japp.network.models.requests.AssignLeaderRequest request);

    @POST("projects/{id}/join")
    Call<Result> joinProject(@Path("id") int projectId, @Body JoinProjectRequest request);

    @POST("projects/{id}/quit")
    Call<Result> quitProject(@Path("id") int projectId);

    @POST("projects/{id}/accept")
    Call<Result> acceptProject(@Path("id") int projectId);

    @POST("projects/{id}/abandon")
    Call<Result> abandonProject(@Path("id") int projectId);

    @POST("projects/{id}/status")
    Call<Result> updateProjectStatus(@Path("id") int projectId,
                                     @Query("status") String status);

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
