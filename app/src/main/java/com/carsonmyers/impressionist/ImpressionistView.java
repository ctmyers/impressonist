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
    private Path mPath;

    private VelocityTracker mVelocityTracker = null;
    private int mActivePointerId;


    private int mAlpha = 150;
    private int mDefaultRadius = 25;
    private Point mLastPoint = null;
    private long mLastPointTime = -1;
    private boolean mUseMotionSpeedForBrushStrokeSize = true;
    private Paint mPaintBorder = new Paint();
    private BrushType mBrushType = BrushType.Square;
    private float mMinBrushRadius = 5;

    private Brush mBrush;


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

        mBrush = new CircleBrush();

        mLastPoint = new Point(0,0);

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
    //https://developer.android.com/training/gestures/scale.html <<<<<<<<<<------------------------------------------------------------------------------------------------------------------------------
    //http://stackoverflow.com/questions/11966692/android-smooth-multi-touch-drawing
    @Override
    public boolean onTouchEvent(MotionEvent motionEvent){

        //TODO
        //Basically, the way this works is to listen for Touch Down and Touch Move events and determine where those
        //touch locations correspond to the bitmap in the ImageView. You can then grab info about the bitmap--like the pixel color--
        //at that location

        // Taken from my doodler code
        super.onTouchEvent(motionEvent);

        final int action = MotionEventCompat.getActionMasked(motionEvent);

        //motionEvent.getPointerCount();


        switch (action) {
            case MotionEvent.ACTION_DOWN:{


                for (int size = motionEvent.getPointerCount(), pointerIndex = 0; pointerIndex < size; pointerIndex++) {


                    //final int pointerIndex = MotionEventCompat.getActionIndex(motionEvent);
                    //mActivePointerId = motionEvent.getPointerId(pointerIndex);

                    float curTouchX = MotionEventCompat.getX(motionEvent, pointerIndex);
                    float curTouchY = MotionEventCompat.getY(motionEvent, pointerIndex);
                    Rect rect = new Rect(getBitmapPositionInsideImageView(mImageView));
                    if (!rect.contains((int) curTouchX, (int) curTouchY))
                        return true;

                    if (mVelocityTracker == null) {
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        mVelocityTracker.clear();
                    }
                    mVelocityTracker.addMovement(motionEvent);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:{
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mImage != null) {

                    for (int size = motionEvent.getPointerCount(), pointerIndex = 0; pointerIndex < size; pointerIndex++) {

                        float curTouchX = MotionEventCompat.getX(motionEvent, pointerIndex);
                        float curTouchY = MotionEventCompat.getY(motionEvent, pointerIndex);
                        Rect rect = new Rect(getBitmapPositionInsideImageView(mImageView));
                        if (!rect.contains((int) curTouchX, (int) curTouchY))
                            return true;

                        mVelocityTracker.addMovement(motionEvent);
                        mVelocityTracker.computeCurrentVelocity(1000);
                        float vx = VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId);
                        float vy = VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId);

                        int historySize = motionEvent.getHistorySize();

                        for (int i = 0; i < historySize; i++) {
                            float touchX = motionEvent.getHistoricalX(pointerIndex, i);
                            float touchY = motionEvent.getHistoricalY(pointerIndex, i);
                            paintOnCanvus(touchX, touchY, vx, vy);
                        }
                        paintOnCanvus(curTouchX, curTouchY, vx, vy);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                mActivePointerId = INVALID_POINTER_ID;
                break;
            case MotionEvent.ACTION_CANCEL:
                mActivePointerId = INVALID_POINTER_ID;
                mVelocityTracker.recycle();
                break;
            case MotionEvent.ACTION_POINTER_UP: {

                final int pointerIndex = MotionEventCompat.getActionIndex(motionEvent);
                final int pointerId = MotionEventCompat.getPointerId(motionEvent, pointerIndex);

                if (pointerId == mActivePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;

                    mVelocityTracker.addMovement(motionEvent);
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float vx = VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId);
                    float vy = VelocityTrackerCompat.getYVelocity(mVelocityTracker, mActivePointerId);

                    float curTouchX = MotionEventCompat.getX(motionEvent, newPointerIndex);
                    float curTouchY = MotionEventCompat.getY(motionEvent, newPointerIndex);

                    paintOnCanvus(curTouchX, curTouchY, vx, vy);

                    mActivePointerId = MotionEventCompat.getPointerId(motionEvent, newPointerIndex);
                }
                break;
            }
        }

        invalidate();
        return true;


    }

    // returns the points of the bitmap
    private static Point getBitmapPoints(float x, float y, ImpressionistView impView, Bitmap bit){
        int newX = 0;
        int newY = 0;

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
     * @return
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

        int top = (int) (imgViewH - heightActual)/2;
        int left = (int) (imgViewW - widthActual)/2;

        rect.set(left, top, left + widthActual, top + heightActual);

        return rect;
    }

    private Paint getNewPaint(){
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setAlpha(mAlpha);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(4);
        return paint;
    }

    public Bitmap getBitmap(){
        return mOffScreenBitmap;
    }

    private boolean paintOnCanvus(float x, float y, float vx, float vy){
        Paint paint = getNewPaint();

        int color;
        int r;
        int g;
        int b;

        if(y > this.getHeight() || y <= 0 || x > this.getWidth() || x <= 0)
            return true;
        Rect rect = new Rect(getBitmapPositionInsideImageView(mImageView));
        if(!rect.contains((int)x, (int)y))
            return true;

        Point p = getBitmapPoints(x,y,this, mImage);
        color = mImage.getPixel(p.x, p.y);


        r = Color.red(color);
        g = Color.green(color);
        b = Color.blue(color);

        mPaint.setARGB(150, r,g,b);

        float rad = 0;

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