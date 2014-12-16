package edu.ucsd.rendezvous;

import com.google.android.glass.timeline.DirectRenderingCallback;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

/**
 * Renders a static google map and information of friends' distances on the timeline.
 */
public class RendezvousDrawer implements DirectRenderingCallback {

    private static final String TAG = RendezvousDrawer.class.getSimpleName();
    private final RendezvousView mRendezvousView;

    private boolean mRenderingPaused;
    private SurfaceHolder mHolder;

    private final RendezvousView.Listener mmapListener = new RendezvousView.Listener() {

        @Override
        public void onChange() {
            if (mHolder != null) {
                draw(mRendezvousView);
            }
        }
    };

    public RendezvousDrawer(Context context) {
        this(new RendezvousView(context));
    }

    public RendezvousDrawer(RendezvousView RendezvousView) {
        mRendezvousView = RendezvousView;
        mRendezvousView.setListener(mmapListener);
    }

    /**
     * Keeps the created {@link SurfaceHolder} and updates this class' rendering state.
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // The creation of a new Surface implicitly resumes the rendering.
        mRenderingPaused = false;
        mHolder = holder;
        updateRenderingState();
    }

    /**
     * Removes the {@link SurfaceHolder} usedff for drawing and stops rendering.
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mHolder = null;
        updateRenderingState();
    }

    /**
     * Updates this class' rendering state according to the provided {@code paused} flag.
     */
    @Override
    public void renderingPaused(SurfaceHolder holder, boolean paused) {
        mRenderingPaused = paused;
        updateRenderingState();
    }

    /**
     * Uses the provided {@code width} and {@code height} to measure and layout the inflated
     * {@link RendezvousView}.
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Measure and layout the view with the canvas dimensions.
        int measuredWidth = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int measuredHeight = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);

        mRendezvousView.measure(measuredWidth, measuredHeight);
        mRendezvousView.layout(
                0, 0, mRendezvousView.getMeasuredWidth(), mRendezvousView.getMeasuredHeight());
    }

    /**
     * Starts or stops rendering according to the {@link LiveCard}'s state.
     */
    private void updateRenderingState() {
        if (mHolder != null && !mRenderingPaused)
        {
            mRendezvousView.start();
        }
    }

    /**
     * Draws the view in the SurfaceHolder's canvas.
     */
    private void draw(View view) {
        Canvas canvas;
        try {
            canvas = mHolder.lockCanvas();
        } catch (Exception e) {
            Log.e(TAG, "Unable to lock canvas: " + e);
            return;
        }
        if (canvas != null) {
            view.draw(canvas);
            mHolder.unlockCanvasAndPost(canvas);
        }
    }
}
