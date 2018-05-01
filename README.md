# :star2:&nbsp;XCodeScanner

A new frame for decode QR code and bar code on Android. It's faster, simpler and more accurate. It's based on ZBar, compatible with Android4.0 (API14) and above.

[中国人当然看中文的](/README_CN.md)

## Catalog

* [Demo](#demo)
* [Function](#function)
* [UML](#uml)
* [Gradle](#gradle)
* [Usage](#usage)
* [Update plan](#updateplan)
* [Changelog](#changelog)
* [Author](#author)

## Demo

|Download|Effect|
|:---:|:---:|
|[click to download](http://fir.im/XCodeScanner) Or scan the following QR code<br/>[![demo](/download.png)](http://fir.im/XCodeScanner  "Scan code to download")|[![gif](/demo.gif)](http://fir.im/XCodeScanner  "Effect")|

## Function
This project is based on the [ZBar](https://github.com/ZBar/ZBar) development. It has highly encapsulated the view, camera, and decoding, and reduces the coupling between the three and increases the flexibility of configuration.

* View
    * `AdjustTextureView`, extended from `TextureView`, can correct the image according to its size, frame width and rotation angle, and solve abnormal problems such as preview image distortion.
    * `ScannerFrameView`, extended from `View`, you can customize the size, color, and animation of the scan box, the four corners, and the scan line by using xml properties or interfaces. **The specific use of reference source code comments**.
    * `MaskRelativeLayout`&`MaskConstraintLayout`，extended from `RelativeLayout`&`ConstraintLayout`, as the parentView of ScannerFrameView, used to draw the outer shadow of the scan box.

* Camera
    * Compatible with `android.hardware.camera2` and `android.hardware.Camera` API。
    * Open the camera from the child thread to prevent blocking of the main thread.
    * Use the singleton to prevent multiple instances from simultaneously operating the camera device to cause an exception.
    * According to the preview size, the image frame size, and the preview direction, the actual position of the scan frame on the image frame is calculated, can decode the scan box area only.
    * Use `TextureReader` instead of `ImageReader`, using openGL to draw the image texture, mainly to solve the problem of serious frame loss in the preview, real-time output YUV format image.

* Decoding
    * Supports specified area decoding.
    * You can specify the type of barcode that needs to be decoded.
    * The callback result contains the barcode type and barcode precision. You can configure the dirty data filter rule.

## UML

[![uml](/uml.png)](#UML  "UML")

## Gradle

Add the following code in module's `build.gradle`
```
    dependencies {
        implementation 'cn.simonlee.xcodescanner:zbar:1.1.5'
    }
```

## Usage

* **STEP.1**<br/>
Get the CameraScanner instance in the Activity's onCreate, and set the monitor to CameraScanner and TextureView.
```java
public void onCreate(Bundle savedInstanceState) {
   super.onCreate(savedInstanceState);
   setContentView(R.layout.activity_scan_constraint);
   mTextureView = findViewById(R.id.textureview);
   mTextureView.setSurfaceTextureListener(this);
   if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      mCameraScanner = OldCameraScanner.getInstance();
   } else {
      mCameraScanner = NewCameraScanner.getInstance();
   }
   mCameraScanner.setCameraListener(this);
}
```
* **STEP.2**<br/>
Set the width and height of the SurfaceTexture and TextureView in onSurfaceTextureAvailable, then open the camera
```java
public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
   mCameraScanner.setSurfaceTexture(surface);
   mCameraScanner.setPreviewSize(width, height);
   mCameraScanner.openCamera(this.getApplicationContext());
}
```
* **STEP.3**<br/>
Set the width and height of the image frame in openCameraSuccess, and get the ZBarDecoder instance set to CameraScanner.
```java
public void openCameraSuccess(int frameWidth, int frameHeight, int frameDegree) {
   mTextureView.setImageFrameMatrix(frameWidth, frameHeight, frameDegree);
   if (mGraphicDecoder == null) {
      mGraphicDecoder = new ZBarDecoder();//Use the parameterized construction method to specify the format for barcode.
      mGraphicDecoder.setDecodeListener(this);
   }
   //Call setFrameRect will limit the decoding area.
   mCameraScanner.setFrameRect(mScannerFrameView.getLeft(), mScannerFrameView.getTop(), mScannerFrameView.getRight(), mScannerFrameView.getBottom());
   mCameraScanner.setGraphicDecoder(mZBarDecoder);
}
```
* **STEP.4**<br/>
Get the decoded result in decodeSuccess of ZBarDecoder. You can customize the dirty data filter rule according to the returned barcode type and precision.
```java
public void decodeSuccess(int type, int quality, String result) {
   ToastHelper.showToast("[type" + type + "/quality" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
}
```
* **STEP.5**<br/>
Close camera and stop decoding in Activity's onDestroy.
```java
public void onDestroy() {
   mCameraScanner.setGraphicDecoder(null);
   mCameraScanner.detach();
   if (mGraphicDecoder != null) {
      mGraphicDecoder.setDecodeListener(null);
      mGraphicDecoder.detach();
   }
   super.onDestroy();
}
```
* **Attention.1**<br/>
Close camera in Activity's onPause.
```java
public void onPause() {
   mCameraScanner.closeCamera();
   super.onPause();
}
```
* **Attention.2**<br/>
Open camera in Activity's onRestart.
```java
public void onRestart() {
   //Some devices will call onSurfaceTextureAvailable to open the camera when they go to the foreground in the background.
   if (mTextureView.isAvailable()) {
      mCameraScanner.setSurfaceTexture(mTextureView.getSurfaceTexture());
      mCameraScanner.setPreviewSize(mTextureView.getWidth(), mTextureView.getHeight());
      mCameraScanner.openCamera(this.getApplicationContext());
   }
   super.onRestart();
}
```

## Update plan

*  Support decode local pictures.
*  Solve the problems caused by changes in the TextureView size.
*  Support environmental brightness monitoring and support open the flash.
*  Supports Zxing.
*  Support generation QR code.

## Changelog

*  V1.1.5   `2018/05/01`
   1. Solve the problem of application permission crash.
   2. Solve the problem of running a crash on the Meizu MX5.
   3. Modify the implementation of `ZBarDecoder` and `TextureReader` to reduce CPU usage.
   4. Add `AdjustTextureView`, extended from `TextureView`, for ease of compatibility testing.
   5. The pause/delay decode interface is migrated from `CameraScanner` to `GraphicDecoder`. `CameraScanner` may continue to call back `decodeSuccess` after a pause because of asynchronous.
   6. Release the open source library: `cn.simonlee.xcodescanner:zbar:1.1.5`.

*  V1.1.4   `2018/04/26`
   1. Solve the crash problem when running on Android4.2.
   2. Solve the problem that some low-end devices may preview severe dropped frames.
   3. Solve the problem that `OldCameraScanner` does not start decoding by default.
   4. Release the open source library: `cn.simonlee.xcodescanner:zbar:1.1.4`.

*  V1.1.3   `2018/04/25`
   1. Solve the problem of running crashes on some x86 devices.
   2. Added `stopDecode()` and `startDecode(int delay)` for stop/start decoding.
   3. Change the ZBar package name to `cn.simonlee.xcodescanner` from `com.simonlee.xcodescanner`.
   4. Release the open source library: `cn.simonlee.xcodescanner:zbar:1.1.3`.

*  V1.1.2   `2018/04/24`
   1. Solve the problem that setting the decoding format in `ZBarDecoder` does not take effect.

*  V1.1.1   `2018/04/16`
   1. Add the heightRatio attribute in `ScannerFrameView` to set the proportion of high relative to the parentView.
   2. Release the open source library: `cn.simonlee.codescanner:zbar:1.1.1`.

*  V1.1.0   `2018/04/16`
   1. Rewrite `ZBarDecoder` to solve the problem of bar code decodeing delay caused by single thread pool.
   2. Solve the problem of the `OldCameraScanner` scan box area exception.

*  V1.0.9   `2018/04/14`
   1. Solve the problem of the `NewCameraScanner` scan box area exception.
   2. Solve the problem of abnormality of `NewCameraScanner` when continuously rotating the device quickly.

*  V1.0.8   `2018/04/13`
    1. `AutoFixTextureView` renamed `AdjustTextureView`, overrides image correction.
    2. `Camera2Scanner` renamed `NewCameraScanner`.
    3. Added `OldCameraScanner` to support below Android 5.0.
    4. Lower minSdkVersion to 14.
    5. Solve the problem that may be caused by front-to-back background switching and horizontal and vertical screen switching.
    6. `NewCameraScanner` cancels `ImageReader` support.

*  V1.0.7   `2018/04/10`
    1. Adjust the method for calculating the width and height of the scan box.
    2. Added `MaskConstraintLayout` layout.
    3. Optimize `Camera2Scanner` to solve the crash caused by background switching.

*  V1.0.6   `2018/04/09`
    1. Adjust the code structure.

*  V1.0.5   `2018/03/29`
    1. Limit the maximum size of frame data to avoid decoding QR code failure due to high pixels.
    2. Shielding support for DataBar (RSS-14) format barcodes, this format is not practical and is prone to misjudgment.

*  V1.0.4   `2018/03/27`
    1. Modify `ZBarDecoder` to fix possible null pointer exceptions for multiple threads.
    2. Modify `GraphicDecoder`, replace EGL10 with EGL14, and solve some device incompatibility problems.

*  V1.0.3   `2018/03/23`
    1. Added `TextureReader` to retrieve frame data through double buffered textures instead of using `ImageReader`.
    2. Modify `GraphicDecoder`, the handler is placed in a subclass to operate.

*  V1.0.2   `2018/03/14`
    1. Added abstract class `GraphicDecoder` to extract the decoding module.
    2. Added `ZBarDecoder`, based on ZBar decoding, and increased decoding type and precision settings.
    3. Modify `ScannerFrameView`, scanline animation is achieved by ValueAnimator.
    4. Modify `Camera2Scanner` to fix the possible abnormalities caused by releasing the camera, and support setting scan box area.

*  V1.0.1   `2018/02/09`
    1. Add `ScannerFrameLayout`, extended from `RelativeLayout`, supports setting the scan box's position and size.
    2. Modify `ScannerFrameView` to customize the inside of the scan box.

*  V1.0.0   `2018/02/03`
    Initial submission code.

## Author

I am a Chinese who is not good at English, so this document was written using a translation tool. There should be a lot of grammatical mistakes, and I would appreciate it if you were willing to correct them.

This is my personal first open source project and I have also devoted a lot of energy to it. In the process of open source encountered many doubts and difficulties, which draw on a lot of very good technical blog. Here to pay tribute to those who are silently dedicated to open source! thank you all!

If you encounter any unusual problems such as crash, black screen, unrecognizable, unable to focus, preview dropped frames, memory leak, etc. during use, please feel free to ask us! At the same time, please attach as much as possible the device model, android version number, BUG recurring steps, exception logs, unrecognized images, etc. I will arrange for a solution as soon as possible.

If you find it useful, please  give me a **Star** to encourage me!:blush:

|Author|E-mail|Blog|WeChat|
|:---:|:---:|:---:|:---:|
|Simon Lee|jmlixiaomeng@163.com|[简书](https://www.jianshu.com/p/65df16604646) · [掘金](https://juejin.im/post/5adf0f166fb9a07ac23a62d1)|[![wechat](/wechat.png)](#Author)|
