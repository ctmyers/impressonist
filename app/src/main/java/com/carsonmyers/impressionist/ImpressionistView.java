package com.carsonmyers.impressionist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ImageView;
import android.widget.Toast;

import com.carsonmyers.impressionist.brushes.Brush;
import com.carsonmyers.impressionist.brushes.CircleBrush;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Vector;

import static android.view.MotionEvent.INVALID_POINTER_ID;
import static java.lang.Math.abs;
import static java.lang.Math.sin;

/**
 * Created by jon on 3/20/2016.
 */
public class ImpressionistView extends View {

    private ImageView mImageView;
    private Bitmap mImage;

    private Canvas mOffScreenCanvas = null;
    private Bitmap mOffScreenBitmap = null;

    private Paint mPaint = new Paint();
    private int mBrushRadius = 20;

    private VelocityTracker mVelocityTracker = null;

    private int mAlpha = 150;
    private Paint mPaintBorder = new Paint();
    private BrushType mBrushType = BrushType.Square;


    public ImpressionistView(Context context) {
        super(context);
        init(null, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ImpressionistView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    /**
     * Because we have more than one constructor (i.e., overloaded constructors), we use
     * a separate initialization method
     * @param attrs
     * @param defStyle
     */
    private void init(AttributeSet attrs, int defStyle){

        // Set setDrawingCacheEnabled to true to support generating a bitmap copy of the view (for saving)
        // See: http://developer.android.com/reference/android/view/View.html#setDrawingCacheEnabled(boolean)
        //      http://developer.android.com/reference/android/view/View.html#getDrawingCache()
        this.setDrawingCacheEnabled(true);

        mPaint.setColor(Color.RED);
        mPaint.setAlpha(mAlpha);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setStrokeWidth(4);

        mPaintBorder.setColor(Color.BLACK);
        mPaintBorder.setStrokeWidth(3);
        mPaintBorder.setStyle(Paint.Style.STROKE);
        mPaintBorder.setAlpha(50);

        //mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){

        Bitmap bitmap = getDrawingCache();
        Log.v("onSizeChanged", MessageFormat.format("bitmap={0}, w={1}, h={2}, oldw={3}, oldh={4}", bitmap, w, h, oldw, oldh));
        if(bitmap != null) {
            mOffScreenBitmap = getDrawingCache().copy(Bitmap.Config.ARGB_8888, true);
            mOffScreenCanvas = new Canvas(mOffScreenBitmap);
        }
    }

    /**
     * Sets the ImageView, which hosts the image that we will paint in this view
     * @param imageView
     */
    public void setImageView(ImageView imageView){
        mImageView = imageView;

        if(imageView.getDrawable() != null)
            mImage = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
    }

    /**
     * Sets the brush type. Feel free to make your own and completely change my BrushType enum
     * @param brushType
     */
    public void setBrushType(BrushType brushType){
        mBrushType = brushType;
    }

    /**
     * Clears the painting
     */
    public void clearPainting(){

        if(mOffScreenCanvas != null) {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            mOffScreenCanvas.drawRect(0, 0, this.getWidth(), this.getHeight(), paint);
        }
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(mOffScreenBitmap != null) {
            canvas.drawBitmap(mOffScreenBitmap, 0, 0, null);
        }

        // Draw the border. Helpful to see the size of the bitmap in the ImageView
        canvas.drawRect(getBitmapPositionInsideImageView(mImageView), mPaintBorder);
    }

    //https://developer.android.com/training/gestures/movement.html
    //https://developer.android.com/training/gestures/multi.html
    //https://developer.android.com/training/gestures/scale.html
    //http://stackoverflow.com/questions/11966692/android-smooth-multi-touch-drawing
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){

        //Basically, the way this works is to listen for Touch Down and Touch Move events and determine where those
        //touch locations correspond to the bitmap in the ImageView. You can then grab info about the bitmap--like the pixel color--
        //at that location

        super.onTouchEvent(motionEvent);

        final int action = MotionEventCompat.getActionMasked(motionEvent);

        switch (action) {
            case MotionEvent.ACTION_DOWN:{

                //go through list of all pointers
                for (int size = motionEvent.getPointerCount(), pointerIndex = 0; pointerIndex < size; pointerIndex++) {

                    //check the user is touching inside the canvas
                    float curTouchX = MotionEventCompat.getX(motionEvent, pointerIndex);
                    float curTouchY = MotionEventCompat.getY(motionEvent, pointerIndex);
                    Rect rect = new Rect(getBitmapPositionInsideImageView(mImageView));
                    if (!rect.contains((int) curTouchX, (int) curTouchY))
                        return true;

                    //update the velocity tracker
                    if (mVelocityTracker == null) {
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        mVelocityTracker.clear();
                    }
                    mVelocityTracker.addMovement(motionEvent);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mImage != null) { //make sure mImage is set before doing anything

                    //go through the pointers
                    for (int size = motionEvent.getPointerCount(), pointerIndex = 0; pointerIndex < size; pointerIndex++) {

                        //get the current touch position and make sure its in the canvas
                        float curTouchX = MotionEventCompat.getX(motionEvent, pointerIndex);
                        float curTouchY = MotionEventCompat.getY(motionEvent, pointerIndex);
                        Rect rect = new Rect(getBitmapPositionInsideImageView(mImageView));
                        if (!rect.contains((int) curTouchX, (int) curTouchY))
                            return true;

                        //update the velocity tracker
                        mVelocityTracker.addMovement(motionEvent);
                        mVelocityTracker.computeCurrentVelocity(1000);
                        float vx = VelocityTrackerCompat.getXVelocity(mVelocityTracker, pointerIndex);
                        float vy = VelocityTrackerCompat.getYVelocity(mVelocityTracker, pointerIndex);


                        //paints the historical points
                        int historySize = motionEvent.getHistorySize();
                        for (int i = 0; i < historySize; i++) {
                            float touchX = motionEvent.getHistoricalX(pointerIndex, i);
                            float touchY = motionEvent.getHistoricalY(pointerIndex, i);
                            paintOnCanvas(touchX, touchY, vx, vy);
                        }
                        //paints the current points
                        paintOnCanvas(curTouchX, curTouchY, vx, vy);
                    }
                }
                break;
            }
        }

        invalidate();
        return true;


    }

    // returns the points of the bitmap
    private static Point getBitmapPoints(float x, float y, ImpressionistView impView, Bitmap bit){
        int newX;
        int newY;

        int imgViewW = bit.getWidth();
        int imgViewH = bit.getHeight();

        int impViewH = impView.getHeight();
        int impViewW = impView.getWidth();

        newX = (int)((x / impViewW)  * imgViewW);
        newY = (int)((y / impViewH) * imgViewH);

        return new Point(newX, newY);
    }


    /**
     * This method is useful to determine the bitmap position within the Image View. It's not needed for anything else
     * Modified from:
     *  - http://stackoverflow.com/a/15538856
     *  - http://stackoverflow.com/a/26930938
     * @param imageView
     * @return Rect
     */
    private static Rect getBitmapPositionInsideImageView(ImageView imageView){
        Rect rect = new Rect();

        if (imageView == null || imageView.getDrawable() == null) {
            return rect;
        }

        // Get image dimensions
        // Get image matrix values and place them in an array
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);

        // Extract the scale values using the constants (if aspect ratio maintained, scaleX == scaleY)
        final float scaleX = f[Matrix.MSCALE_X];
        final float scaleY = f[Matrix.MSCALE_Y];

        // Get the drawable (could also get the bitmap behind the drawable and getWidth/getHeight)
        final Drawable d = imageView.getDrawable();
        final int origW = d.getIntrinsicWidth();
        final int origH = d.getIntrinsicHeight();

        // Calculate the actual dimensions
        final int widthActual = Math.round(origW * scaleX);
        final int heightActual = Math.round(origH * scaleY);

        // Get image position
        // We assume that the image is centered into ImageView
        int imgViewW = imageView.getWidth();
        int imgViewH = imageView.getHeight();

        int top = (imgViewH - heightActual) /2;
        int left = (imgViewW - widthActual) /2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }

