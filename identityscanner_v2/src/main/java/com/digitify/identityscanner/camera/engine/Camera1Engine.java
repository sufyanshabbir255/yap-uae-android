//package com.digitify.identityscanner.camera.engine;
//
//import android.annotation.TargetApi;
//import android.graphics.ImageFormat;
//import android.graphics.PointF;
//import android.graphics.Rect;
//import android.graphics.SurfaceTexture;
//import android.hardware.Camera;
//import android.location.Location;
//import android.os.Build;
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.annotation.VisibleForTesting;
//
//import android.view.SurfaceHolder;
//
//import com.google.android.gms.tasks.Task;
//import com.google.android.gms.tasks.Tasks;
//import com.digitify.identityscanner.camera.CameraException;
//import com.digitify.identityscanner.camera.CameraLogger;
//import com.digitify.identityscanner.camera.CameraOptions;
//import com.digitify.identityscanner.camera.engine.mappers.Camera1Mapper;
//import com.digitify.identityscanner.camera.engine.offset.Axis;
//import com.digitify.identityscanner.camera.engine.offset.Reference;
//import com.digitify.identityscanner.camera.frame.Frame;
//import com.digitify.identityscanner.camera.PictureResult;
//import com.digitify.identityscanner.camera.controls.Facing;
//import com.digitify.identityscanner.camera.controls.Flash;
//import com.digitify.identityscanner.camera.frame.FrameManager;
//import com.digitify.identityscanner.camera.gesture.Gesture;
//import com.digitify.identityscanner.camera.controls.Hdr;
//import com.digitify.identityscanner.camera.controls.WhiteBalance;
//import com.digitify.identityscanner.camera.internal.utils.CropHelper;
//import com.digitify.identityscanner.camera.picture.Full1PictureRecorder;
//import com.digitify.identityscanner.camera.picture.Snapshot1PictureRecorder;
//import com.digitify.identityscanner.camera.picture.SnapshotGlPictureRecorder;
//import com.digitify.identityscanner.camera.preview.GlCameraPreview;
//import com.digitify.identityscanner.camera.size.AspectRatio;
//import com.digitify.identityscanner.camera.size.Size;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//
//
//@SuppressWarnings("deprecation")
//public class Camera1Engine extends CameraEngine implements
//        Camera.PreviewCallback,
//        Camera.ErrorCallback,
//        FrameManager.BufferCallback {
//
//
//    private static final String TAG = Camera1Engine.class.getSimpleName();
//    private static final CameraLogger LOG = CameraLogger.create(TAG);
//
//    private static final int PREVIEW_FORMAT = ImageFormat.NV21;
//    @VisibleForTesting static final int AUTOFOCUS_END_DELAY_MILLIS = 2500;
//
//    private final Camera1Mapper mMapper = Camera1Mapper.Companion.get();
//    private Camera mCamera;
//    @VisibleForTesting int mCameraId;
//    private Runnable mFocusEndRunnable;
//
//    public Camera1Engine(@NonNull Callback callback) {
//        super(callback);
//    }
//
//    //region Utilities
//
//    @Override
//    public void onError(int error, Camera camera) {
//        if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
//            // Looks like this is recoverable.
//            LOG.w("Recoverable error inside the onError callback.",
//                    "CAMERA_ERROR_SERVER_DIED");
//            restart();
//            return;
//        }
//
//        String message = LOG.e("Internal Camera1 error.", error);
//        Exception runtime = new RuntimeException(message);
//        int reason;
//        switch (error) {
//            case Camera.CAMERA_ERROR_EVICTED: reason = CameraException.REASON_DISCONNECTED; break;
//            case Camera.CAMERA_ERROR_UNKNOWN: reason = CameraException.REASON_UNKNOWN; break;
//            default: reason = CameraException.REASON_UNKNOWN;
//        }
//        throw new CameraException(runtime, reason);
//    }
//
//    //endregion
//
//    //region Protected APIs
//
//    @EngineThread
//    @NonNull
//    @Override
//    protected List<Size> getPreviewStreamAvailableSizes() {
//        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
//        List<Size> result = new ArrayList<>(sizes.size());
//        for (Camera.Size size : sizes) {
//            Size add = new Size(size.width, size.height);
//            if (!result.contains(add)) result.add(add);
//        }
//        LOG.i("getPreviewStreamAvailableSizes:", result);
//        return result;
//    }
//    @EngineThread
//    @NonNull
//    @Override
//    protected Task<Void> onStopPreview() {
//        mPictureRecorder = null;
//        getFrameManager().release();
//        mCamera.setPreviewCallbackWithBuffer(null); // Release anything left
//        try {
//            mCamera.stopPreview();
//        } catch (Exception e) {
//        }
//        return Tasks.forResult(null);
//    }
//
//    @EngineThread
//    @Override
//    protected void onPreviewStreamSizeChanged() {
//        restartPreview();
//    }
//
//    @EngineThread
//    @Override
//    protected boolean collectCameraInfo(@NonNull Facing facing) {
//        int internalFacing = mMapper.mapFacing(facing);
//        LOG.i("collectCameraInfo",
//                "Facing:", facing,
//                "Internal:", internalFacing,
//                "Cameras:", Camera.getNumberOfCameras());
//        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
//            Camera.getCameraInfo(i, cameraInfo);
//            if (cameraInfo.facing == internalFacing) {
//                getAngles().setSensorOffset(facing, cameraInfo.orientation);
//                mCameraId = i;
//                return true;
//            }
//        }
//        return false;
//    }
//
//    //endregion
//
//    //region Start
//
//    @NonNull
//    @EngineThread
//    @Override
//    protected Task<Void> onStartEngine() {
//        try {
//            mCamera = Camera.open(mCameraId);
//        } catch (Exception e) {
//            LOG.e("onStartEngine:", "Failed to connect. Maybe in use by another app?");
//            throw new CameraException(e, CameraException.REASON_FAILED_TO_CONNECT);
//        }
//        mCamera.setErrorCallback(this);
//
//        // Set parameters that might have been set before the camera was opened.
//        LOG.i("onStartEngine:", "Applying default parameters.");
//        Camera.Parameters params = mCamera.getParameters();
//        mCameraOptions = new CameraOptions(params, mCameraId,
//                getAngles().flip(Reference.SENSOR, Reference.VIEW));
//        applyAllParameters(params);
//        mCamera.setParameters(params);
//        mCamera.setDisplayOrientation(getAngles().offset(Reference.SENSOR, Reference.VIEW,
//                Axis.ABSOLUTE)); // <- not allowed during preview
//        LOG.i("onStartEngine:", "Ended");
//        return Tasks.forResult(null);
//    }
//
//    @EngineThread
//    @NonNull
//    @Override
//    protected Task<Void> onStartBind() {
//        LOG.i("onStartBind:", "Started");
//        Object output = mPreview.getOutput();
//        try {
//            if (output instanceof SurfaceHolder) {
//                mCamera.setPreviewDisplay((SurfaceHolder) output);
//            } else if (output instanceof SurfaceTexture) {
//                mCamera.setPreviewTexture((SurfaceTexture) output);
//            } else {
//                throw new RuntimeException("Unknown CameraPreview output class.");
//            }
//        } catch (IOException e) {
//            throw new CameraException(e, CameraException.REASON_FAILED_TO_START_PREVIEW);
//        }
//
//        mCaptureSize = computeCaptureSize();
//        mPreviewStreamSize = computePreviewStreamSize();
//        return Tasks.forResult(null);
//    }
//
//    @EngineThread
//    @NonNull
//    @Override
//    protected Task<Void> onStartPreview() {
//        LOG.i("onStartPreview", "Dispatching onCameraPreviewStreamSizeChanged.");
//        mCallback.onCameraPreviewStreamSizeChanged();
//
//        Size previewSize = getPreviewStreamSize(Reference.VIEW);
//        if (previewSize == null) {
//            throw new IllegalStateException("previewStreamSize should not be null at this point.");
//        }
//        mPreview.setStreamSize(previewSize.getWidth(), previewSize.getHeight());
//
//        Camera.Parameters params = mCamera.getParameters();
//        // NV21 should be the default, but let's make sure, since YuvImage will only support this
//        // and a few others
//        params.setPreviewFormat(ImageFormat.NV21);
//        // setPreviewSize is not allowed during preview
//        params.setPreviewSize(mPreviewStreamSize.getWidth(), mPreviewStreamSize.getHeight());
//            // setPictureSize is allowed during preview
//            params.setPictureSize(mCaptureSize.getWidth(), mCaptureSize.getHeight());
//        mCamera.setParameters(params);
//
//        mCamera.setPreviewCallbackWithBuffer(null); // Release anything left
//        mCamera.setPreviewCallbackWithBuffer(this); // Add ourselves
//        getFrameManager().setUp(PREVIEW_FORMAT, mPreviewStreamSize);
//
//        LOG.i("onStartPreview", "Starting preview with startPreview().");
//        try {
//            mCamera.startPreview();
//        } catch (Exception e) {
//            throw new CameraException(e, CameraException.REASON_FAILED_TO_START_PREVIEW);
//        }
//        LOG.i("onStartPreview", "Started preview.");
//        return Tasks.forResult(null);
//    }
//
//    //endregion
//
//
//
//    @EngineThread
//    @NonNull
//    @Override
//    protected Task<Void> onStopBind() {
//        mPreviewStreamSize = null;
//        mCaptureSize = null;
//        try {
//            if (mPreview.getOutputClass() == SurfaceHolder.class) {
//                mCamera.setPreviewDisplay(null);
//            } else if (mPreview.getOutputClass() == SurfaceTexture.class) {
//                mCamera.setPreviewTexture(null);
//            } else {
//                throw new RuntimeException("Unknown CameraPreview output class.");
//            }
//        } catch (IOException e) {
//        }
//        return Tasks.forResult(null);
//    }
//
//    @EngineThread
//    @NonNull
//    @Override
//    protected Task<Void> onStopEngine() {
//        LOG.i("onStopEngine:", "About to clean up.");
//        mHandler.remove(mFocusResetRunnable);
//        if (mFocusEndRunnable != null) {
//            mHandler.remove(mFocusEndRunnable);
//        }
//        if (mCamera != null) {
//            try {
//                LOG.i("onStopEngine:", "Clean up.", "Releasing camera.");
//                mCamera.release();
//                LOG.i("onStopEngine:", "Clean up.", "Released camera.");
//            } catch (Exception e) {
//                LOG.w("onStopEngine:", "Clean up.", "Exception while releasing camera.", e);
//            }
//            mCamera = null;
//            mCameraOptions = null;
//        }
//        mCameraOptions = null;
//        mCamera = null;
//        LOG.w("onStopEngine:", "Clean up.", "Returning.");
//        return Tasks.forResult(null);
//    }
//
//    //endregion
//
//    //region Pictures
//
//    @EngineThread
//    @Override
//    protected void onTakePicture(@NonNull PictureResult.Stub stub, boolean doMetering) {
//        stub.rotation = getAngles().offset(Reference.SENSOR, Reference.OUTPUT,
//                Axis.RELATIVE_TO_SENSOR);
//        stub.size = getPictureSize(Reference.OUTPUT);
//        mPictureRecorder = new Full1PictureRecorder(stub, Camera1Engine.this, mCamera);
//        mPictureRecorder.take();
//    }
//
//    @EngineThread
//    @Override
//    protected void onTakePictureSnapshot(@NonNull PictureResult.Stub stub,
//                                         @NonNull AspectRatio outputRatio,
//                                         boolean doMetering) {
//        // Not the real size: it will be cropped to match the view ratio
//        stub.size = getUncroppedSnapshotSize(Reference.OUTPUT);
//        // Actually it will be rotated and set to 0.
//        stub.rotation = getAngles().offset(Reference.SENSOR, Reference.OUTPUT, Axis.RELATIVE_TO_SENSOR);
//        if (mPreview instanceof GlCameraPreview && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            mPictureRecorder = new SnapshotGlPictureRecorder(stub, this, (GlCameraPreview) mPreview, outputRatio);
//        } else {
//            mPictureRecorder = new Snapshot1PictureRecorder(stub, this, mCamera, outputRatio);
//        }
//        mPictureRecorder.take();
//    }
//
//
//
//    //endregion
//
//    //region Parameters
//
//    private void applyAllParameters(@NonNull Camera.Parameters params) {
//        applyDefaultFocus(params);
//        applyFlash(params, Flash.OFF);
//        applyLocation(params, null);
//        applyWhiteBalance(params, WhiteBalance.AUTO);
//        applyHdr(params, Hdr.OFF);
//        applyZoom(params, 0F);
//        applyExposureCorrection(params, 0F);
//        applyPlaySounds(mPlaySounds);
//    }
//
//    private void applyDefaultFocus(@NonNull Camera.Parameters params) {
//        List<String> modes = params.getSupportedFocusModes();
//
//        if (modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
//            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
//            return;
//        }
//
//        if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
//            params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
//            return;
//        }
//
//        if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
//            params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
//            //noinspection UnnecessaryReturnStatement
//            return;
//        }
//    }
//
//    @Override
//    public void setFlash(@NonNull Flash flash) {
//        final Flash old = mFlash;
//        mFlash = flash;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() == STATE_STARTED) {
//                    Camera.Parameters params = mCamera.getParameters();
//                    if (applyFlash(params, old)) mCamera.setParameters(params);
//                }
//                mFlashOp.end(null);
//            }
//        });
//    }
//
//    private boolean applyFlash(@NonNull Camera.Parameters params, @NonNull Flash oldFlash) {
//        if (mCameraOptions.supports(mFlash)) {
//            params.setFlashMode(mMapper.mapFlash(mFlash));
//            return true;
//        }
//        mFlash = oldFlash;
//        return false;
//    }
//
//    @Override
//    public void setLocation(@Nullable Location location) {
//        final Location oldLocation = mLocation;
//        mLocation = location;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() == STATE_STARTED) {
//                    Camera.Parameters params = mCamera.getParameters();
//                    if (applyLocation(params, oldLocation)) mCamera.setParameters(params);
//                }
//                mLocationOp.end(null);
//            }
//        });
//    }
//
//    private boolean applyLocation(@NonNull Camera.Parameters params,
//                                  @SuppressWarnings("unused") @Nullable Location oldLocation) {
//        if (mLocation != null) {
//            params.setGpsLatitude(mLocation.getLatitude());
//            params.setGpsLongitude(mLocation.getLongitude());
//            params.setGpsAltitude(mLocation.getAltitude());
//            params.setGpsTimestamp(mLocation.getTime());
//            params.setGpsProcessingMethod(mLocation.getProvider());
//        }
//        return true;
//    }
//
//    @Override
//    public void setWhiteBalance(@NonNull WhiteBalance whiteBalance) {
//        final WhiteBalance old = mWhiteBalance;
//        mWhiteBalance = whiteBalance;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() == STATE_STARTED) {
//                    Camera.Parameters params = mCamera.getParameters();
//                    if (applyWhiteBalance(params, old)) mCamera.setParameters(params);
//                }
//                mWhiteBalanceOp.end(null);
//            }
//        });
//    }
//
//    private boolean applyWhiteBalance(@NonNull Camera.Parameters params,
//                                      @NonNull WhiteBalance oldWhiteBalance) {
//        if (mCameraOptions.supports(mWhiteBalance)) {
//            params.setWhiteBalance(mMapper.mapWhiteBalance(mWhiteBalance));
//            return true;
//        }
//        mWhiteBalance = oldWhiteBalance;
//        return false;
//    }
//
//    @Override
//    public void setHdr(@NonNull Hdr hdr) {
//        final Hdr old = mHdr;
//        mHdr = hdr;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() == STATE_STARTED) {
//                    Camera.Parameters params = mCamera.getParameters();
//                    if (applyHdr(params, old)) mCamera.setParameters(params);
//                }
//                mHdrOp.end(null);
//            }
//        });
//    }
//
//    private boolean applyHdr(@NonNull Camera.Parameters params, @NonNull Hdr oldHdr) {
//        if (mCameraOptions.supports(mHdr)) {
//            params.setSceneMode(mMapper.mapHdr(mHdr));
//            return true;
//        }
//        mHdr = oldHdr;
//        return false;
//    }
//
//    @Override
//    public void setZoom(final float zoom, @Nullable final PointF[] points, final boolean notify) {
//        final float old = mZoomValue;
//        mZoomValue = zoom;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() == STATE_STARTED) {
//                    Camera.Parameters params = mCamera.getParameters();
//                    if (applyZoom(params, old)) {
//                        mCamera.setParameters(params);
//                        if (notify) {
//                            mCallback.dispatchOnZoomChanged(mZoomValue, points);
//                        }
//                    }
//                }
//                mZoomOp.end(null);
//            }
//        });
//    }
//
//    private boolean applyZoom(@NonNull Camera.Parameters params, float oldZoom) {
//        if (mCameraOptions.isZoomSupported()) {
//            float max = params.getMaxZoom();
//            params.setZoom((int) (mZoomValue * max));
//            mCamera.setParameters(params);
//            return true;
//        }
//        mZoomValue = oldZoom;
//        return false;
//    }
//
//    @Override
//    public void setExposureCorrection(final float EVvalue, @NonNull final float[] bounds,
//                                      @Nullable final PointF[] points, final boolean notify) {
//        final float old = mExposureCorrectionValue;
//        mExposureCorrectionValue = EVvalue;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() == STATE_STARTED) {
//                    Camera.Parameters params = mCamera.getParameters();
//                    if (applyExposureCorrection(params, old)) {
//                        mCamera.setParameters(params);
//                        if (notify) {
//                            mCallback.dispatchOnExposureCorrectionChanged(mExposureCorrectionValue,
//                                    bounds, points);
//                        }
//                    }
//                }
//                mExposureCorrectionOp.end(null);
//            }
//        });
//    }
//
//    private boolean applyExposureCorrection(@NonNull Camera.Parameters params,
//                                            float oldExposureCorrection) {
//        if (mCameraOptions.isExposureCorrectionSupported()) {
//            // Just make sure we're inside boundaries.
//            float max = mCameraOptions.getExposureCorrectionMaxValue();
//            float min = mCameraOptions.getExposureCorrectionMinValue();
//            float val = mExposureCorrectionValue;
//            val = val < min ? min : val > max ? max : val; // cap
//            mExposureCorrectionValue = val;
//            // Apply.
//            int indexValue = (int) (mExposureCorrectionValue
//                    / params.getExposureCompensationStep());
//            params.setExposureCompensation(indexValue);
//            return true;
//        }
//        mExposureCorrectionValue = oldExposureCorrection;
//        return false;
//    }
//
//    @Override
//    public void setPlaySounds(boolean playSounds) {
//        final boolean old = mPlaySounds;
//        mPlaySounds = playSounds;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() == STATE_STARTED) {
//                    applyPlaySounds(old);
//                }
//                mPlaySoundsOp.end(null);
//            }
//        });
//    }
//
//    @SuppressWarnings("UnusedReturnValue")
//    @TargetApi(17)
//    private boolean applyPlaySounds(boolean oldPlaySound) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
//            Camera.CameraInfo info = new Camera.CameraInfo();
//            Camera.getCameraInfo(mCameraId, info);
//            if (info.canDisableShutterSound) {
//                try {
//                    // this method is documented to throw on some occasions. #377
//                    return mCamera.enableShutterSound(mPlaySounds);
//                } catch (RuntimeException exception) {
//                    return false;
//                }
//            }
//        }
//        if (mPlaySounds) {
//            return true;
//        }
//        mPlaySounds = oldPlaySound;
//        return false;
//    }
//
//    //endregion
//
//    //region Frame Processing
//
//    @NonNull
//    @Override
//    protected FrameManager instantiateFrameManager() {
//        return new FrameManager(2, this);
//    }
//
//    @Override
//    public void onBufferAvailable(@NonNull byte[] buffer) {
//        if (getEngineState() == STATE_STARTED) {
//            mCamera.addCallbackBuffer(buffer);
//        }
//    }
//
//    @Override
//    public void onPreviewFrame(byte[] data, Camera camera) {
//        if (data == null) {
//            // Let's test this with an exception.
//            throw new RuntimeException("Camera1 returns null data from onPreviewFrame! " +
//                    "This would make the frame processors crash later.");
//        }
//        Frame frame = getFrameManager().getFrame(data,
//                System.currentTimeMillis(),
//                getAngles().offset(Reference.SENSOR, Reference.OUTPUT, Axis.RELATIVE_TO_SENSOR));
//        mCallback.dispatchFrame(frame);
//    }
//
//    //endregion
//
//    //region Auto Focus
//
//    @Override
//    public void startAutoFocus(@Nullable final Gesture gesture, @NonNull final PointF point) {
//        // Must get width and height from the UI thread.
//        int viewWidth = 0, viewHeight = 0;
//        if (mPreview != null && mPreview.hasSurface()) {
//            viewWidth = mPreview.getView().getWidth();
//            viewHeight = mPreview.getView().getHeight();
//        }
//        final int viewWidthF = viewWidth;
//        final int viewHeightF = viewHeight;
//        mHandler.run(new Runnable() {
//            @Override
//            public void run() {
//                if (getEngineState() < STATE_STARTED) return;
//                if (!mCameraOptions.isAutoFocusSupported()) return;
//                final PointF p = new PointF(point.x, point.y); // copy.
//                int offset = getAngles().offset(Reference.SENSOR, Reference.VIEW, Axis.ABSOLUTE);
//                List<Camera.Area> meteringAreas2 = computeMeteringAreas(p.x, p.y,
//                        viewWidthF, viewHeightF, offset);
//                List<Camera.Area> meteringAreas1 = meteringAreas2.subList(0, 1);
//
//                // At this point we are sure that camera supports auto focus... right?
//                // Look at CameraView.onTouchEvent().
//                Camera.Parameters params = mCamera.getParameters();
//                int maxAF = params.getMaxNumFocusAreas();
//                int maxAE = params.getMaxNumMeteringAreas();
//                if (maxAF > 0) params.setFocusAreas(maxAF > 1 ? meteringAreas2 : meteringAreas1);
//                if (maxAE > 0) params.setMeteringAreas(maxAE > 1 ? meteringAreas2 : meteringAreas1);
//                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
//                mCamera.setParameters(params);
//                mCallback.dispatchOnFocusStart(gesture, p);
//
//                // The auto focus callback is not guaranteed to be called, but we really want it
//                // to be. So we remove the old runnable if still present and post a new one.
//                if (mFocusEndRunnable != null) mHandler.remove(mFocusEndRunnable);
//                mFocusEndRunnable = new Runnable() {
//                    @Override
//                    public void run() {
//                        mCallback.dispatchOnFocusEnd(gesture, false, p);
//                    }
//                };
//                mHandler.post(AUTOFOCUS_END_DELAY_MILLIS, mFocusEndRunnable);
//
//                // Wrapping autoFocus in a try catch to handle some device specific exceptions,
//                // see See https://github.com/natario1/CameraView/issues/181.
//                try {
//                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
//                        @Override
//                        public void onAutoFocus(boolean success, Camera camera) {
//                            if (mFocusEndRunnable != null) {
//                                mHandler.remove(mFocusEndRunnable);
//                                mFocusEndRunnable = null;
//                            }
//                            mCallback.dispatchOnFocusEnd(gesture, success, p);
//                            mHandler.remove(mFocusResetRunnable);
//                            if (shouldResetAutoFocus()) {
//                                mHandler.post(getAutoFocusResetDelay(), mFocusResetRunnable);
//                            }
//                        }
//                    });
//                } catch (RuntimeException e) {
//                    // Let the mFocusEndRunnable do its job. (could remove it and quickly dispatch
//                    // onFocusEnd here, but let's make it simpler).
//                }
//
//            }
//        });
//    }
//
//    @NonNull
//    @EngineThread
//    private static List<Camera.Area> computeMeteringAreas(double viewClickX, double viewClickY,
//                                                          int viewWidth, int viewHeight,
//                                                          int sensorToDisplay) {
//        // Event came in view coordinates. We must rotate to sensor coordinates.
//        // First, rescale to the -1000 ... 1000 range.
//        int displayToSensor = -sensorToDisplay;
//        viewClickX = -1000d + (viewClickX / (double) viewWidth) * 2000d;
//        viewClickY = -1000d + (viewClickY / (double) viewHeight) * 2000d;
//
//        // Apply rotation to this point.
//        // https://academo.org/demos/rotation-about-point/
//        double theta = ((double) displayToSensor) * Math.PI / 180;
//        double sensorClickX = viewClickX * Math.cos(theta) - viewClickY * Math.sin(theta);
//        double sensorClickY = viewClickX * Math.sin(theta) + viewClickY * Math.cos(theta);
//        LOG.i("focus:", "viewClickX:", viewClickX, "viewClickY:", viewClickY);
//        LOG.i("focus:", "sensorClickX:", sensorClickX, "sensorClickY:", sensorClickY);
//
//        // Compute the rect bounds.
//        Rect rect1 = computeMeteringArea(sensorClickX, sensorClickY, 150d);
//        int weight1 = 1000; // 150 * 150 * 1000 = more than 10.000.000
//        Rect rect2 = computeMeteringArea(sensorClickX, sensorClickY, 300d);
//        int weight2 = 100; // 300 * 300 * 100 = 9.000.000
//
//        List<Camera.Area> list = new ArrayList<>(2);
//        list.add(new Camera.Area(rect1, weight1));
//        list.add(new Camera.Area(rect2, weight2));
//        return list;
//    }
//
//    @NonNull
//    private static Rect computeMeteringArea(double centerX, double centerY, double size) {
//        double delta = size / 2d;
//        int top = (int) Math.max(centerY - delta, -1000);
//        int bottom = (int) Math.min(centerY + delta, 1000);
//        int left = (int) Math.max(centerX - delta, -1000);
//        int right = (int) Math.min(centerX + delta, 1000);
//        LOG.i("focus:", "computeMeteringArea:",
//                "top:", top,
//                "left:", left,
//                "bottom:", bottom,
//                "right:", right);
//        return new Rect(left, top, right, bottom);
//    }
//
//    private final Runnable mFocusResetRunnable = new Runnable() {
//        @Override
//        public void run() {
//            if (getEngineState() < STATE_STARTED) return;
//            mCamera.cancelAutoFocus();
//            Camera.Parameters params = mCamera.getParameters();
//            int maxAF = params.getMaxNumFocusAreas();
//            int maxAE = params.getMaxNumMeteringAreas();
//            if (maxAF > 0) params.setFocusAreas(null);
//            if (maxAE > 0) params.setMeteringAreas(null);
//            applyDefaultFocus(params); // Revert to internal focus.
//            mCamera.setParameters(params);
//        }
//    };
//
//    //endregion
//}
//
