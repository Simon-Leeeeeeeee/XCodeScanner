/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.demo.camera2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

/**
 * A {@link TextureView} that can be adjusted to a specified aspect ratio.
 */
public class AutoFitTextureView extends TextureView {

    private float mRatio_WH;

    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(int surfaceWidth, int surfaceHeight, int orientation) {
        if (surfaceWidth < 0 || surfaceHeight < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
//        int orientation = Surface.ROTATION_0;
//        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
//        if (windowManager != null) {
//            orientation = windowManager.getDefaultDisplay().getRotation();
//        }
        setRotation(-90 * orientation);
        if (orientation == Surface.ROTATION_90 || orientation == Surface.ROTATION_270) {
            mRatio_WH = 1F * surfaceWidth / surfaceHeight;
            setScaleX(1F / mRatio_WH);
            setScaleY(mRatio_WH);
        } else {
            mRatio_WH = 1F * surfaceHeight / surfaceWidth;
        }
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 >= mRatio_WH) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatio_WH) {
                setMeasuredDimension((int) (height * mRatio_WH), height);
            } else {
                setMeasuredDimension(width, (int) (width / mRatio_WH));
            }
        }
    }

}
