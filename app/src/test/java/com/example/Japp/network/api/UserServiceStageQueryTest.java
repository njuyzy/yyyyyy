package com.example.Japp.network.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import okhttp3.HttpUrl;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UserServiceStageQueryTest {

    @Test
    public void myProjectsSendsAcceptedStageWithoutLegacyStatus() {
        UserService service = new Retrofit.Builder()
                .baseUrl("https://example.test/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UserService.class);

        HttpUrl url = service.getMyProjects("LEADING", null, "ACCEPTED", 1, 15)
                .request().url();

        assertEquals("LEADING", url.queryParameter("relation"));
        assertEquals("ACCEPTED", url.queryParameter("stage"));
        assertNull(url.queryParameter("status"));
    }
}
