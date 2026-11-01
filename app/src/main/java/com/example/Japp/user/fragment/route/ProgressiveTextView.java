package com.example.Japp.user.fragment.route;

import android.animation.ValueAnimator;
import android.content.Context;
import android.icu.text.BreakIterator;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.ColorUtils;

import com.example.Japp.R;

import java.util.ArrayList;
import java.util.List;

/** Reveals complete Unicode characters, with a separate color fade for each one. */
public final class ProgressiveTextView extends AppCompatTextView {
    private static final long FADE_MILLIS = 180;

    static final class Reveal {
        final int[] ends;
        final long startsAt;
        final long stepMillis;
        final long finishesAt;

        Reveal(String text, long startsAt) {
            this.startsAt = startsAt;
            BreakIterator iterator = BreakIterator.getCharacterInstance();
            iterator.setText(text);
            List<Integer> boundaries = new ArrayList<>();
            for (int end = iterator.next(); end != BreakIterator.DONE; end = iterator.next())
                boundaries.add(end);
            ends = new int[boundaries.size()];
            for (int i = 0; i < ends.length; i++) ends[i] = boundaries.get(i);
            stepMillis = Math.max(6, Math.min(24, 12_000 / Math.max(1, ends.length)));
            finishesAt = startsAt + Math.max(0, ends.length - 1) * stepMillis + FADE_MILLIS;
        }
    }

    private CharSequence fullText = "";
    private Reveal reveal;
    private Runnable onTextGrowing;
    private boolean paused;
    private int visibleEnd;
    private final Runnable frame = this::renderFrame;

    public ProgressiveTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    void bind(CharSequence text, @Nullable Reveal reveal, @Nullable Runnable onTextGrowing) {
        removeCallbacks(frame);
        this.fullText = text;
        this.reveal = reveal;
        this.onTextGrowing = onTextGrowing;
        visibleEnd = 0;
        renderFrame();
    }

    private void renderFrame() {
        removeCallbacks(frame);
        if (reveal == null || !ValueAnimator.areAnimatorsEnabled()
                || SystemClock.uptimeMillis() >= reveal.finishesAt || reveal.ends.length == 0) {
            setText(fullText);
            reveal = null;
            return;
        }
        long elapsed = SystemClock.uptimeMillis() - reveal.startsAt;
        int count = elapsed < 0 ? 0
                : (int) Math.min(reveal.ends.length, elapsed / reveal.stepMillis + 1);
        int end = count == 0 ? 0 : reveal.ends[count - 1];
        SpannableString visible = new SpannableString(fullText.subSequence(0, end));
        int pale = getContext().getColor(R.color.route_text_hint);
        int solid = getCurrentTextColor();
        int fadingStart = (int) Math.max(0, (elapsed - FADE_MILLIS) / reveal.stepMillis);
        for (int i = fadingStart; i < count; i++) {
            float fraction = Math.min(1f, Math.max(0f,
                    (elapsed - i * reveal.stepMillis) / (float) FADE_MILLIS));
            visible.setSpan(new ForegroundColorSpan(ColorUtils.blendARGB(pale, solid, fraction)),
                    i == 0 ? 0 : reveal.ends[i - 1], reveal.ends[i], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (end != visibleEnd && onTextGrowing != null) onTextGrowing.run();
        visibleEnd = end;
        setText(visible);
        if (isAttachedToWindow() && !paused) {
            if (elapsed < 0) postDelayed(frame, Math.min(-elapsed, 100));
            else postOnAnimation(frame);
        }
    }

    void pauseReveal() {
        paused = true;
        removeCallbacks(frame);
    }

    void resumeReveal() {
        paused = false;
        renderFrame();
    }

    void clearReveal() {
        removeCallbacks(frame);
        reveal = null;
        onTextGrowing = null;
        fullText = "";
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        renderFrame();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(frame);
        super.onDetachedFromWindow();
    }
}
