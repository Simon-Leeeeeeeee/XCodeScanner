# :star2:&nbsp;XCodeScanner

一个更快更简单更精准的Android平台解析条形码及二维码的框架。采用[ZBar](https://github.com/ZBar/ZBar)解析图像数据，兼容` Android4.0 (API14) `及以上版本。

## 目录

* [示例demo](#示例demo)
* [功能介绍](#功能介绍)
* [UML类图](#uml类图)
* [集成方式](#集成方式)
* [使用方式](#使用方式)
* [更新计划](#更新计划)
* [版本记录](#版本记录)
* [关于作者](#关于作者)

## 示例demo

|Demo下载|示例效果|
|:---:|:---:|
|[点此下载](http://fir.im/XCodeScanner) 或扫描下面二维码<br/>[![demo](/download.png)](http://fir.im/XCodeScanner  "扫码下载示例程序")|[![gif](/demo.gif)](http://fir.im/XCodeScanner  "示例效果")|

## 功能介绍

本项目基于[ZBar](https://github.com/ZBar/ZBar)进行开发，分别对视图、相机、解码三个方面进行了高度封装，同时降低三者之间的耦合，增加可灵活配置性。

* 视图
    * 自定义`AdjustTextureView`，继承自`TextureView`，开放`setImageFrameMatrix`接口，可根据自身尺寸、图像帧宽高及旋转角度对图像进行校正，解决预览画面变形等异常问题。
    * 自定义`ScannerFrameView`，继承自`View`，可通过xml属性或接口自定义扫描框、四个角及扫描线的尺寸、颜色、动画等，**具体属性使用参考源码注解**。
    * 自定义`MaskRelativeLayout`&`MaskConstraintLayout`，分别继承自`RelativeLayout`&`ConstraintLayout`，做为`ScannerFrameView`的父容器，用于绘制扫描框外部阴影。

* 相机
    * 兼容`android.hardware.camera2`及`android.hardware.Camera`两版API。
    * 子线程开启camera，防止阻塞主线程造成界面跳转卡顿。
    * 采用单例模式，防止出现多个实例同时操作相机设备引发异常。
    * 开放扫码框Rect设置接口，根据预览尺寸、图像帧尺寸、预览方向计算出扫码框在图像帧上的实际位置，以指定图像识别区域。
    * 用`TextureReader`代替`ImageReader`，采用openGL绘制图像纹理，主要解决预览掉帧严重的问题，实时输出YUV格式图像。

* 解码
    * 支持指定图像区域识别。
    * 开放条码类型配置接口，可任意指定需要识别的条码类型。
    * 解码回调结果包含条码类型、条码精度，可配置脏数据过滤规则。

## UML类图

[![uml](/uml.png)](#UML类图  "UML类图")

## 集成方式

在module的`build.gradle`中添加如下代码
```
    dependencies {
        implementation 'cn.simonlee.xcodescanner:zbar:1.1.3'
    }
```

## 使用方式

* **STEP.1**<br/>
在Activity的onCreate方法中获取CameraScanner实例，并对CameraScanner和TextureView设置监听
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
在onSurfaceTextureAvailable回调中设置SurfaceTexture及TextureView的宽高，然后开启相机
```java
public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
   mCameraScanner.setSurfaceTexture(surface);
   mCameraScanner.setPreviewSize(width, height);
   mCameraScanner.openCamera(this.getApplicationContext());
}
```
* **STEP.3**<br/>
在openCameraSuccess回调中设置图像帧的宽高及旋转角度，获取ZBarDecoder实例设置给CameraScanner
```java
public void openCameraSuccess(int frameWidth, int frameHeight, int frameDegree) {
   mTextureView.setImageFrameMatrix(frameWidth, frameHeight, frameDegree);
   if (mGraphicDecoder == null) {
      mGraphicDecoder = new ZBarDecoder();//使用带参构造方法可指定条码识别的格式
      mGraphicDecoder.setDecodeListener(this);
   }
   //调用setFrameRect方法会对识别区域进行限制，注意getLeft等获取的是相对于父容器左上角的坐标，实际应传入相对于TextureView左上角的坐标。
   mCameraScanner.setFrameRect(mScannerFrameView.getLeft(), mScannerFrameView.getTop(), mScannerFrameView.getRight(), mScannerFrameView.getBottom());
   mCameraScanner.setGraphicDecoder(mZBarDecoder);
}
```
* **STEP.4**<br/>
在ZBarDecoder的decodeSuccess回调中获取解析结果，开发者可根据回传的条码类型及精度自定义脏数据过滤规则
```java
public void decodeSuccess(int type, int quality, String result) {
   ToastHelper.showToast("[类型" + type + "/精度" + quality + "]" + result, ToastHelper.LENGTH_SHORT);
}
```
* **STEP.5**<br/>
在Activity的onDestroy方法中关闭相机和解码
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
* **注意.1**<br/>
在Activity的onPause方法中关闭相机
```java
public void onPause() {
   mCameraScanner.closeCamera();
   super.onPause();
}
```
* **注意.2**<br/>
在Activity的onRestart方法中开启相机
```java
public void onRestart() {
   //部分机型在后台转前台时会回调onSurfaceTextureAvailable开启相机，因此要做判断防止重复开启
   if (mTextureView.isAvailable()) {
      mCameraScanner.setSurfaceTexture(mTextureView.getSurfaceTexture());
      mCameraScanner.setPreviewSize(mTextureView.getWidth(), mTextureView.getHeight());
      mCameraScanner.openCamera(this.getApplicationContext());
   }
   super.onRestart();
}
```

## 更新计划

*  解决TextureView尺寸变化及padding&margin带来的一些问题。
*  增加环境亮度监测，提示闪光灯开启。
*  增加本地图片识别功能。
*  增加Zxing支持。
*  增加二维码生成功能。

## 版本记录

*  V1.1.3   `2018/04/25`
   1. 修复部分x86设备闪退的问题。
   2. `CameraScanner`新增`stopDecode()`和`startDecode(int delay)`接口，可暂停/延时解码。
   3. ZBar包名由`com.simonlee.xcodescanner`变更为`com.simonlee.xcodescanner`。
   4. 发布开源库：`cn.simonlee.xcodescanner:zbar:1.1.3`，`codescanner`变更为`xcodescanner`，由此带来不便的敬请谅解。
   5. 有开发者反馈部分机型存在闪退、无法解析二维码的问题，将在近期解决。

*  V1.1.2   `2018/04/24`
   1. 修复`ZBarDecoder`中设置解码格式无效的问题。

*  V1.1.1   `2018/04/16`
   1. `ScannerFrameView`增加高占比属性，可设置相对父容器高的占比。
   2. 发布开源库：`cn.simonlee.codescanner:zbar:1.1.1`。

*  V1.1.0   `2018/04/16`
   1. 重写`ZBarDecoder`，解决单线程池可能引起的条码解析延迟问题。
   2. 解决`OldCameraScanner`扫描框区域识别异常的问题。

*  V1.0.9   `2018/04/14`
   1. 解决`NewCameraScanner`扫描框区域识别异常的问题。
   2. 解决连续快速旋转屏幕时`NewCameraScanner`出现异常的问题。

*  V1.0.8   `2018/04/13`
    1. `AutoFixTextureView`更名为`AdjustTextureView`，重写图像校正方式。
    2. `Camera2Scanner`更名为`NewCameraScanner`。
    3. 新增`OldCameraScanner`实现对`Android5.0`以下的支持。
    4. 下调minSdkVersion至14。
    5. 解决前后台切换，横竖屏切换可能产生的异常。
    6. `NewCameraScanner`中取消`ImageReader`的支持。

*  V1.0.7   `2018/04/10`
    1. 调整扫描框宽高计算方式，新增`MaskConstraintLayout`布局。
    2. 优化`Camera2Scanner`，解决后台切换导致的闪退问题。

*  V1.0.6   `2018/04/09`
    1. 调整代码结构，将扫码核心从app移植到zbar中。

*  V1.0.5   `2018/03/29`
    1. 增加帧数据的最大尺寸限制，避免因过高像素导致ZBar解析二维码失败。
    2. 屏蔽ZBar对DataBar(RSS-14)格式条码的支持，此格式实用性不高，且易产生误判。

*  V1.0.4   `2018/03/27`
    1. 修改`ZBarDecoder`，修复多线程可能的空指针异常。
    2. 修改`GraphicDecoder`，EGL14替换EGL10，解决部分机型不兼容的问题；解决多线程可能的空指针异常。

*  V1.0.3   `2018/03/23`
    1. 新增`TextureReader`，通过双缓冲纹理获取帧数据进行回调，代替ImageReader的使用。
    2. 修改`GraphicDecoder`，handler放到子类中去操作。

*  V1.0.2   `2018/03/14`
    1. 新增抽象类`GraphicDecoder`，将条码解析模块进行抽离。
    2. 新增`ZBarDecoder`，采用ZBar解析条码，并增加解析类型及解析精度设置。
    3. 修改`ScannerFrameView`，扫描线动画由属性动画实现。
    4. 修改`Camera2Scanner`，修复释放相机可能导致的异常，增加扫描框区域设置。
    5. 其他微调。

*  V1.0.1   `2018/02/09`
    1. 新增`ScannerFrameLayout`，为`RelativeLayout`的子类，可对扫描框的位置和大小进行设置。
    2. 修改`ScannerFrameView`，可对扫描框内部进行定制。

*  V1.0.0   `2018/02/03`
    初次提交代码。

## 关于作者

这是我个人的第一个开源项目，慢慢悠悠也投入了不少精力。在开源的过程中碰到了许多疑点难点，其中借鉴了很多大神的成果。因为自己的疏忽没有预先做好参考记录，在这里向那些为开源默默奉献的大神们致敬！谢谢你们！

如果在使用过程中遇到了闪退、黑屏、无法识别、无法对焦、预览掉帧、内存泄漏等任何异常问题，欢迎提Issues！同时请尽量附上设备型号、android版本号、BUG复现步骤、异常日志、无法识别的图像等，我会尽快安排解决。

如果您觉得有用，请动动小手给我一个**Star**来点鼓励吧:blush:

|Author|E-mail|WeChat|
|---|---|---|
|Simon Lee|jmlixiaomeng@163.com|[![wechat](/wechat.png)](#关于作者 "私人微信哦~")|