    public Bitmap getBitmap(){
        return mOffScreenBitmap;
    }


    // Paints the coordinate on the mOffscreenbitmap
    private boolean paintOnCanvas(float x, float y, float vx, float vy){

        int color;
        int r;
        int g;
        int b;

        //makes sure we are in bounds
        if(y > this.getHeight() || y <= 0 || x > this.getWidth() || x <= 0)
            return true;
        Rect rect = new Rect(getBitmapPositionInsideImageView(mImageView));
        if(!rect.contains((int)x, (int)y))
            return true;

        //get the color from the correct location
        Point p = getBitmapPoints(x,y,this, mImage);
        color = mImage.getPixel(p.x, p.y);
        r = Color.red(color);
        g = Color.green(color);
        b = Color.blue(color);
        mPaint.setARGB(150, r,g,b);

        // paints the coordinate depending on the selected brushtype
        float rad;

        switch(mBrushType){
            case Square:
                mOffScreenCanvas.drawRect(x - mBrushRadius, y - mBrushRadius, x + mBrushRadius, y + mBrushRadius, mPaint);
                break;
            case Arc:
                int arcRadius = mBrushRadius + 20;
                RectF rectF = new RectF(x - arcRadius, y - arcRadius, x + arcRadius, y + arcRadius);
                mOffScreenCanvas.drawArc(rectF, 45f, 45f, true, mPaint);
                break;
            case Sine:
                rad = (float)((mBrushRadius-10)*(sin(x) + sin(y))+10);
                mOffScreenCanvas.drawCircle(x,y,rad,mPaint);
                break;
            case Velocity:
                rad = (abs(vx)+abs(vy))/500 + 10;
                mOffScreenCanvas.drawCircle(x,y,rad,mPaint);
                break;
        }

        return true;
    }
}