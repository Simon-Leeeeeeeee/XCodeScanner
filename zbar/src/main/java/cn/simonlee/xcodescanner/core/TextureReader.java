package cn.simonlee.xcodescanner.core;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * @author Simon Lee
 * @e-mail jmlixiaomeng@163.com
 * @github https://github.com/Simon-Leeeeeeeee/XCodeScanner
 * @createdTime 2018-03-23
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class TextureReader implements SurfaceTexture.OnFrameAvailableListener {

    // 着色器中的自定义变量
    private static final String[] mShaderAttributes = new String[]{
            "uWidth", "uHeight", "uTexture", "aVertex", "aTexture"};

    // 顶点着色器的脚本
    private static final String mVerticeShaderScript
            = "attribute vec4 aVertex;                         \n"
            + "attribute vec4 aTexture;                        \n"
            + "varying vec2 vTexture;                          \n"
            + "void main(){                                    \n"
            + "   gl_Position = aVertex;                       \n"
            + "    vTexture = aTexture.xy;                     \n"
            + "}";

    // 片元着色器的脚本
    private static final String mFragmentShaderScript
            = "#extension GL_OES_EGL_image_external : require  \n"
            + "precision mediump float;                        \n" // 声明float类型的精度为中等(精度越高越耗资源) lowp mediump highp
            + "uniform samplerExternalOES uTexture;            \n"
            + "varying vec2 vTexture;                          \n"
            + "uniform float uWidth;                           \n"
            + "uniform float uHeight;                          \n"
            + "float cY(float x,float y){                      \n"
            + "    vec4 c=texture2D(uTexture,vec2(x,y));       \n"//默认
//          + "    vec4 c=texture2D(uTexture,vec2(y,(1.-x)));  \n"//顺90
//          + "    vec4 c=texture2D(uTexture,vec2((1.-x),y));  \n"//顺180
//          + "    vec4 c=texture2D(uTexture,vec2((1.-y),x));  \n"//顺270
            + "    return c.r*0.257+c.g*0.504+c.b*0.098;       \n"//+0.0625
            + "}                                               \n"
            + "void main(){                                    \n"
            + "    float x =floor(uWidth*vTexture.x)*4.;       \n"
            + "    float y =floor(uHeight*vTexture.y)*4.;      \n"
            + "    float posx =mod(x,uWidth);                  \n"
            + "    float posy =y+floor(x/uWidth);              \n"
            + "    vec4 oColor=vec4(0);                        \n"
            + "    float textureYPos=posy/uHeight;             \n"
            + "    oColor[0]=cY(posx/uWidth,textureYPos);      \n"
            + "    oColor[1]=cY((posx+1.)/uWidth,textureYPos); \n"
            + "    oColor[2]=cY((posx+2.)/uWidth,textureYPos); \n"
            + "    oColor[3]=cY((posx+3.)/uWidth,textureYPos); \n"
            + "    gl_FragColor = oColor;                      \n"
            + "}";

    private int mWidth;
    private int mHeight;

    private int mGLWidth;
    private int mGLHeight;
    private int mGLTexture;
    private int mGLVertexIndex;
    private int mGLTextureIndex;
    private int mGLShaderProgram;//着色器脚本程序

    private EGLContext mEGLContext;
    private EGLDisplay mEGLDisplay;
    private EGLSurface mEGLSurface;

    private byte[] mOutPutBytes;
    private ByteBuffer mOutPutBuffer;
    private FloatBuffer mVertexBuffer;
    private FloatBuffer mTextureBuffer;

    private int[] mOESTexture = new int[1];//外部纹理
    private int[] mOutputFrame = new int[1];//双缓冲帧
    private int[] mOutputTexture = new int[1];//双缓冲纹理

    private SurfaceTexture mOESSurfaceTexture;

    private OnImageAvailableListener mImageAvailableListener;

    public interface OnImageAvailableListener {
        void onFrameAvailable(byte[] frameData, int width, int height);
    }

    public TextureReader(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
        this.mOutPutBuffer = ByteBuffer.allocate(width * height * 3 / 2);
        setupOESTexture();//设置外部纹理
        setupTextureBuffer();//设置纹理Buffer
        initEGL();//初始化EGL
        creatShaderProgram();//创建着色器程序
        setupShaderAttributeID();//获取着色器中自定义变量的索引
        setupOutputFrame();//设置双缓冲帧
    }

    //设置外部纹理
    private void setupOESTexture() {
        // 激活纹理单元0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        //生成纹理
        GLES20.glGenTextures(1, mOESTexture, 0);

        // 绑定纹理
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mOESTexture[0]);
        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            throw new RuntimeException("TextureReader Error! Failed to generate OESTexture.");
        }
    }

    //设置纹理Buffer
    private void setupTextureBuffer() {
        //不同平台字节顺序不同，要通过ByteOrder设置nativeOrder()避免异常
        ByteOrder byteOrder = ByteOrder.nativeOrder();

        float vertexCoordArray[] = {
                -1.0f, -1.0f,//左下
                -1.0f, 1.0f,//左上
                1.0f, -1.0f,//右下
                1.0f, 1.0f//右上
        };
        ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(vertexCoordArray.length * 4);//一个float占4个字节
        this.mVertexBuffer = vertexBuffer.order(byteOrder).asFloatBuffer().put(vertexCoordArray);
        this.mVertexBuffer.position(0);

        final float textureCoordArray[] = {
                0.0f, 0.0f,//左下
                0.0f, 1.0f,//左上
                1.0f, 0.0f,//右下
                1.0f, 1.0f//右上
        };
        ByteBuffer textureBuffer = ByteBuffer.allocateDirect(textureCoordArray.length * 4);//一个float占4个字节
        this.mTextureBuffer = textureBuffer.order(byteOrder).asFloatBuffer().put(textureCoordArray);
        this.mTextureBuffer.position(0);
    }

    private void initEGL() {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int version[] = new int[2];
        EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1);

        EGLConfig[] configArray = new EGLConfig[1];
        int[] configAttributes = new int[]{
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,       //渲染类型
                EGL14.EGL_BUFFER_SIZE, 32,                           //color buffer 的颜色深度 RGBA之和
                EGL14.EGL_RED_SIZE, 8,                               //指定RGB中的R大小（bits）
                EGL14.EGL_GREEN_SIZE, 8,                             //指定G大小
                EGL14.EGL_BLUE_SIZE, 8,                              //指定B大小
                EGL14.EGL_ALPHA_SIZE, 8,                             //指定Alpha大小，以上四项实际上指定了像素格式
                EGL14.EGL_DEPTH_SIZE, 0,                             //指定深度缓存大小
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, //指定渲染api类别
                EGL14.EGL_STENCIL_SIZE, 1,
                EGL14.EGL_NONE                                       //总是以EGL14.EGL_NONE结尾
        };
        EGL14.eglChooseConfig(mEGLDisplay, configAttributes, 0, configArray, 0, 1, new int[1], 0);

        //将Surface转换为本地窗口
        final int[] attributes = {
                EGL14.EGL_WIDTH, mWidth,
                EGL14.EGL_HEIGHT, mHeight,
                EGL14.EGL_NONE};
        mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, configArray[0], attributes, 0);
        if (mEGLSurface == null || mEGLSurface == EGL14.EGL_NO_SURFACE) {
            throw new RuntimeException("TextureReader Error! Failed to create EGLSurface: " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }

        int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configArray[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0);

        //将EGLDisplay、EGLSurface和EGLContext进行绑定
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("TextureReader Error! EGL MakeCurrent failure: " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
    }

    //创建着色器程序
    private void creatShaderProgram() {
        // 编译顶点着色器脚本
        final int vertexShaderHandle = compileShader(GLES20.GL_VERTEX_SHADER, mVerticeShaderScript);
        // 编译片元着色器脚本
        final int fragmentShaderHandle = compileShader(GLES20.GL_FRAGMENT_SHADER, mFragmentShaderScript);

        // 创建程序
        mGLShaderProgram = GLES20.glCreateProgram();
        // 若程序创建成功则向程序中加入顶点着色器与片元着色器
        if (mGLShaderProgram != 0) {
            // 向程序中加入顶点着色器
            GLES20.glAttachShader(mGLShaderProgram, vertexShaderHandle);
            // 向程序中加入片元着色器
            GLES20.glAttachShader(mGLShaderProgram, fragmentShaderHandle);
            // 绑定着色器中的自定义变量
            final int size = mShaderAttributes.length;
            for (int i = 0; i < size; i++) {
                GLES20.glBindAttribLocation(mGLShaderProgram, i, mShaderAttributes[i]);
            }
            // 链接程序
            GLES20.glLinkProgram(mGLShaderProgram);
            // 存放链接成功program数量的数组
            int[] linkStatus = new int[1];
            // 获取program的链接情况
            GLES20.glGetProgramiv(mGLShaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
            // 若链接失败则报错并删除程序
            if (linkStatus[0] != GLES20.GL_TRUE) {//若编译失败则删除此program
                GLES20.glDeleteProgram(mGLShaderProgram);
                mGLShaderProgram = 0;
            }
        }
        if (mGLShaderProgram == 0) {
            throw new RuntimeException("TextureReader Error! Failed to create ShaderProgram.");
        }
    }

    // 编译着色器脚本
    private int compileShader(int shaderType, String sourceCode) {
        // 创建一个新shader
        int shaderHandle = GLES20.glCreateShader(shaderType);
        // 若创建成功则加载shader
        if (shaderHandle != 0) {
            // 加载shader的源代码
            GLES20.glShaderSource(shaderHandle, sourceCode);
            // 编译shader
            GLES20.glCompileShader(shaderHandle);
            // 存放编译成功shader数量的数组
            int[] compileStatus = new int[1];
            // 获取Shader的编译情况
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
            if (compileStatus[0] == 0) {//若编译失败则删除此shader
                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }
        if (shaderHandle == 0) {
            throw new RuntimeException("TextureReader Error! Failed to compile Shader.");
        }
        return shaderHandle;
    }

    //获取着色器中自定义变量的索引
    private void setupShaderAttributeID() {
        mGLWidth = GLES20.glGetUniformLocation(mGLShaderProgram, mShaderAttributes[0]);        // "uWidth"
        mGLHeight = GLES20.glGetUniformLocation(mGLShaderProgram, mShaderAttributes[1]);       // "uHeight"
        mGLTexture = GLES20.glGetUniformLocation(mGLShaderProgram, mShaderAttributes[2]);      // "uTexture"
        mGLVertexIndex = GLES20.glGetAttribLocation(mGLShaderProgram, mShaderAttributes[3]);   // "aVertex"
        mGLTextureIndex = GLES20.glGetAttribLocation(mGLShaderProgram, mShaderAttributes[4]);  // "aTexture"
    }

    //设置双缓冲帧
    private void setupOutputFrame() {
        //生成帧缓冲对象，用于离屏渲染缓冲
        GLES20.glGenFramebuffers(1, mOutputFrame, 0);
        //生成纹理
        GLES20.glGenTextures(1, mOutputTexture, 0);
        // 绑定纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOutputTexture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mWidth, mHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        //设置缩小过滤为使用纹理中坐标最接近的一个像素的颜色作为需要绘制的像素颜色
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        //设置放大过滤为使用纹理中坐标最接近的若干个颜色，通过加权平均算法得到需要绘制的像素颜色
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        //设置环绕方向S，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        //设置环绕方向T，截取纹理坐标到[1/2n,1-1/2n]。将导致永远不会与border融合
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            throw new RuntimeException("TextureReader Error! Failed to generate OutputTexture.");
        }
    }

    public void setOnImageAvailableListener(OnImageAvailableListener listener) {
        this.mImageAvailableListener = listener;
    }

    public SurfaceTexture getSurfaceTexture() {
        if (mOESSurfaceTexture == null) {
            mOESSurfaceTexture = new SurfaceTexture(mOESTexture[0]);
            mOESSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
            mOESSurfaceTexture.setOnFrameAvailableListener(this);
        }
        return mOESSurfaceTexture;
    }

    public synchronized void close() {
        if (mOESSurfaceTexture != null) {
            mOESSurfaceTexture.setOnFrameAvailableListener(null);
            mOESSurfaceTexture.release();
            mOESSurfaceTexture = null;
        }
        releaseGL();
        releaseEGL();
    }

    private void releaseGL() {
        GLES20.glDeleteProgram(mGLShaderProgram);
        GLES20.glDeleteTextures(1, mOESTexture, 0);
        GLES20.glDeleteTextures(1, mOutputTexture, 0);
        GLES20.glDeleteFramebuffers(1, mOutputFrame, 0);
    }

    private void releaseEGL() {
        EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        if (mEGLSurface != null) {
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
        }
        if (mEGLContext != null) {
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
        }
        EGL14.eglTerminate(mEGLDisplay);
    }

    @Override
    public synchronized void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mOESSurfaceTexture != null) {
            drawTexture();
        }
    }

    private void drawTexture() {
        mOESSurfaceTexture.updateTexImage();
        GLES20.glViewport(0, 0, mWidth, mHeight);

        //GLBindFrameBuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mOutputFrame[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mOutputTexture[0], 0);

        //GLClear
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        //启用着色器程序
        GLES20.glUseProgram(mGLShaderProgram);

        //GLBindTexture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOutputTexture[0]);
        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            throw new RuntimeException("TextureReader Error! Failed to bind OutputTexture.");
        }

        //GLSetExpandData
        GLES20.glUniform1i(mGLTexture, 0);
        GLES20.glUniform1f(mGLWidth, mWidth);
        GLES20.glUniform1f(mGLHeight, mHeight);

        //GLDraw
        GLES20.glEnableVertexAttribArray(mGLVertexIndex);
        GLES20.glEnableVertexAttribArray(mGLTextureIndex);
        GLES20.glVertexAttribPointer(mGLVertexIndex, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer);
        GLES20.glVertexAttribPointer(mGLTextureIndex, 2, GLES20.GL_FLOAT, false, 0, mTextureBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLVertexIndex);
        GLES20.glDisableVertexAttribArray(mGLTextureIndex);
        if (mImageAvailableListener != null) {
            GLES20.glReadPixels(0, 0, mWidth, mHeight * 3 / 8, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mOutPutBuffer);
//            mOutPutBytes = mOutPutBuffer.array();
            if (mOutPutBytes == null) {
                mOutPutBytes = new byte[mWidth * mHeight];
            }
            mOutPutBuffer.position(0);
            mOutPutBuffer.get(mOutPutBytes, 0, mOutPutBytes.length);
            mOutPutBuffer.clear();
            mImageAvailableListener.onFrameAvailable(mOutPutBytes, mWidth, mHeight);
        }

        //GLUnbindFrameBuffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);

        mOESSurfaceTexture.releaseTexImage();
    }

}
