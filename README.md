# :star2:&nbsp;CodeScanner

一个Android平台上用来解析条码及二维码的框架。目前采用zbar解析图像数据，兼容` API14 `及以上版本。

|**Author**|**Simon Lee**|
|---|---|
|**E-mail**|**jmlixiaomeng@163.com**|

****
## 目录
* [功能特色](#功能特色)
* [示例程序](#示例程序)
* [Gradle依赖](#gradle依赖)
* [更新计划](#更新计划)
* [接口说明](#接口说明)
* [版本记录](#版本记录)

## 功能特色

1. 支持新旧两版CameraAPI

2. zbar解码，更快更精准

3. 扫码框随心定制，动画不卡顿

4. layout任意尺寸，预览不变形

5. camera异步开启，不占主线程

6. 可配置扫码框内识别，精准无误差

7. 前后台、横竖屏任意切，绝对不闪退

8. TextureReader取代ImageReader，预览不丢帧

9. ZBarDecoder支持图像格式及精度控制，过滤脏数据

10. 自动对焦很简单，指哪扫哪

## 示例程序

|Demo下载|示例效果|
|:---:|:---:|
|[点此下载](http://fir.im/CodeScanner) 或扫描下面二维码<br/>[![demo](/download.png)](http://fir.im/CodeScanner  "扫码下载示例程序")|[![gif](/demo.gif)](http://fir.im/CodeScanner  "示例效果")|

## Gradle依赖

在module的`build.gradle`中添加如下代码

    dependencies {
        implementation 'cn.simonlee.codescanner:zbar:1.1.1'
    }

## 更新计划
-  解决TextureView尺寸变化及padding&margin带来的一些问题。
-  增加环境亮度监测，提示闪光灯开启。
-  增加本地图片识别功能。
-  增加Zxing支持。
-  增加二维码生成功能。

## 接口说明
#### &nbsp;&nbsp;&nbsp;&nbsp;:cat:&nbsp;&nbsp;**AdjustTextureView**
    继承自TextureView，用于渲染camera预览图像，可根据图像参数进行适配以解决形变问题
|接口|功能说明|参数及返回值|备注|
|:---:|:---:|:---:|:---:|
|**setImageFrameMatrix(int frameWidth, int frameHeight, int frameDegree)**|根据图像帧宽高及角度进行显示校正|**frameWidth:** 图像帧的宽<br/>**frameHeight:** 图像帧的高<br/>**frameDegree:** 图像旋转角度|　　|
|**setImageFrameMatrix()**|根据图像帧宽高及角度进行显示校正|||

#### &nbsp;&nbsp;&nbsp;&nbsp;:dog:&nbsp;&nbsp;**ScannerFrameView**
    继承自View，用于绘制扫描框
|接口|功能说明|参数及返回值|备注|
|:---:|:---:|:---:|:---:|
|**setFrameWidthRatio(float frameWidthRatio)**|设置view宽占比（相对父容器的宽）|**frameWidthRatio:** 宽占比|仅宽为**wrap_content**时有效，xml中可通过**frame_widthRatio**属性配置|
|**setFrameHeightRatio(float frameHeightRatio)**|设置view高占比（相对父容器的高）|**frameHeightRatio:** 高占比|仅高为**wrap_content**时有效，xml中可通过**frame_heightRatio**属性配置|
|**setFrameWHRatio(float frameWHRatio)**|设置view宽高比|**frameWHRatio:** 宽高比|仅当宽或高为**wrap_content**且未设置父占比时有效，xml中可通过**frame_whRatio**属性配置|
|**setFrameLineVisible(boolean frameLineVisible)**|设置是否显示边框|**frameLineVisible:** true显示/false隐藏|xml中可通过**frameLine_visible**属性配置|
|**setFrameLineWidth(int frameLineWidth)**|设置边框宽度|**frameLineWidth:** 边框宽|xml中可通过**frameLine_width**属性配置|
|**setFrameLineColor(int frameLineColor)**|设置边框颜色|**frameLineColor:** 十六进制色值|xml中可通过**frameLine_color**属性配置|

    未完待续，近日完善。。
    
#### &nbsp;&nbsp;&nbsp;&nbsp;:dog:&nbsp;&nbsp;**MaskRelativeLayout**
    继承自RelativeLayout，用于绘制扫描框外部阴影
|接口|功能说明|参数及返回值|备注|
|:---:|:---:|:---:|:---:|
|**setFrameOutsideColor(int frameOutsideColor)**|设置扫描框外部填充色|**frameOutsideColor:** 十六进制色值|xml中可通过**frame_outsideColor**属性配置|

    未完待续，近日完善。。

## 版本记录

-  V1.1.1   `2018/04/16`

    1. `ScannerFrameView`增加高占比属性，可设置相对父容器高的占比。
    2. 修改包名为`com.simonlee.demo.camerascanner`。

-  V1.1.0   `2018/04/16`

    1. 重写`ZBarDecoder`，解决单线程池可能引起的条码解析延迟问题。
    2. 解决`OldCameraScanner`扫描框区域识别异常的问题。

-  V1.0.9   `2018/04/14`

    1. 解决`NewCameraScanner`扫描框区域识别异常的问题。
    2. 解决连续快速旋转屏幕时`NewCameraScanner`出现异常的问题。

-  V1.0.8   `2018/04/13`

    1. `AutoFixTextureView`更名为`AdjustTextureView`，重写图像校正方式。
    2. `Camera2Scanner`更名为`NewCameraScanner`。
    3. 新增`OldCameraScanner`实现对`Android5.0`以下的支持。
    4. 下调minSdkVersion至14。
    5. 解决前后台切换，横竖屏切换可能产生的异常。
    6. `NewCameraScanner`中取消ImageReader的支持。

-  V1.0.7   `2018/04/10`

    1. 调整扫描框宽高计算方式，新增`MaskConstraintLayout`布局。
    2. 优化`Camera2Scanner`，解决后台切换导致的闪退问题。

-  V1.0.6   `2018/04/09`

    1. 调整代码结构，将扫码核心从app移植到zbar中。

-  V1.0.5   `2018/03/29`

    1. 增加帧数据的最大尺寸限制，避免因过高像素导致Zbar解析二维码失败。
    2. 屏蔽ZBar对DataBar(RSS-14)格式条码的支持，此格式实用性不高，且易产生误判。

-  V1.0.4   `2018/03/27`

    1. 修改`ZBarDecoder`，修复多线程可能的空指针异常。
    2. 修改`GraphicDecoder`，EGL14替换EGL10，解决部分机型不兼容的问题；解决多线程可能的空指针异常。

-  V1.0.3   `2018/03/23`

    1. 新增`TextureReader`，通过双缓冲纹理获取帧数据进行回调，代替ImageReader的使用。
    2. 修改`GraphicDecoder`，handler放到子类中去操作。

-  V1.0.2   `2018/03/14`

    1. 新增抽象类`GraphicDecoder`，将条码解析模块进行抽离。
    2. 新增`ZBarDecoder`，采用ZBar解析条码，并增加解析类型及解析精度设置。
    3. 修改`ScannerFrameView`，扫描线动画由属性动画实现。
    4. 修改`Camera2Scanner`，修复释放相机可能导致的异常，增加扫描框区域设置。
    5. 其他微调。

-  V1.0.1   `2018/02/09`

    1. 新增`ScannerFrameLayout`，为`RelativeLayout`的子类，可对扫描框的位置和大小进行设置。
    2. 修改`ScannerFrameView`，可对扫描框内部进行定制。
    
-  V1.0.0   `2018/02/03`

    初次提交代码。
