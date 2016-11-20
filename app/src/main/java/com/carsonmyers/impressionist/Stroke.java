package com.carsonmyers.impressionist;

import android.graphics.Paint;
import android.graphics.Path;

/**
 * Created by carson on 11/5/2016.
 */

public class Stroke {
    private Path mPath;
    public float x;
    public float y;
    private Paint mPaint;

    public Stroke(float x, float y, Paint mPaint) {
        this.x = x;
        this.y = y;
        this.mPaint = mPaint;
    }

    public Path getPath() {
        return mPath;
    }

    public void setPath(Path mPath) {
        this.mPath = mPath;
    }

    public Paint getPaint() {
        return mPaint;
    }

    public void setPaint(Paint mPaint) {
        this.mPaint = mPaint;
    }

    public String toString(){
        return "Stroke alpha: " + mPaint.getAlpha() + " width: " + mPaint.getStrokeWidth();
    }
}
