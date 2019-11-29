package com.line.foldtext;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatTextView;


public class FoldTextView extends AppCompatTextView {
    private static final String TAG = "FoldTextView";
    private static final String ELLIPSIZE_END = "...";
    private static final int MAX_LINE = 4;
    private static final String EXPAND_TIP_TEXT = " 收起";
    private static final String FOLD_TIP_TEXT = "展开";
    private static final int TIP_COLOR = 0xFFFFFFFF;
    /**
     * 全文显示的位置
     */
    private static final int END = 0;
    private int mShowMaxLine;
    /**
     * 折叠文本
     */
    private String mFoldText;
    /**
     * 展开文本
     */
    private String mExpandText;
    /**
     * 原始文本
     */
    private CharSequence mOriginalText;
    /**
     * 是否展开
     */
    private boolean isExpand;
    /**
     * 全文显示的位置
     */
    private int mTipGravity;
    /**
     * 全文文字的颜色
     */
    private int mTipColor;
    private boolean flag;
    private Paint foldTextPaint;

    /**
     * 展开后是否显示文字提示
     */
    private boolean isShowTipAfterExpand;


    /**
     * 提示文字坐标
     */
    float minX;
    float maxX;
    float minY;
    float maxY;
    /**
     * 收起全文不在一行显示时
     */
    float middleY;
    /**
     * 原始文本的行数
     */
    int originalLineCount;

    /**
     * 点击时间
     */
    private long clickTime;
    /**
     * 是否超过最大行数
     */
    private boolean isOverMaxLine;

    /**
     * 收起展开使用icon
     */
    private boolean foldWithIcon = true;
    /**
     * 箭头width
     */
    private int iconLineWidth = 5;
    /**
     * 箭头一边所在正方形的边长
     */
    private int iconSize = 10;

    private int iconColor = Color.GRAY;

    private Paint iconPaint;


    public FoldTextView(Context context) {
        this(context, null);
    }


