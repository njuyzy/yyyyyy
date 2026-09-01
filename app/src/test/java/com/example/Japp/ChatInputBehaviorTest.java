package com.example.Japp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ChatInputBehaviorTest {

    @Test
    public void chatInputResizesForKeyboardAndHandlesImeSend() throws Exception {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        String manifest = read(projectRoot.resolve("app/src/main/AndroidManifest.xml"));
        String activity = read(projectRoot.resolve(
                "app/src/main/java/com/example/Japp/Chat/chatActivity.java"));

        assertTrue(manifest.matches("(?s).*android:name=\"\\.Chat\\.chatActivity\".*?"
                + "android:windowSoftInputMode=\"adjustPan\".*"));
        assertFalse(activity.contains("DisplayCutoutAdapter.apply(this)"));
        assertTrue(activity.contains("edtInput.setOnEditorActionListener"));
        assertTrue(activity.contains("EditorInfo.IME_ACTION_SEND"));
        assertFalse(activity.contains("addOnLayoutChangeListener"));
        assertFalse(activity.contains("setOnFocusChangeListener"));
        assertFalse(activity.contains("scrollToLatestMessage"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
