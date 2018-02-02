package com.demo.camera2;

import android.graphics.ImageFormat;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Size;
import android.view.Surface;

public class Progress implements Allocation.OnBufferAvailableListener {
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
    private Allocation mBufferInAllocation;//此Allocation的Surface连接到Camera，接收YUV格式Buffer数据
    private Allocation mBufferOutAllocation;//此Allocation的Surface连接到View，从mBufferInAllocation获取YUV格式数据并转化为RGBA格式，返回给View

    public Progress(RenderScript rs, Size dimensions) {
        Type.Builder yuvTypeBuilder = new Type.Builder(rs, Element.YUV(rs));
        yuvTypeBuilder.setX(dimensions.getWidth());
        yuvTypeBuilder.setY(dimensions.getHeight());
        yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);

        Type.Builder rgbTypeBuilder = new Type.Builder(rs, Element.RGBA_8888(rs));
        rgbTypeBuilder.setX(dimensions.getWidth());
        rgbTypeBuilder.setY(dimensions.getHeight());

        mBufferInAllocation = Allocation.createTyped(rs, yuvTypeBuilder.create(),
                Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

        mBufferOutAllocation = Allocation.createTyped(rs, rgbTypeBuilder.create(),
                Allocation.USAGE_IO_OUTPUT | Allocation.USAGE_SCRIPT);

        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.YUV(rs));
        yuvToRgbIntrinsic.setInput(mBufferInAllocation);

        mBufferInAllocation.setOnBufferAvailableListener(this);
    }

    @Override
    public void onBufferAvailable(Allocation allocation) {
        mBufferInAllocation.ioReceive();
//      mBufferOutAllocation.copyFrom(mBufferInAllocation);
        yuvToRgbIntrinsic.forEach(mBufferOutAllocation);//将InAllocation的YUV数据转化为RGBA格式写入OutAllocation
        mBufferOutAllocation.ioSend();
//      byte[] hehe = new byte[mPreviewSize.getWidth() * mPreviewSize.getHeight() * 2];
//      mBufferInAllocation.copyTo(hehe);//取出YUV格式数据
    }

    public Surface getBufferInSurface() {
        return mBufferInAllocation.getSurface();//返回的是这个Input
    }

    public void setBufferOutSurface(Surface output) {
        mBufferOutAllocation.setSurface(output);//绑定到这个Out了
    }

}