    public FoldTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
    }

    public FoldTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mShowMaxLine = MAX_LINE;
        if (attrs != null) {
            TypedArray arr = context.obtainStyledAttributes(attrs, R.styleable.FoldTextView);
            mShowMaxLine = arr.getInt(R.styleable.FoldTextView_fold_text_showMaxLine, MAX_LINE);
            mTipGravity = arr.getInt(R.styleable.FoldTextView_fold_text_tipGravity, END);
            mTipColor = arr.getColor(R.styleable.FoldTextView_fold_text_tipColor, TIP_COLOR);
            mFoldText = arr.getString(R.styleable.FoldTextView_fold_text_foldText);
            mExpandText = arr.getString(R.styleable.FoldTextView_fold_text_expandText);
            isShowTipAfterExpand = arr.getBoolean(R.styleable.FoldTextView_fold_text_showTipAfterExpand, false);
            foldWithIcon = arr.getBoolean(R.styleable.FoldTextView_fold_text_foldWithIcon, true);
            iconSize = arr.getDimensionPixelSize(R.styleable.FoldTextView_fold_text_iconSize, 10);
            iconColor = arr.getColor(R.styleable.FoldTextView_fold_text_iconColor, Color.GRAY);
            iconLineWidth = arr.getDimensionPixelSize(R.styleable.FoldTextView_fold_text_iconLineWidth, 5);
            arr.recycle();
        }
        if (TextUtils.isEmpty(mExpandText)) {
            mExpandText = EXPAND_TIP_TEXT;
        }
        if (TextUtils.isEmpty(mFoldText)) {
            mFoldText = FOLD_TIP_TEXT;
        }
        if (mTipGravity == END) {
            mFoldText = "   ".concat(mFoldText);
        }

        if (foldWithIcon) {
            mExpandText = " ";
        }

        initPaint();
    }

    private void initPaint() {
        if (foldWithIcon) {
            iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            iconPaint.setColor(iconColor);
            iconPaint.setStyle(Paint.Style.STROKE);
            iconPaint.setStrokeCap(Paint.Cap.SQUARE);
            iconPaint.setStrokeJoin(Paint.Join.ROUND);
            iconPaint.setStrokeWidth(iconLineWidth);
        } else {
            foldTextPaint = new Paint();
            foldTextPaint.setTextSize(getTextSize());
            foldTextPaint.setColor(mTipColor);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(new InnerClickListener());
    }

    @Override
    public void setText(final CharSequence text, final TextView.BufferType type) {
        mOriginalText = text;
        if (TextUtils.isEmpty(text) || mShowMaxLine == 0) {
            super.setText(text, type);
        } else if (isExpand) {
            //文字展开
            SpannableStringBuilder spannable = new SpannableStringBuilder(mOriginalText);
            if (isShowTipAfterExpand) {
                spannable.append(mExpandText);
                spannable.setSpan(new ForegroundColorSpan(mTipColor), spannable.length() - mExpandText.length(), spannable.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            }
            super.setText(spannable, type);
            int mLineCount = getLineCount();
            Layout layout = getLayout();
            minX = getPaddingLeft() + layout.getPrimaryHorizontal(spannable.toString().lastIndexOf(mExpandText.charAt(0)) - 1);
            maxX = getPaddingLeft() + layout.getSecondaryHorizontal(spannable.toString().lastIndexOf(mExpandText.charAt(mExpandText.length() - 1)) + 1);
            Rect bound = new Rect();
            if (mLineCount > originalLineCount) {
                //不在同一行
                layout.getLineBounds(originalLineCount - 1, bound);
                minY = getPaddingTop() + bound.top;
                middleY = minY + getPaint().getFontMetrics().descent - getPaint().getFontMetrics().ascent;
                maxY = middleY + getPaint().getFontMetrics().descent - getPaint().getFontMetrics().ascent;
            } else {
                //同一行
                layout.getLineBounds(originalLineCount - 1, bound);
                minY = getPaddingTop() + bound.top;
                maxY = minY + getPaint().getFontMetrics().descent - getPaint().getFontMetrics().ascent;
            }
        } else {
            if (!flag) {
                getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        getViewTreeObserver().removeOnPreDrawListener(this);
                        flag = true;
                        formatText(text, type);
                        return true;
                    }
                });
            } else {
                formatText(text, type);
            }
        }
    }

    private void formatText(CharSequence text, final TextView.BufferType type) {
        Layout layout = getLayout();
        if (layout == null || !layout.getText().equals(mOriginalText)) {
            super.setText(mOriginalText, type);
            layout = getLayout();
        }
        if (layout == null) {
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    translateText(getLayout(), type);
                }
            });
        } else {
            translateText(layout, type);
        }
    }

    private void translateText(Layout layout, TextView.BufferType type) {
        originalLineCount = layout.getLineCount();
        if (layout.getLineCount() > mShowMaxLine) {
            isOverMaxLine = true;
            SpannableStringBuilder span = new SpannableStringBuilder();
            int start = layout.getLineStart(mShowMaxLine - 1);
            int end = layout.getLineEnd(mShowMaxLine - 1);
            if (mTipGravity == END) {
                TextPaint paint = getPaint();
                StringBuilder builder = new StringBuilder(ELLIPSIZE_END).append(mFoldText);
                end -= paint.breakText(mOriginalText, start, end, false, paint.measureText(builder.toString()), null);
                float x = getWidth() - getPaddingLeft() - getPaddingRight() - getTextWidth(mFoldText);
                while (layout.getPrimaryHorizontal(end - 1) + getTextWidth(mOriginalText.subSequence(end - 1, end).toString()) < x) {
                    end++;
                }
                end--;
            } else {
                end--;
            }
            CharSequence ellipsize = mOriginalText.subSequence(0, end);
            span.append(ellipsize);
            span.append(ELLIPSIZE_END);
            if (mTipGravity != END) {
                span.append("\n");
            }
            super.setText(span, type);
        } else {
            isOverMaxLine = false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (isOverMaxLine) {
            if (!isExpand) {
                //折叠
                if (mTipGravity == END) {
                    minX = getWidth() - getPaddingLeft() - getPaddingRight() - getTextWidth(mFoldText);
                    maxX = getWidth() - getPaddingLeft() - getPaddingRight();
                    minY = getHeight() - (getPaint().getFontMetrics().descent - getPaint().getFontMetrics().ascent) - getPaddingBottom();
                    maxY = getHeight() - getPaddingBottom();
                    if (foldWithIcon) {
                        drawDownIcon(canvas, getIconWidth() + minX, getHeight() - getPaint().getFontMetrics().descent - getPaddingBottom() - getIconHeight());
                    } else {
                        canvas.drawText(mFoldText, minX, getHeight() - getPaint().getFontMetrics().descent - getPaddingBottom(), foldTextPaint);
                    }
                } else {
                    minX = getPaddingLeft();
                    maxX = minX + getTextWidth(mFoldText);
                    minY = getHeight() - (getPaint().getFontMetrics().descent - getPaint().getFontMetrics().ascent) - getPaddingBottom();
                    maxY = getHeight() - getPaddingBottom();
                    if (foldWithIcon) {
                        drawDownIcon(canvas, getIconWidth() + minX, getHeight() - getPaint().getFontMetrics().descent - getPaddingBottom() - getIconHeight());
                    } else {
                        canvas.drawText(mFoldText, minX, getHeight() - getPaint().getFontMetrics().descent - getPaddingBottom(), foldTextPaint);
                    }
                }
            } else {
                if (foldWithIcon) {
                    drawUpIcon(canvas, maxX, getHeight() - getPaint().getFontMetrics().descent - getPaddingBottom() - getIconHeight());
                }
            }
        }
    }

    private float getIconWidth() {
        return iconSize * 2;
    }

    private float getIconHeight() {
        return iconSize;
    }

    private void drawDownIcon(Canvas canvas, float left, float top) {
        canvas.drawLine(left, top, left + iconSize, top + iconSize, iconPaint);
        canvas.drawLine(left + iconSize, top + iconSize, left + iconSize * 2, top, iconPaint);
    }

    private void drawUpIcon(Canvas canvas, float left, float top) {
        canvas.drawLine(left, top + iconSize, left + iconSize, top, iconPaint);
        canvas.drawLine(left + iconSize, top, left + iconSize * 2, top + iconSize, iconPaint);
    }

    private float getTextWidth(String text) {
        Paint paint = getPaint();
        return paint.measureText(text);
    }


    private class InnerClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            isExpand = !isExpand;
            setText(mOriginalText);
        }
    }

    public FoldTextView setExpand(boolean expand) {
        isExpand = expand;
        return this;
    }

    public void setShowMaxLine(int mShowMaxLine) {
        this.mShowMaxLine = mShowMaxLine;
        invalidate();
    }

    public void setFoldText(String mFoldText) {
        this.mFoldText = mFoldText;
        invalidate();
    }

    public void setExpandText(String mExpandText) {
        this.mExpandText = mExpandText;
        invalidate();
    }

    public void setTipGravity(int mTipGravity) {
        this.mTipGravity = mTipGravity;
        invalidate();
    }

    public void setTipColor(int mTipColor) {
        this.mTipColor = mTipColor;
        invalidate();
    }

    public void setShowTipAfterExpand(boolean showTipAfterExpand) {
        isShowTipAfterExpand = showTipAfterExpand;
    }

}
