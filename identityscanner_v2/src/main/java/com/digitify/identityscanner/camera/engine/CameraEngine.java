package com.digitify.identityscanner.camera.engine;

import android.content.Context;
import android.graphics.PointF;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.digitify.identityscanner.camera.CameraException;
import com.digitify.identityscanner.camera.CameraLogger;
import com.digitify.identityscanner.camera.CameraOptions;
import com.digitify.identityscanner.camera.PictureResult;
import com.digitify.identityscanner.camera.controls.Facing;
import com.digitify.identityscanner.camera.controls.Flash;
import com.digitify.identityscanner.camera.controls.Hdr;
import com.digitify.identityscanner.camera.controls.Mode;
import com.digitify.identityscanner.camera.controls.WhiteBalance;
import com.digitify.identityscanner.camera.engine.offset.Angles;
import com.digitify.identityscanner.camera.engine.offset.Reference;
import com.digitify.identityscanner.camera.frame.Frame;
import com.digitify.identityscanner.camera.frame.FrameManager;
import com.digitify.identityscanner.camera.gesture.Gesture;
import com.digitify.identityscanner.camera.internal.utils.Op;
import com.digitify.identityscanner.camera.internal.utils.WorkerHandler;
import com.digitify.identityscanner.camera.overlay.Overlay;
import com.digitify.identityscanner.camera.picture.PictureRecorder;
import com.digitify.identityscanner.camera.preview.CameraPreview;
import com.digitify.identityscanner.camera.size.AspectRatio;
import com.digitify.identityscanner.camera.size.Size;
import com.digitify.identityscanner.camera.size.SizeSelector;
import com.digitify.identityscanner.camera.size.SizeSelectors;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


/**
 * PROCESS
 * Setting up the Camera is usually a 4 steps process:
 * 1. Setting up the Surface. Done by {@link CameraPreview}.
 * 2. Starting the camera. Done by us. See {@link #startEngine()}, {@link #onStartEngine()}.
 * 3. Binding the camera to the surface. Done by us. See {@link #startBind()},
 * {@link #onStartBind()}
 * 4. Streaming the camera preview. Done by us. See {@link #startPreview()},
 * {@link #onStartPreview()}
 * <p>
 * The first two steps can actually happen at the same time, anyway
 * the order is not guaranteed, we just get a callback from the Preview when 1 happens.
 * So at the end of both step 1 and 2, the engine should check if both have
 * been performed and trigger the steps 3 and 4.
 * <p>
 * We use an abstraction for each step called {@link Step} that manages the state of
 * each step and ensures that start and stop operations, for each step, are never called if the
 * previous one has not ended.
 * <p>
 * <p>
 * STATE
 * We only expose generic {@link #start()} and {@link #stop()} calls to the outside.
 * The external users of this class are most likely interested in whether we have completed step 2
 * or not, since that tells us if we can act on the camera or not, rather than knowing about
 * steps 3 and 4.
 * <p>
 * So in the {@link CameraEngine} notation,
 * - {@link #start()}: ASYNC - starts the engine (S2). When possible, at a later time,
 * S3 and S4 are also performed.
 * - {@link #stop()}: ASYNC - stops everything: undoes S4, then S3, then S2.
 * - {@link #restart()}: ASYNC - completes a stop then a start.
 * - {@link #destroy()}: SYNC - performs a {@link #stop()} that will go on no matter the exceptions,
 * without throwing. Makes the engine unusable and clears resources.
 * <p>
 * For example, we expose the engine (S2) state through {@link #getEngineState()}. It will be:
 * - {@link #STATE_STARTING} if we're into step 2
 * - {@link #STATE_STARTED} if we've completed step 2. No clue about 3 or 4.
 * - {@link #STATE_STOPPING} if we're undoing steps 4, 3 and 2.
 * - {@link #STATE_STOPPED} if we have undone steps 4, 3 and 2 (or they never started at all).
 * <p>
 * <p>
 * THREADING
 * Subclasses should always execute isoCountryCode2Digit on the thread given by {@link #mHandler}.
 * For convenience, all the setup and tear down methods are called on this engine thread:
 * {@link #onStartEngine()}, {@link #onStartBind()}, {@link #onStartPreview()} to setup and
 * {@link #onStopEngine()}, {@link #onStopBind()}, {@link #onStopPreview()} to tear down.
 * However, these methods are not forced to be synchronous and then can simply return a Google's
 * {@link Task}.
 * <p>
 * Other setters are executed on the callers thread so subclasses should make sure they post
 * to the engine handler before acting on themselves.
 * <p>
 * <p>
 * ERROR HANDLING
 * THe {@link #mHandler} thread has a special {@link Thread.UncaughtExceptionHandler} that handles
 * exceptions and dispatches error to the callback (instead of crashing the app).
 * This lets subclasses run isoCountryCode2Digit safely and directly throw {@link CameraException}s when needed.
 * <p>
 * For convenience, the two main method {@link #onStartEngine()} and {@link #onStopEngine()}
 * are already called on the engine thread, but they can still be asynchronous by returning a
 * Google's {@link Task}.
 */
