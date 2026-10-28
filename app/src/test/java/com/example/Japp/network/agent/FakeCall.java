package com.example.Japp.network.agent;

import java.io.IOException;
import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

final class FakeCall<T> implements Call<T> {
    Callback<T> callback;
    boolean cancelled;
    boolean executed;
    @Override public Response<T> execute() throws IOException { throw new UnsupportedOperationException(); }
    @Override public void enqueue(Callback<T> callback) { this.callback = callback; executed = true; }
    @Override public boolean isExecuted() { return executed; }
    @Override public void cancel() { cancelled = true; }
    @Override public boolean isCanceled() { return cancelled; }
    @Override public Call<T> clone() { return new FakeCall<>(); }
    @Override public Request request() { return new Request.Builder().url("http://example.test/").build(); }
    @Override public Timeout timeout() { return new Timeout(); }
    void reply(T data) { callback.onResponse(this, Response.success(data)); }
    void respond(Response<T> response) { callback.onResponse(this, response); }
    void failure() { callback.onFailure(this, new IOException("test network interruption")); }
}
