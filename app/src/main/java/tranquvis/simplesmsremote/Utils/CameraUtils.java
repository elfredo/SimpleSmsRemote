package tranquvis.simplesmsremote.Utils;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import org.apache.commons.lang3.NotImplementedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import tranquvis.simplesmsremote.Data.CaptureSettings;

/**
 * Created by Andi on 15.10.2016.
 */

public class CameraUtils {
    private static final String TAG = CameraUtils.class.getName();

    public static void TakePhoto(final Context context, MyCameraInfo camera,
                                 CaptureSettings settings) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            TakePhoto2(context, camera, settings);
        } else {
            TakePhoto1(context, camera, settings);
        }
    }

    /**
     * Get first camera whose lens facing is back.
     * @param context app context
     * @return the camera information or null if no camera was found
     * @throws Exception
     */
    public static MyCameraInfo GetBackCamera(Context context) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return GetBackCamera2(context);
        } else {
            return GetBackCamera1(context);
        }
    }

    public static List<MyCameraInfo> GetAllCameras(Context context) throws Exception
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return GetAllCameras2(context);
        } else {
            return GetAllCameras1(context);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static List<MyCameraInfo> GetAllCameras2(Context context) throws Exception
    {
        List<MyCameraInfo> cameras = new ArrayList<>();

        CameraManager cameraManager =
                (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        for (String cameraId : cameraManager.getCameraIdList())
        {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            MyCameraInfo cameraInfo = MyCameraInfo.CreateFromCameraCharacteristics(cameraId,
                    characteristics);
            cameras.add(cameraInfo);
        }

        return cameras;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static Iterable<MyCameraInfo> GetAllCamerasIterable2(Context context) throws Exception
    {
        final CameraManager cameraManager =
                (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        final String[] cameraIdList = cameraManager.getCameraIdList();

        return new Iterable<MyCameraInfo>() {
            @Override
            public Iterator<MyCameraInfo> iterator()
            {
                return new Iterator<MyCameraInfo>() {
                    private int pos = 0;

                    @Override
                    public boolean hasNext()
                    {
                        return pos < cameraIdList.length - 1;
                    }

                    @Override
                    public MyCameraInfo next()
                    {
                        String cameraId = cameraIdList[pos++];
                        CameraCharacteristics characteristics;
                        try
                        {
                            characteristics = cameraManager.getCameraCharacteristics(cameraId);
                            return MyCameraInfo.CreateFromCameraCharacteristics(cameraId,
                                    characteristics);
                        } catch (CameraAccessException e)
                        {
                            return null;
                        }
                    }
                };
            }
        };
    }

    private static List<MyCameraInfo> GetAllCameras1(Context context)
    {
        throw new NotImplementedException("TODO");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static MyCameraInfo GetBackCamera2(Context context) throws Exception {
        for (MyCameraInfo cameraInfo : GetAllCamerasIterable2(context))
        {
            if(cameraInfo.getLensFacing() != null
                    && cameraInfo.getLensFacing() == MyCameraInfo.LensFacing.BACK)
                return cameraInfo;
        }
        return null;
    }

    private static MyCameraInfo GetBackCamera1(Context context) {
        throw new NotImplementedException("TODO");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void TakePhoto2(final Context context, MyCameraInfo camera,
                                   final CaptureSettings settings) throws Exception, SecurityException {
        CameraManager cameraManager =
                (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        //create image surface
        final ImageReader imageReader = ImageReader.newInstance(settings.getResolution().getWidth(),
                settings.getResolution().getHeight(), PixelFormat.RGBA_8888, 1);
        final List<Surface> surfaceList = new ArrayList<>();
        surfaceList.add(imageReader.getSurface());

        //open camera
        CameraDevice cameraDevice = OpenCameraSync2(context, cameraManager, camera);
        if(cameraDevice == null) {
            throw new Exception("Failed to open camera.");
        }

        //open capture session
        CameraCaptureSession captureSession = GetCaptureSessionSync2(context, cameraDevice,
                surfaceList);
        if(captureSession == null) {
            cameraDevice.close();
            throw new Exception("Failed to configure capture session.");
        }

        CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(
                CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureRequestBuilder.addTarget(surfaceList.get(0));

        //configure capture based on settings
        if(settings.isAutofocus())
        {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_AUTO);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_START);
        }

        if(settings.getFlashlight() == CaptureSettings.FlashlightMode.ON)
        {
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
        }
        else if(settings.getFlashlight() == CaptureSettings.FlashlightMode.OFF)
        {
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }

        CaptureRequest captureRequest = captureRequestBuilder.build();

        //capture photo
        boolean captureSuccess = CapturePhotoSync2(context, captureSession, captureRequest);
        if(!captureSuccess) {
            captureSession.close();
            cameraDevice.close();
            throw new Exception("Failed to capture photo with camera.");
        }

        cameraDevice.close();

        //get bitmap from ImageReader
        Bitmap bitmap = ImageUtils.GetBitmapFromImageReader(imageReader);
        imageReader.close();

        //save file
        File file = new File(settings.getOutputPath());
        file.getParentFile().mkdirs(); //create parent directories
        if(!file.createNewFile())
            throw new Exception("Failed to create file for image.");

        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file);
            bitmap.compress(settings.getCompressFormat(), 100, os);
            Log.i(TAG, "image saved successfully");
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        finally
        {
            try {
                if(os != null)
                    os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void TakePhoto1(final Context context, MyCameraInfo camera,
                                   CaptureSettings settings) throws Exception
    {
        throw new NotImplementedException("TODO");
    }

    /**
     * Capture photo synchronously.
     * The method will wait a common time until the capture completes.
     * When the method reaches this max. time the process is aborted.
     * @param context app context
     * @param captureSession capture session
     * @param captureRequest capture request
     * @return if the capture was successful
     * @throws Exception
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static boolean CapturePhotoSync2(Context context, CameraCaptureSession captureSession,
            CaptureRequest captureRequest) throws Exception
    {
        final CaptureRequestResult result = new CaptureRequestResult();
        captureSession.capture(captureRequest, new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                           TotalCaptureResult captureResult) {
                super.onCaptureCompleted(session, request, captureResult);
                result.captureSuccess = true;
                result.requestFinished = true;
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                result.requestFinished = true;
            }

        }, new Handler(context.getMainLooper()));

        //wait until photo is captured
        final int maxWaitTime = 5000; //milliseconds
        final int timeout = 10;

        long startTime = System.currentTimeMillis();
        while(!result.requestFinished && (System.currentTimeMillis() - startTime) < maxWaitTime)
        {
            Thread.sleep(timeout);
        }
        captureSession.abortCaptures();

        return result.captureSuccess;
    }

    /**
     * Get capture session synchronously.
     * The method will wait a common time until the configuration completes.
     * When the method reaches this max. time the process is aborted.
     * @param context app context
     * @param cameraDevice camera device
     * @param surfaceList list of image outputs
     * @return capture session
     * @throws Exception
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static CameraCaptureSession GetCaptureSessionSync2(Context context,
            CameraDevice cameraDevice, List<Surface> surfaceList) throws Exception
    {
        final CaptureSessionRequestResult result = new CaptureSessionRequestResult();
        cameraDevice.createCaptureSession(surfaceList, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                result.cameraCaptureSession = cameraCaptureSession;
                result.requestFinished = true;
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                result.requestFinished = true;
            }
        }, new Handler(context.getMainLooper()));

        //wait until camera session is configured
        final int maxWaitTime = 3000; //milliseconds
        final int timeout = 10;

        long startTime = System.currentTimeMillis();
        while(!result.requestFinished && (System.currentTimeMillis() - startTime) < maxWaitTime)
        {
            Thread.sleep(timeout);
        }

        return result.cameraCaptureSession;
    }

    /**
     * Open camera synchronously.
     * The method will wait a common time until the open process completes.
     * When the method reaches this max. time the process is aborted.
     * @param context app context
     * @param cameraManager camera manager
     * @param cameraInfo camera information
     * @return the camera device
     * @throws Exception
     * @throws SecurityException
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static CameraDevice OpenCameraSync2(Context context, CameraManager cameraManager,
            MyCameraInfo cameraInfo) throws Exception, SecurityException
    {
        final CameraOpenRequestResult result = new CameraOpenRequestResult();
        cameraManager.openCamera(cameraInfo.getId(), new CameraDevice.StateCallback() {
            @Override
            public void onOpened(final CameraDevice cameraDevice) {
                result.cameraDevice = cameraDevice;
                result.requestFinished = true;
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                result.requestFinished = true;
            }

            @Override
            public void onError(CameraDevice cameraDevice, int i) {
                result.requestFinished = true;
            }
        }, new Handler(context.getMainLooper()));

        //wait until camera is opened
        final int maxWaitTime = 3000; //milliseconds
        final int timeout = 10;

        long startTime = System.currentTimeMillis();
        while(!result.requestFinished && (System.currentTimeMillis() - startTime) < maxWaitTime)
        {
            Thread.sleep(timeout);
        }

        return result.cameraDevice;
    }

    private static class CameraOpenRequestResult
    {
        private boolean requestFinished = false;
        private CameraDevice cameraDevice = null;
    }

    private static class CaptureSessionRequestResult
    {
        private boolean requestFinished = false;
        private CameraCaptureSession cameraCaptureSession = null;
    }

    private static class CaptureRequestResult
    {
        private boolean requestFinished = false;
        private boolean captureSuccess = false;
    }

    public static class MyCameraInfo
    {
        private String id;
        private Size resolution;
        private LensFacing lensFacing = null;
        private boolean autofocusSupport = false;
        private boolean flashlightSupport = false;

        public MyCameraInfo(String id, Size resolution) {
            this.id = id;
            this.resolution = resolution;
        }

        public String getId() {
            return id;
        }

        public Size getResolution() {
            return resolution;
        }

        public boolean isAutofocusSupport() {
            return autofocusSupport;
        }

        public void setAutofocusSupport(boolean autofocusSupport) {
            this.autofocusSupport = autofocusSupport;
        }

        public boolean isFlashlightSupport()
        {
            return flashlightSupport;
        }

        public void setFlashlightSupport(boolean flashlightSupport)
        {
            this.flashlightSupport = flashlightSupport;
        }

        public LensFacing getLensFacing()
        {
            return lensFacing;
        }

        public void setLensFacing(LensFacing lensFacing)
        {
            this.lensFacing = lensFacing;
        }

        public CaptureSettings getDefaultCaptureSettings()
        {
            String defaultPhotosPath = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DCIM).getAbsolutePath();
            String filename = "remotely_" + String.valueOf(System.currentTimeMillis()) + ".jpg";
            String path = defaultPhotosPath + File.separator + filename;

            CaptureSettings captureSettings = new CaptureSettings(resolution,
                    Bitmap.CompressFormat.JPEG, path);
            captureSettings.setAutofocus(autofocusSupport);
            captureSettings.setFlashlight(CaptureSettings.FlashlightMode.AUTO);
            return captureSettings;
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public static MyCameraInfo CreateFromCameraCharacteristics(String cameraId,
                CameraCharacteristics characteristics)
        {
            Size resolution =
                    characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
            MyCameraInfo cameraInfo = new MyCameraInfo(cameraId, resolution);

            // supported functionality depends on the supported hardware level
            switch (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))
            {
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:

                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:

                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                    cameraInfo.setAutofocusSupport(true);
                case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                    break;
            }

            if(characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE).booleanValue())
                cameraInfo.setFlashlightSupport(true);

            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if(lensFacing != null)
            {
                if(lensFacing == CameraCharacteristics.LENS_FACING_BACK)
                    cameraInfo.setLensFacing(LensFacing.BACK);
                else if(lensFacing == CameraCharacteristics.LENS_FACING_FRONT)
                    cameraInfo.setLensFacing(LensFacing.FRONT);
                else if(lensFacing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                    cameraInfo.setLensFacing(LensFacing.EXTERNAL);
            }

            //TODO add more info

            return cameraInfo;
        }

        public enum LensFacing
        {
            FRONT, BACK, EXTERNAL
        }
    }
}
