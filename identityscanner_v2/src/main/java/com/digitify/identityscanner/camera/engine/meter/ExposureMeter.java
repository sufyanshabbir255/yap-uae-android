package com.digitify.identityscanner.camera.engine.meter;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.digitify.identityscanner.camera.CameraLogger;
import com.digitify.identityscanner.camera.engine.action.ActionHolder;

import java.util.List;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class ExposureMeter extends BaseMeter {

    private static final String TAG = ExposureMeter.class.getSimpleName();
    private static final CameraLogger LOG = CameraLogger.create(TAG);

    private static final int STATE_WAITING_PRECAPTURE = 0;
    private static final int STATE_WAITING_PRECAPTURE_END = 1;

    @SuppressWarnings("WeakerAccess")
    public ExposureMeter(@NonNull List<MeteringRectangle> areas, boolean skipIfPossible) {
        super(areas, skipIfPossible);
    }

    @Override
    protected boolean checkIsSupported(@NonNull ActionHolder holder) {
        // In our case, this means checking if we support the AE precapture trigger.
        boolean isNotLegacy = readCharacteristic(
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL, -1)
                != CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
        Integer aeMode = holder.getBuilder(this).get(CaptureRequest.CONTROL_AE_MODE);
        boolean isAEOn = aeMode != null &&
                (aeMode == CameraCharacteristics.CONTROL_AE_MODE_ON
                        || aeMode == CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                        || aeMode == CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH
                        || aeMode == CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE
                        || aeMode == 5
                        /* CameraCharacteristics.CONTROL_AE_MODE_ON_EXTERNAL_FLASH, API 28 */);
        boolean result = isNotLegacy && isAEOn;
        LOG.i("checkIsSupported:", result);
        return result;
    }

    @Override
    protected boolean checkShouldSkip(@NonNull ActionHolder holder) {
        Integer aeState = holder.getLastResult(this).get(CaptureResult.CONTROL_AE_STATE);
        boolean result = aeState != null && aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED;
        LOG.i("checkShouldSkip:", result);
        return result;
    }

    @Override
    protected void onStarted(@NonNull ActionHolder holder, @NonNull List<MeteringRectangle> areas) {
        LOG.i("onStarted:", "with areas:", areas);

        // Launch the precapture trigger.
        holder.getBuilder(this).set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

        // Check the regions.
        int maxRegions = readCharacteristic(CameraCharacteristics.CONTROL_MAX_REGIONS_AE,
                0);
        if (!areas.isEmpty() && maxRegions > 0) {
            int max = Math.min(maxRegions, areas.size());
            holder.getBuilder(this).set(CaptureRequest.CONTROL_AE_REGIONS,
                    areas.subList(0, max).toArray(new MeteringRectangle[]{}));
        }

        // Apply
        holder.applyBuilder(this);
        setState(STATE_WAITING_PRECAPTURE);
    }

    @Override
    protected void onCompleted(@NonNull ActionHolder holder) {
        super.onCompleted(holder);
        // Remove (but not apply) the risky parameter so it is not included in new requests.
        // Documentation about this key says that this should be allowed.
        holder.getBuilder(this).set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, null);
    }

    @Override
    public void onCaptureCompleted(@NonNull ActionHolder holder, @NonNull CaptureRequest request,
                                   @NonNull TotalCaptureResult result) {
        super.onCaptureCompleted(holder, request, result);
        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
        Integer aeTriggerState = result.get(CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER);
        LOG.i("onCaptureCompleted:", "aeState:", aeState, "aeTriggerState:", aeTriggerState);
        if (aeState == null) return;

        if (getState() == STATE_WAITING_PRECAPTURE) {
            switch (aeState) {
                case CaptureResult.CONTROL_AE_STATE_PRECAPTURE: {
                    setState(STATE_WAITING_PRECAPTURE_END);
                    break;
                }
                case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED: {
                    // PRECAPTURE is a transient state. Being here might mean that precapture run
                    // and was successful, OR that the trigger was not even received yet. To
                    // distinguish, check the trigger state.
                    if (aeTriggerState != null && aeTriggerState
                            == CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_START) {
                        setSuccessful(true);
                        setState(Companion.getSTATE_COMPLETED());
                    }
                    break;
                }
                case CaptureResult.CONTROL_AE_STATE_LOCKED: {
                    // There's nothing we can do, AE was locked, triggers are ignored.
                    setSuccessful(false);
                    setState(Companion.getSTATE_COMPLETED());
                    break;
                }
                case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                case CaptureResult.CONTROL_AE_STATE_SEARCHING: {
                    // Wait...
                    break;
                }
            }
        }

        if (getState() == STATE_WAITING_PRECAPTURE_END) {
            switch (aeState) {
                case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED: {
                    setSuccessful(true);
                    setState(Companion.getSTATE_COMPLETED());
                    break;
                }
                case CaptureResult.CONTROL_AE_STATE_LOCKED: {
                    // There's nothing we can do, AE was locked, triggers are ignored.
                    setSuccessful(false);
                    setState(Companion.getSTATE_COMPLETED());
                    break;
                }
                case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                case CaptureResult.CONTROL_AE_STATE_SEARCHING: {
                    // Wait...
                    break;
                }
            }
        }
    }
}