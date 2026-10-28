package com.example.Japp.network;

import static org.junit.Assert.assertEquals;

import okhttp3.MediaType;
import okhttp3.ResponseBody;

import org.junit.Test;

import retrofit2.Response;

public class ResponseMessageReaderTest {

    @Test
    public void readsBackendMessageFromHttp400ErrorBody() {
        ResponseBody body = ResponseBody.create(
                MediaType.get("application/json"),
                "{\"code\":0,\"msg\":\"放弃原因不能少于 5 个字\",\"data\":null}");

        assertEquals("放弃原因不能少于 5 个字",
                ResponseMessageReader.read(Response.error(400, body), "请求失败"));
    }

    @Test
    public void readsBackendMessageFromHttp403ErrorBody() {
        ResponseBody body = ResponseBody.create(
                MediaType.get("application/json"),
                "{\"code\":0,\"msg\":\"距出发不足 6 小时，还剩 359 分钟\",\"data\":null}");

        assertEquals("距出发不足 6 小时，还剩 359 分钟",
                ResponseMessageReader.read(Response.error(403, body), "请求失败"));
    }

    @Test
    public void malformedErrorBodyUsesFallback() {
        ResponseBody body = ResponseBody.create(MediaType.get("text/plain"), "gateway error");

        assertEquals("请求失败",
                ResponseMessageReader.read(Response.error(500, body), "请求失败"));
    }
}