public abstract class CameraEngine implements
        CameraPreview.SurfaceCallback,
        PictureRecorder.PictureResultListener {

    public interface Callback {
        @NonNull
        Context getContext();

        void dispatchOnCameraOpened(CameraOptions options);

        void dispatchOnCameraClosed();

        void onCameraPreviewStreamSizeChanged();

        void onShutter(boolean shouldPlaySound);

        void dispatchOnPictureTaken(PictureResult.Stub stub);

        void dispatchOnFocusStart(@Nullable Gesture trigger, @NonNull PointF where);

        void dispatchOnFocusEnd(@Nullable Gesture trigger, boolean success, @NonNull PointF where);

        void dispatchOnZoomChanged(final float newValue, @Nullable final PointF[] fingers);

        void dispatchOnExposureCorrectionChanged(float newValue,
                                                 @NonNull float[] bounds,
                                                 @Nullable PointF[] fingers);

        void dispatchFrame(@NonNull Frame frame);

        void dispatchError(CameraException exception);
    }

    private static final String TAG = CameraEngine.class.getSimpleName();
    private static final CameraLogger LOG = CameraLogger.create(TAG);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public static final int STATE_STOPPING = Step.STATE_STOPPING;
    public static final int STATE_STOPPED = Step.STATE_STOPPED;
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static final int STATE_STARTING = Step.STATE_STARTING;
    public static final int STATE_STARTED = Step.STATE_STARTED;

    // Need to be protected
    @SuppressWarnings("WeakerAccess")
    protected WorkerHandler mHandler;
    @SuppressWarnings("WeakerAccess")
    protected final Callback mCallback;
    @SuppressWarnings("WeakerAccess")
    protected CameraPreview mPreview;
    @SuppressWarnings("WeakerAccess")
    protected CameraOptions mCameraOptions;
    @SuppressWarnings("WeakerAccess")
    protected PictureRecorder mPictureRecorder;
    @SuppressWarnings("WeakerAccess")
    protected Size mCaptureSize;
    @SuppressWarnings("WeakerAccess")
    protected Size mPreviewStreamSize;
    @SuppressWarnings("WeakerAccess")
    protected Flash mFlash;
    @SuppressWarnings("WeakerAccess")
    protected WhiteBalance mWhiteBalance;
    @SuppressWarnings("WeakerAccess")
    protected Hdr mHdr;
    @SuppressWarnings("WeakerAccess")
    protected Location mLocation;
    @SuppressWarnings("WeakerAccess")
    protected float mZoomValue;
    @SuppressWarnings("WeakerAccess")
    protected float mExposureCorrectionValue;
    @SuppressWarnings("WeakerAccess")
    protected boolean mPlaySounds;
    @SuppressWarnings("WeakerAccess")
    protected boolean mPictureMetering;
    @SuppressWarnings("WeakerAccess")
    protected boolean mPictureSnapshotMetering;

    // Can be private
    @VisibleForTesting
    Handler mCrashHandler;
    private final FrameManager mFrameManager;
    private final Angles mAngles;
    @Nullable
    private SizeSelector mPreviewStreamSizeSelector;
    private SizeSelector mPictureSizeSelector;
    private Facing mFacing;
    private Mode mMode;
    private boolean mHasFrameProcessors;
    private long mAutoFocusResetDelayMillis;
    // in REF_VIEW, for consistency with SizeSelectors
    private int mSnapshotMaxWidth = Integer.MAX_VALUE;
    // in REF_VIEW, for consistency with SizeSelectors
    private int mSnapshotMaxHeight = Integer.MAX_VALUE;
    private Overlay overlay;

    // Steps
    private final Step.Callback mStepCallback = new Step.Callback() {
        @Override
        @NonNull
        public Executor getExecutor() {
            return mHandler.getExecutor();
        }

        @Override
        public void handleException(@NonNull Exception exception) {
            CameraEngine.this.handleException(Thread.currentThread(), exception, false);
        }
    };
    @VisibleForTesting
    Step mEngineStep = new Step("engine", mStepCallback);
    private Step mBindStep = new Step("bind", mStepCallback);
    private Step mPreviewStep = new Step("preview", mStepCallback);
    private Step mAllStep = new Step("all", mStepCallback);

    // Ops used for testing.
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    Op<Void> mZoomOp = new Op<>();
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    Op<Void> mExposureCorrectionOp
            = new Op<>();
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    Op<Void> mFlashOp = new Op<>();
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    Op<Void> mWhiteBalanceOp
            = new Op<>();
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    Op<Void> mHdrOp = new Op<>();
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    Op<Void> mLocationOp = new Op<>();
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    Op<Void> mPlaySoundsOp = new Op<>();

    protected CameraEngine(@NonNull Callback callback) {
        mCallback = callback;
        mCrashHandler = new Handler(Looper.getMainLooper());
        mHandler = WorkerHandler.get("CameraViewEngine");
        mHandler.getThread().setUncaughtExceptionHandler(new CrashExceptionHandler());
        mFrameManager = instantiateFrameManager();
        mAngles = new Angles();
    }

    public void setPreview(@NonNull CameraPreview cameraPreview) {
        if (mPreview != null) mPreview.setSurfaceCallback(null);
        mPreview = cameraPreview;
        mPreview.setSurfaceCallback(this);
    }

    @NonNull
    public CameraPreview getPreview() {
        return mPreview;
    }

    //region Error handling

    /**
     * The base exception handler, which inspects the exception and
     * decides what to do.
     */
    private class CrashExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(final Thread thread, final Throwable throwable) {
            handleException(thread, throwable, true);
        }
    }

    /**
     * A static exception handler used during destruction to avoid leaks,
     * since the default handler is not static and the thread might survive the engine.
     */
    private static class NoOpExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            // No-op.
        }
    }

    /**
     * Handles exceptions coming from either runtime errors on the {@link #mHandler} isoCountryCode2Digit that is
     * not caught (using the {@link CrashExceptionHandler}), as might happen during standard
     * mHandler.post() operations that subclasses might do, OR for errors caught by tasks and
     * continuations that we launch here.
     * <p>
     * In the first case, the thread is about to be terminated. In the second case,
     * we can actually keep using it.
     *
     * @param thread               the thread
     * @param throwable            the throwable
     * @param fromExceptionHandler true if coming from exception handler
     */
    private void handleException(@NonNull Thread thread,
                                 final @NonNull Throwable throwable,
                                 final boolean fromExceptionHandler) {
        if (!(throwable instanceof CameraException)) {
            // This is unexpected, either a bug or something the developer should know.
            // Release and crash the UI thread so we get bug reports.
            mCrashHandler.post(new Runnable() {
                @Override
                public void run() {
                    destroy();
                    // Throws an unchecked exception without unnecessary wrapping.
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    } else {
                        throw new RuntimeException(throwable);
                    }
                }
            });
            return;
        }

        final CameraException cameraException = (CameraException) throwable;
        LOG.e("uncaughtException:", "Got CameraException:", cameraException,
                "on engine state:", getEngineState());
        if (fromExceptionHandler) {
            // Got to restart the handler.
            thread.interrupt();
            mHandler = WorkerHandler.get("CameraViewEngine");
            mHandler.getThread().setUncaughtExceptionHandler(new CrashExceptionHandler());
        }

        mCallback.dispatchError(cameraException);
        if (cameraException.isUnrecoverable()) {
            // Stop everything (if needed) without notifying teardown errors.
            stop(true);
        }
    }

    //endregion

    //region states and steps

    public final int getEngineState() {
        return mEngineStep.getState();
    }

    @SuppressWarnings("WeakerAccess")
    public final int getBindState() {
        return mBindStep.getState();
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public final int getPreviewState() {
        return mPreviewStep.getState();
    }

    private boolean canStartEngine() {
        return mEngineStep.isStoppingOrStopped();
    }

    private boolean needsStopEngine() {
        return mEngineStep.isStartedOrStarting();
    }

    private boolean canStartBind() {
        return mEngineStep.isStarted()
                && mPreview != null
                && mPreview.hasSurface()
                && mBindStep.isStoppingOrStopped();
    }

    private boolean needsStopBind() {
        return mBindStep.isStartedOrStarting();
    }

    private boolean canStartPreview() {
        return mEngineStep.isStarted()
                && mBindStep.isStarted()
                && mPreviewStep.isStoppingOrStopped();
    }

    private boolean needsStopPreview() {
        return mPreviewStep.isStartedOrStarting();
    }

    //endregion

    //region Start & Stop the engine

    @NonNull
    @EngineThread
    private Task<Void> startEngine() {
        if (canStartEngine()) {
            mEngineStep.doStart(false, new Callable<Task<Void>>() {
                @Override
                public Task<Void> call() {
                    if (!collectCameraInfo(mFacing)) {
                        LOG.e("onStartEngine:", "No camera available for facing", mFacing);
                        throw new CameraException(CameraException.REASON_NO_CAMERA);
                    }
                    return onStartEngine();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    mCallback.dispatchOnCameraOpened(mCameraOptions);
                }
            });
        }
        return mEngineStep.getTask();
    }

    @NonNull
    @EngineThread
    private Task<Void> stopEngine(boolean swallowExceptions) {
        if (needsStopEngine()) {
            mEngineStep.doStop(swallowExceptions, new Callable<Task<Void>>() {
                @Override
                public Task<Void> call() {
                    return onStopEngine();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    mCallback.dispatchOnCameraClosed();
                }
            });
        }
        return mEngineStep.getTask();
    }

    /**
     * Starts the engine.
     *
     * @return a task
     */
    @NonNull
    @EngineThread
    protected abstract Task<Void> onStartEngine();

    /**
     * Stops the engine.
     * Stop events should generally not throw exceptions. We
     * want to release resources either way.
     *
     * @return a task
     */
    @NonNull
    @EngineThread
    protected abstract Task<Void> onStopEngine();

    //endregion

    //region Start & Stop binding

    @NonNull
    @EngineThread
    private Task<Void> startBind() {
        if (canStartBind()) {
            mBindStep.doStart(false, new Callable<Task<Void>>() {
                @Override
                public Task<Void> call() {
                    return onStartBind();
                }
            });
        }
        return mBindStep.getTask();
    }

    @NonNull
    @EngineThread
    private Task<Void> stopBind(boolean swallowExceptions) {
        if (needsStopBind()) {
            mBindStep.doStop(swallowExceptions, new Callable<Task<Void>>() {
                @Override
                public Task<Void> call() {
                    return onStopBind();
                }
            });
        }
        return mBindStep.getTask();
    }

    /**
     * Starts the binding process.
     *
     * @return a task
     */
    @NonNull
    @EngineThread
    protected abstract Task<Void> onStartBind();

    /**
     * Stops the binding process.
     * Stop events should generally not throw exceptions. We
     * want to release resources either way.
     *
     * @return a task
     */
    @NonNull
    @EngineThread
    protected abstract Task<Void> onStopBind();

    @SuppressWarnings("WeakerAccess")
    protected void restartBind() {
        LOG.i("restartBind", "posting.");
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                LOG.w("restartBind", "executing stopPreview.");
                stopPreview(false).continueWithTask(mHandler.getExecutor(),
                        new Continuation<Void, Task<Void>>() {
                            @Override
                            public Task<Void> then(@NonNull Task<Void> task) {
                                LOG.w("restartBind", "executing stopBind.");
                                return stopBind(false);
                            }
                        }).onSuccessTask(mHandler.getExecutor(), new SuccessContinuation<Void, Void>() {
                    @NonNull
                    @Override
                    public Task<Void> then(@Nullable Void aVoid) {
                        LOG.w("restartBind", "executing startBind.");
                        return startBind();
                    }
                }).onSuccessTask(mHandler.getExecutor(), new SuccessContinuation<Void, Void>() {
                    @NonNull
                    @Override
                    public Task<Void> then(@Nullable Void aVoid) {
                        LOG.w("restartBind", "executing startPreview.");
                        return startPreview();
                    }
                });
            }
        });
    }

    //endregion

    //region Start & Stop preview

    @NonNull
    @EngineThread
    private Task<Void> startPreview() {
        LOG.i("startPreview", "canStartPreview:", canStartPreview());
        if (canStartPreview()) {
            mPreviewStep.doStart(false, new Callable<Task<Void>>() {
                @Override
                public Task<Void> call() {
                    return onStartPreview();
                }
            });
        }
        return mPreviewStep.getTask();
    }

    @NonNull
    @EngineThread
    private Task<Void> stopPreview(boolean swallowExceptions) {
        LOG.i("stopPreview",
                "needsStopPreview:", needsStopPreview(),
                "swallowExceptions:", swallowExceptions);
        if (needsStopPreview()) {
            mPreviewStep.doStop(swallowExceptions, new Callable<Task<Void>>() {
                @Override
                public Task<Void> call() {
                    return onStopPreview();
                }
            });
        }
        return mPreviewStep.getTask();
    }

    @SuppressWarnings("WeakerAccess")
    protected void restartPreview() {
        LOG.i("restartPreview", "posting.");
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                LOG.i("restartPreview", "executing.");
                stopPreview(false);
                startPreview();
            }
        });
    }

    /**
     * Starts the preview streaming.
     *
     * @return a task
     */
    @NonNull
    @EngineThread
    protected abstract Task<Void> onStartPreview();

    /**
     * Stops the preview streaming.
     * Stop events should generally not throw exceptions. We
     * want to release resources either way.
     *
     * @return a task
     */
    @NonNull
    @EngineThread
    protected abstract Task<Void> onStopPreview();

    //endregion

    //region Surface callbacks

    /**
     * The surface is now available, which means that step 1 has completed.
     * If we have also completed step 2, go on with binding and streaming.
     */
    @Override
    public final void onSurfaceAvailable() {
        LOG.i("onSurfaceAvailable:", "Size is", getPreviewSurfaceSize(Reference.VIEW));
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                startBind().onSuccessTask(mHandler.getExecutor(), new SuccessContinuation<Void, Void>() {
                    @NonNull
                    @Override
                    public Task<Void> then(@Nullable Void aVoid) {
                        return startPreview();
                    }
                });
            }
        });
    }

    @Override
    public final void onSurfaceChanged() {
        LOG.i("onSurfaceChanged:", "Size is", getPreviewSurfaceSize(Reference.VIEW),
                "Posting.");
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                LOG.i("onSurfaceChanged:",
                        "Engine started?", mEngineStep.isStarted(),
                        "Bind started?", mBindStep.isStarted());
                if (!mEngineStep.isStarted()) return; // Too early
                if (!mBindStep.isStarted()) return; // Too early

                // Compute a new camera preview size and apply.
                Size newSize = computePreviewStreamSize();
                if (newSize.equals(mPreviewStreamSize)) {
                    LOG.i("onSurfaceChanged:",
                            "The computed preview size is identical. No op.");
                } else {
                    LOG.i("onSurfaceChanged:",
                            "Computed a new preview size. Calling onPreviewStreamSizeChanged().");
                    mPreviewStreamSize = newSize;
                    onPreviewStreamSizeChanged();
                }
            }
        });
    }

    /**
     * The preview stream size has changed. At this point, some engine might want to
     * simply call {@link #restartPreview()}, others to {@link #restartBind()}.
     * <p>
     * It basically depends on the step at which the preview stream size is actually used.
     */
    @EngineThread
    protected abstract void onPreviewStreamSizeChanged();

    @Override
    public final void onSurfaceDestroyed() {
        LOG.i("onSurfaceDestroyed");
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                stopPreview(false).onSuccessTask(mHandler.getExecutor(),
                        new SuccessContinuation<Void, Void>() {
                            @NonNull
                            @Override
                            public Task<Void> then(@Nullable Void aVoid) {
                                return stopBind(false);
                            }
                        });
            }
        });
    }

    //endregion

    //region Start & Stop all

    /**
     * Not final due to mockito requirements, but this is basically
     * it, nothing more to do.
     * <p>
     * NOTE: Should not be called on the {@link #mHandler} thread! I think
     * that would cause deadlocks due to us awaiting for {@link #stop()} to return.
     */
    public void destroy() {
        LOG.i("destroy:", "state:", getEngineState(), "thread:", Thread.currentThread());
        // Prevent CameraEngine leaks. Don't set to null, or exceptions
        // inside the standard stop() method might crash the main thread.
        mHandler.getThread().setUncaughtExceptionHandler(new NoOpExceptionHandler());
        // Stop if needed, synchronously and silently.
        // Cannot use Tasks.await() because we might be on the UI thread.
        final CountDownLatch latch = new CountDownLatch(1);
        stop(true).addOnCompleteListener(mHandler.getExecutor(),
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        latch.countDown();
                    }
                });
        try {
            boolean success = latch.await(3, TimeUnit.SECONDS);
            if (!success) {
                // TODO seems like this is always the case?
                LOG.e("Probably some deadlock in destroy.",
                        "Current thread:", Thread.currentThread(),
                        "Handler thread: ", mHandler.getThread());
            }
        } catch (InterruptedException ignore) {
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected final void restart() {
        LOG.i("Restart:", "calling stop and start");
        stop();
        start();
    }

    @NonNull
    public Task<Void> start() {
        LOG.i("Start:", "posting runnable. State:", getEngineState());
        final TaskCompletionSource<Void> outTask = new TaskCompletionSource<>();
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                LOG.w("Start:", "executing runnable. AllState is", mAllStep.getState());
                // It's better to schedule anyway. allStep might be STARTING and we might be
                // tempted to early return here, but the truth is that there might be a stop
                // already scheduled when the STARTING op ends.
                // if (mAllStep.isStoppingOrStopped()) {
                //     LOG.i("Start:", "executing runnable. AllState is STOPPING or STOPPED,
                //     so we schedule a start.");
                mAllStep.doStart(false, new Callable<Task<Void>>() {
                    @Override
                    public Task<Void> call() {
                        return startEngine().addOnFailureListener(mHandler.getExecutor(),
                                new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        outTask.trySetException(e);
                                    }
                                }).onSuccessTask(mHandler.getExecutor(), new SuccessContinuation<Void, Void>() {
                            @NonNull
                            @Override
                            public Task<Void> then(@Nullable Void aVoid) {
                                outTask.trySetResult(null);
                                return startBind();
                            }
                        }).onSuccessTask(mHandler.getExecutor(), new SuccessContinuation<Void, Void>() {
                            @NonNull
                            @Override
                            public Task<Void> then(@Nullable Void aVoid) {
                                return startPreview();
                            }
                        });
                    }
                });
                // } else {
                //     // NOTE: this returns early if we were STARTING.
                //     LOG.i("Start:",
                //     "executing runnable. AllState is STARTING or STARTED, so we return early.");
                //     outTask.trySetResult(null);
                // }
            }
        });
        return outTask.getTask();
    }

    @NonNull
    public Task<Void> stop() {
        return stop(false);
    }

    @NonNull
    private Task<Void> stop(final boolean swallowExceptions) {
        LOG.i("Stop:", "posting runnable. State:", getEngineState());
        final TaskCompletionSource<Void> outTask = new TaskCompletionSource<>();
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                LOG.w("Stop:", "executing runnable. AllState is", mAllStep.getState());
                // It's better to schedule anyway. allStep might be STOPPING and we might be
                // tempted to early return here, but the truth is that there might be a start
                // already scheduled when the STOPPING op ends.
                // if (mAllStep.isStartedOrStarting()) {
                //     LOG.i("Stop:", "executing runnable. AllState is STARTING or STARTED,
                //     so we schedule a stop.");
                mAllStep.doStop(swallowExceptions, new Callable<Task<Void>>() {
                    @Override
                    public Task<Void> call() {
                        return stopPreview(swallowExceptions).continueWithTask(
                                mHandler.getExecutor(), new Continuation<Void, Task<Void>>() {
                                    @Override
                                    public Task<Void> then(@NonNull Task<Void> task) {
                                        return stopBind(swallowExceptions);
                                    }
                                }).continueWithTask(mHandler.getExecutor(), new Continuation<Void, Task<Void>>() {
                            @Override
                            public Task<Void> then(@NonNull Task<Void> task) {
                                return stopEngine(swallowExceptions);
                            }
                        }).continueWithTask(mHandler.getExecutor(), new Continuation<Void, Task<Void>>() {
                            @Override
                            public Task<Void> then(@NonNull Task<Void> task) {
                                if (task.isSuccessful()) {
                                    outTask.trySetResult(null);
                                } else {
                                    //noinspection ConstantConditions
                                    outTask.trySetException(task.getException());
                                }
                                return task;
                            }
                        });
                    }
                });
                // } else {
                //     // NOTE: this returns early if we were STOPPING.
                //     LOG.i("Stop:", "executing runnable.
                //     AllState is STOPPING or STOPPED, so we return early.");
                //     outTask.trySetResult(null);
                // }
            }
        });
        return outTask.getTask();
    }

    //endregion

    //region Final setters and getters

    public final void setOverlay(@Nullable Overlay overlay) {
        this.overlay = overlay;
    }

    @Nullable
    public final Overlay getOverlay() {
        return overlay;
    }

    public final Angles getAngles() {
        return mAngles;
    }

    public final void setPreviewStreamSizeSelector(@Nullable SizeSelector selector) {
        mPreviewStreamSizeSelector = selector;
    }

    @Nullable
    public final SizeSelector getPreviewStreamSizeSelector() {
        return mPreviewStreamSizeSelector;
    }

    public final void setPictureSizeSelector(@NonNull SizeSelector selector) {
        mPictureSizeSelector = selector;
    }

    @NonNull
    public final SizeSelector getPictureSizeSelector() {
        return mPictureSizeSelector;
    }


    public final void setSnapshotMaxWidth(int maxWidth) {
        mSnapshotMaxWidth = maxWidth;
    }

    public int getSnapshotMaxWidth() {
        return mSnapshotMaxWidth;
    }

    public final void setSnapshotMaxHeight(int maxHeight) {
        mSnapshotMaxHeight = maxHeight;
    }

    public int getSnapshotMaxHeight() {
        return mSnapshotMaxHeight;
    }

    public final void setAutoFocusResetDelay(long delayMillis) {
        mAutoFocusResetDelayMillis = delayMillis;
    }

    public final long getAutoFocusResetDelay() {
        return mAutoFocusResetDelayMillis;
    }

    /**
     * Sets a new facing value. This will restart the session (if there's any)
     * so that we can open the new facing camera.
     *
     * @param facing facing
     */
    public final void setFacing(final @NonNull Facing facing) {
        final Facing old = mFacing;
        if (facing != old) {
            mFacing = facing;
            mHandler.run(new Runnable() {
                @Override
                public void run() {
                    if (getEngineState() < STATE_STARTED) return;
                    if (collectCameraInfo(facing)) {
                        restart();
                    } else {
                        mFacing = old;
                    }
                }
            });
        }
    }

    @NonNull
    public final Facing getFacing() {
        return mFacing;
    }

    /**
     * Sets the desired mode (either picture).
     *
     * @param mode desired mode.
     */
    public final void setMode(@NonNull Mode mode) {
        if (mode != mMode) {
            mMode = mode;
            mHandler.run(new Runnable() {
                @Override
                public void run() {
                    if (getEngineState() == STATE_STARTED) {
                        restart();
                    }
                }
            });
        }
    }

    @NonNull
    public final Mode getMode() {
        return mMode;
    }

    @NonNull
    public final FrameManager getFrameManager() {
        return mFrameManager;
    }

    @Nullable
    public final CameraOptions getCameraOptions() {
        return mCameraOptions;
    }

    @NonNull
    public final Flash getFlash() {
        return mFlash;
    }

    @NonNull
    public final WhiteBalance getWhiteBalance() {
        return mWhiteBalance;
    }

    @NonNull
    public final Hdr getHdr() {
        return mHdr;
    }

    @Nullable
    public final Location getLocation() {
        return mLocation;
    }

    public final float getZoomValue() {
        return mZoomValue;
    }

    public final float getExposureCorrectionValue() {
        return mExposureCorrectionValue;
    }

    @CallSuper
    public void setHasFrameProcessors(boolean hasFrameProcessors) {
        mHasFrameProcessors = hasFrameProcessors;
    }

    @SuppressWarnings("WeakerAccess")
    public final boolean hasFrameProcessors() {
        return mHasFrameProcessors;
    }

    @SuppressWarnings("WeakerAccess")
    protected final boolean shouldResetAutoFocus() {
        return mAutoFocusResetDelayMillis > 0 && mAutoFocusResetDelayMillis != Long.MAX_VALUE;
    }

    public final void setPictureMetering(boolean enable) {
        mPictureMetering = enable;
    }

    public final boolean getPictureMetering() {
        return mPictureMetering;
    }

    public final void setPictureSnapshotMetering(boolean enable) {
        mPictureSnapshotMetering = enable;
    }

    public final boolean getPictureSnapshotMetering() {
        return mPictureSnapshotMetering;
    }

    //endregion

    //region Abstract setters and APIs

    /**
     * Camera is about to be opened. Implementors should look into available cameras
     * and see if anyone matches the given {@link Facing value}.
     * <p>
     * If so, implementors should set {@link Angles#setSensorOffset(Facing, int)}
     * and any other information (like camera ID) needed to start the engine.
     *
     * @param facing the facing value
     * @return true if we have one
     */
    @EngineThread
    protected abstract boolean collectCameraInfo(@NonNull Facing facing);

    /**
     * Called at construction time to get a frame manager that can later be
     * accessed through {@link #getFrameManager()}.
     *
     * @return a frame manager
     */
    @NonNull
    protected abstract FrameManager instantiateFrameManager();

    // If closed, no-op. If opened, check supported and apply.
    public abstract void setZoom(float zoom, @Nullable PointF[] points, boolean notify);

    // If closed, no-op. If opened, check supported and apply.
    public abstract void setExposureCorrection(float EVvalue,
                                               @NonNull float[] bounds,
                                               @Nullable PointF[] points,
                                               boolean notify);

    // If closed, keep. If opened, check supported and apply.
    public abstract void setFlash(@NonNull Flash flash);

    // If closed, keep. If opened, check supported and apply.
    public abstract void setWhiteBalance(@NonNull WhiteBalance whiteBalance);

    // If closed, keep. If opened, check supported and apply.
    public abstract void setHdr(@NonNull Hdr hdr);

    // If closed, keep. If opened, check supported and apply.
    public abstract void setLocation(@Nullable Location location);

    public abstract void startAutoFocus(@Nullable Gesture gesture, @NonNull PointF point);

    public abstract void setPlaySounds(boolean playSounds);


    public final boolean isTakingPicture() {
        return mPictureRecorder != null;
    }

    /* not final for tests */
    public void takePicture(final @NonNull PictureResult.Stub stub) {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (getBindState() < STATE_STARTED) return;
                if (isTakingPicture()) return;
                stub.isSnapshot = false;
                stub.location = mLocation;
                stub.facing = mFacing;
                onTakePicture(stub, mPictureMetering);
            }
        };
        new Thread(runnable).start();

        LOG.v("takePicture", "scheduling");
        /*mHandler.run(new Runnable() {
            @Override
            public void run() {
                if (getBindState() < STATE_STARTED) return;
                if (isTakingPicture()) return;
                stub.isSnapshot = false;
                stub.location = mLocation;
                stub.facing = mFacing;
                onTakePicture(stub, mPictureMetering);
            }
        });*/
    }

    /**
     * The snapshot size is the {@link #getPreviewStreamSize(Reference)}, but cropped based on the
     * view/surface aspect ratio.
     *
     * @param stub a picture stub
     */
    public final void takePictureSnapshot(final @NonNull PictureResult.Stub stub) {
        LOG.v("takePictureSnapshot", "scheduling");
        mHandler.run(new Runnable() {
            @Override
            public void run() {
                LOG.v("takePictureSnapshot", "performing. BindState:",
                        getBindState(), "isTakingPicture:", isTakingPicture());
                if (getBindState() < STATE_STARTED) return;
                if (isTakingPicture()) return;
                stub.location = mLocation;
                stub.isSnapshot = true;
                stub.facing = mFacing;
                // Leave the other parameters to subclasses.
                //noinspection ConstantConditions
                AspectRatio ratio = AspectRatio.of(getPreviewSurfaceSize(Reference.OUTPUT));
                onTakePictureSnapshot(stub, ratio, mPictureSnapshotMetering);
            }
        });
    }

    @Override
    public void onPictureShutter(boolean didPlaySound) {
        mCallback.onShutter(!didPlaySound);
    }

    @Override
    public void onPictureResult(@Nullable PictureResult.Stub result, @Nullable Exception error) {
        mPictureRecorder = null;
        if (result != null) {
            mCallback.dispatchOnPictureTaken(result);
        } else {
            mCallback.dispatchError(new CameraException(error,
                    CameraException.REASON_PICTURE_FAILED));
        }
    }


    @EngineThread
    protected abstract void onTakePicture(@NonNull PictureResult.Stub stub, boolean doMetering);

    @EngineThread
    protected abstract void onTakePictureSnapshot(@NonNull PictureResult.Stub stub,
                                                  @NonNull AspectRatio outputRatio,
                                                  boolean doMetering);

    //endregion

    //region Size utilities

    @Nullable
    public final Size getPictureSize(@SuppressWarnings("SameParameterValue") @NonNull Reference reference) {
        Size size = mCaptureSize;
        if (size == null) return null;
        return getAngles().flip(Reference.SENSOR, reference) ? size.flip() : size;
    }

    @Nullable
    public final Size getPreviewStreamSize(@NonNull Reference reference) {
        Size size = mPreviewStreamSize;
        if (size == null) return null;
        return getAngles().flip(Reference.SENSOR, reference) ? size.flip() : size;
    }

    @SuppressWarnings("SameParameterValue")
    @Nullable
    private Size getPreviewSurfaceSize(@NonNull Reference reference) {
        CameraPreview preview = mPreview;
        if (preview == null) return null;
        return getAngles().flip(Reference.VIEW, reference) ? preview.getSurfaceSize().flip()
                : preview.getSurfaceSize();
    }

    /**
     * Returns the snapshot size, but not cropped with the view dimensions, which
     * is what we will do before creating the snapshot. However, cropping is done at various
     * levels so we don't want to perform the op here.
     * <p>
     * The base snapshot size is based on PreviewStreamSize (later cropped with view ratio). Why?
     * One might be tempted to say that it's the SurfaceSize (which already matches the view ratio).
     * <p>
     * The camera sensor will capture preview frames with PreviewStreamSize and that's it. Then they
     * are hardware-scaled by the preview surface, but this does not affect the snapshot, as the
     * snapshot recorder simply creates another surface.
     * <p>
     * Done tests to ensure that this is true, by using
     * 1. small SurfaceSize and biggest() PreviewStreamSize: output is not low quality
     * 2. big SurfaceSize and smallest() PreviewStreamSize: output is low quality
     * In both cases the result.size here was set to the biggest of the two.
     *
     * @param reference the reference system
     * @return the uncropped snapshot size
     */
    @Nullable
    public final Size getUncroppedSnapshotSize(@NonNull Reference reference) {
        Size baseSize = getPreviewStreamSize(reference);
        if (baseSize == null) return null;
        boolean flip = getAngles().flip(reference, Reference.VIEW);
        int maxWidth = flip ? mSnapshotMaxHeight : mSnapshotMaxWidth;
        int maxHeight = flip ? mSnapshotMaxWidth : mSnapshotMaxHeight;
        float baseRatio = AspectRatio.of(baseSize).toFloat();
        float maxValuesRatio = AspectRatio.of(maxWidth, maxHeight).toFloat();
        if (maxValuesRatio >= baseRatio) {
            // Height is the real constraint.
            int outHeight = Math.min(baseSize.getHeight(), maxHeight);
            int outWidth = (int) Math.floor((float) outHeight * baseRatio);
            return new Size(outWidth, outHeight);
        } else {
            // Width is the real constraint.
            int outWidth = Math.min(baseSize.getWidth(), maxWidth);
            int outHeight = (int) Math.floor((float) outWidth / baseRatio);
            return new Size(outWidth, outHeight);
        }
    }

    /**
     * This is called either on cameraView.start(), or when the underlying surface changes.
     * It is possible that in the first call the preview surface has not already computed its
     * dimensions.
     * But when it does, the {@link CameraPreview.SurfaceCallback} should be called,
     * and this should be refreshed.
     *
     * @return the capture size
     */
    @NonNull
    @SuppressWarnings("WeakerAccess")
    protected final Size computeCaptureSize() {
        return computeCaptureSize(mMode);
    }

    @SuppressWarnings("WeakerAccess")
    protected final Size computeCaptureSize(@NonNull Mode mode) {
        // We want to pass stuff into the REF_VIEW reference, not the sensor one.
        // This is already managed by CameraOptions, so we just flip again at the end.
        boolean flip = getAngles().flip(Reference.SENSOR, Reference.VIEW);
        SizeSelector selector;
        Collection<Size> sizes;
        selector = mPictureSizeSelector;
        sizes = mCameraOptions.getSupportedPictureSizes();

        selector = SizeSelectors.or(selector, SizeSelectors.biggest());
        List<Size> list = new ArrayList<>(sizes);
        Size result = selector.select(list).get(0);
        if (!list.contains(result)) {
            throw new RuntimeException("SizeSelectors must not return Sizes other than " +
                    "those in the input list.");
        }
        LOG.i("computeCaptureSize:", "result:", result, "flip:", flip, "mode:", mode);
        if (flip) result = result.flip(); // Go back to REF_SENSOR
        return result;
    }

    /**
     * This is called anytime {@link #computePreviewStreamSize()} is called.
     * This means that it should be called during the binding process, when
     * we can be sure that the camera is available (engineState == STARTED).
     *
     * @return a list of available sizes for preview
     */
    @EngineThread
    @NonNull
    protected abstract List<Size> getPreviewStreamAvailableSizes();

    @EngineThread
    @NonNull
    @SuppressWarnings("WeakerAccess")
    protected final Size computePreviewStreamSize() {
        @NonNull List<Size> previewSizes = getPreviewStreamAvailableSizes();
        // These sizes come in REF_SENSOR. Since there is an external selector involved,
        // we must convert all of them to REF_VIEW, then flip back when returning.
        boolean flip = getAngles().flip(Reference.SENSOR, Reference.VIEW);
        List<Size> sizes = new ArrayList<>(previewSizes.size());
        for (Size size : previewSizes) {
            sizes.add(flip ? size.flip() : size);
        }

        // Create our own default selector, which will be used if the external
        // mPreviewStreamSizeSelector is null, or if it fails in finding a size.
        Size targetMinSize = getPreviewSurfaceSize(Reference.VIEW);
        if (targetMinSize == null) {
            throw new IllegalStateException("targetMinSize should not be null here.");
        }
        AspectRatio targetRatio = AspectRatio.of(mCaptureSize.getWidth(), mCaptureSize.getHeight());
        if (flip) targetRatio = targetRatio.flip();
        LOG.i("computePreviewStreamSize:",
                "targetRatio:", targetRatio,
                "targetMinSize:", targetMinSize);
        SizeSelector matchRatio = SizeSelectors.and( // Match this aspect ratio and sort by biggest
                SizeSelectors.aspectRatio(targetRatio, 0),
                SizeSelectors.biggest());
        SizeSelector matchSize = SizeSelectors.and( // Bigger than this size, and sort by smallest
                SizeSelectors.minHeight(targetMinSize.getHeight()),
                SizeSelectors.minWidth(targetMinSize.getWidth()),
                SizeSelectors.smallest());
        SizeSelector matchAll = SizeSelectors.or(
                SizeSelectors.and(matchRatio, matchSize), // Try to respect both constraints.
                matchSize, // If couldn't match aspect ratio, at least respect the size
                matchRatio, // If couldn't respect size, at least match aspect ratio
                SizeSelectors.biggest() // If couldn't match any, take the biggest.
        );

        // Apply the external selector with this as a fallback,
        // and return a size in REF_SENSOR reference.
        SizeSelector selector;
        if (mPreviewStreamSizeSelector != null) {
            selector = SizeSelectors.or(mPreviewStreamSizeSelector, matchAll);
        } else {
            selector = matchAll;
        }
        Size result = selector.select(sizes).get(0);
        if (!sizes.contains(result)) {
            throw new RuntimeException("SizeSelectors must not return Sizes other than " +
                    "those in the input list.");
        }
        if (flip) result = result.flip();
        LOG.i("computePreviewStreamSize:", "result:", result, "flip:", flip);
        return result;
    }

    //endregion
}
