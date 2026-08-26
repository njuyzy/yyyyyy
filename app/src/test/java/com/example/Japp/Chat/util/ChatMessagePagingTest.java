package com.example.Japp.Chat.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.Japp.network.models.ServerChatMessage;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ChatMessagePagingTest {

    @Test
    public void initialPageReturnsLatestThirtyInAscendingOrder() {
        List<ServerChatMessage> source = messages(1, 75);

        List<ServerChatMessage> page = ChatMessagePaging.pageBefore(source, null);

        assertEquals(30, page.size());
        assertEquals(46L, page.get(0).getId());
        assertEquals(75L, page.get(29).getId());
        assertTrue(ChatMessagePaging.mayHaveOlder(source, page));
    }

    @Test
    public void nextPageUsesExclusiveBeforeId() {
        List<ServerChatMessage> source = messages(1, 75);

        List<ServerChatMessage> page = ChatMessagePaging.pageBefore(source, 46L);

        assertEquals(30, page.size());
        assertEquals(16L, page.get(0).getId());
        assertEquals(45L, page.get(29).getId());
    }

    @Test
    public void finalPartialPageStopsPaging() {
        List<ServerChatMessage> source = messages(1, 75);

        List<ServerChatMessage> page = ChatMessagePaging.pageBefore(source, 16L);

        assertEquals(15, page.size());
        assertEquals(1L, page.get(0).getId());
        assertFalse(ChatMessagePaging.mayHaveOlder(source, page));
    }

    @Test
    public void mergeDeduplicatesByMessageId() {
        List<ServerChatMessage> merged = ChatMessagePaging.merge(
                messages(1, 30), messages(21, 50));

        assertEquals(50, merged.size());
        assertEquals(1L, merged.get(0).getId());
        assertEquals(50L, merged.get(49).getId());
    }

    private static List<ServerChatMessage> messages(long from, long to) {
        List<ServerChatMessage> result = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            result.add(new ServerChatMessage(id, 1L, 1,
                    "message-" + id, "TEXT", null));
        }
        return result;
    }
}
