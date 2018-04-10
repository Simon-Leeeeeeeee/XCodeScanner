:star2: Camera2Scanner
============================

## 目录
* [功能介绍](#功能介绍)
* [更新计划](#更新计划)
* [版本记录](#版本记录)

## 功能介绍
Android平台基于[zBar](https://github.com/ZBar/ZBar)的开源扫码框架，要求<font color=#0366d6>SDK21</font>及以上。 

-  使用camera2API
-  自动对焦
-  扫码结果准确率高，可控制精度
-  扫描界面可任意定制

## 更新计划
-  针对<font color=#0366d6>SDK21</font>及以上，优化camera2API的使用。
-  针对<font color=#0366d6>SDK21</font>以下，提供旧的cameraAPI的支持。
-  增加Zxing支持。

## 版本记录

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
