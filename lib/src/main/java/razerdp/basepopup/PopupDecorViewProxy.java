package razerdp.basepopup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import razerdp.util.log.LogTag;
import razerdp.util.log.PopupLogUtil;

/**
 * Created by 大灯泡 on 2017/12/25.
 * <p>
 * 旨在用来拦截keyevent、以及蒙层
 */
final class PopupDecorViewProxy extends ViewGroup {
    private static final String TAG = "HackPopupDecorView";
    //模糊层
    private PopupMaskLayout mMaskLayout;
    private BasePopupHelper mHelper;
    private View mTarget;
    private Rect mTouchRect = new Rect();

    private int childLeftMargin;
    private int childTopMargin;
    private int childRightMargin;
    private int childBottomMargin;

    private PopupDecorViewProxy(Context context) {
        this(context, null);
    }

    private PopupDecorViewProxy(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    private PopupDecorViewProxy(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public static PopupDecorViewProxy create(Context context, BasePopupHelper helper) {
        PopupDecorViewProxy result = new PopupDecorViewProxy(context);
        result.init(helper);
        return result;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(BasePopupHelper helper) {
        mHelper = helper;
        setClipChildren(mHelper.isClipChildren());
        if (mHelper.isInterceptTouchEvent()) {
            setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } else {
            setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        mMaskLayout = PopupMaskLayout.create(getContext(), mHelper);
        addViewInLayout(mMaskLayout, -1, new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (!mHelper.isInterceptTouchEvent()) {
            mMaskLayout.setVisibility(GONE);
        } else {
            mMaskLayout.setVisibility(VISIBLE);
        }

        mMaskLayout.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        return mHelper.isDismissWhenTouchOutside();
                    case MotionEvent.ACTION_UP:
                        if (mHelper.isDismissWhenTouchOutside()) {
                            int x = (int) event.getX();
                            int y = (int) event.getY();
                            if (mTarget != null) {
                                mTarget.getGlobalVisibleRect(mTouchRect);
                            }
                            if (!mTouchRect.contains(x, y)) {
                                mHelper.onOutSideTouch();
                            }
                        }
                        break;
                }
                return false;
            }
        });
    }


    public void addPopupDecorView(View target, WindowManager.LayoutParams params) {
        if (target == null) {
            throw new NullPointerException("contentView不能为空");
        }
        if (getChildCount() == 2) {
            removeViewsInLayout(1, 1);
        }
        mTarget = target;
        WindowManager.LayoutParams wp = new WindowManager.LayoutParams();
        wp.copyFrom(params);
        wp.x = 0;
        wp.y = 0;

        boolean parseLayoutParamsFromXML = false;

        if (mHelper.getContentViewLayoutId() != 0) {
            try {
                FrameLayout tempLayout = new FrameLayout(getContext());
                View tempView = LayoutInflater.from(getContext()).inflate(mHelper.getContentViewLayoutId(), tempLayout, false);
                ViewGroup.LayoutParams childParams = tempView.getLayoutParams();
                if (childParams != null) {
                    wp.width = childParams.width;
                    wp.height = childParams.height;
                    if (childParams instanceof ViewGroup.MarginLayoutParams) {
                        MarginLayoutParams marginChildParams = ((MarginLayoutParams) childParams);
                        childLeftMargin = marginChildParams.leftMargin;
                        childTopMargin = marginChildParams.topMargin;
                        childRightMargin = marginChildParams.rightMargin;
                        childBottomMargin = marginChildParams.bottomMargin;
                    }
                    parseLayoutParamsFromXML = true;
                }
                tempView = null;
                tempLayout = null;
            } catch (Exception e) {
                PopupLogUtil.trace(e);
                parseLayoutParamsFromXML = false;
            }
        }

        if (!parseLayoutParamsFromXML) {
            View contentView = findContentView(target);
            boolean hasSet = false;

            if (contentView != null) {
                if (mHelper.getPopupViewWidth() == LayoutParams.WRAP_CONTENT &&
                        mHelper.getPopupViewHeight() == LayoutParams.WRAP_CONTENT) {
                    if (wp.width <= 0) {
                        wp.width = contentView.getMeasuredWidth() == 0 ? mHelper.getPopupViewWidth() : contentView.getMeasuredWidth();
                        hasSet = true;
                    }
                    if (wp.height <= 0) {
                        wp.height = contentView.getMeasuredHeight() == 0 ? mHelper.getPopupViewHeight() : contentView.getMeasuredHeight();
                        hasSet = true;
                    }
                }
            }
            if (!hasSet) {
                if (wp.width <= 0) {
                    wp.width = mHelper.getPopupViewWidth();
                }
                if (wp.height <= 0) {
                    wp.height = mHelper.getPopupViewHeight();
                }

            }
        }

        addView(target, wp);
    }

    private View findContentView(View root) {
        if (root == null) return null;
        if (!(root instanceof ViewGroup)) return root;
        ViewGroup rootGroup = (ViewGroup) root;
        if (rootGroup.getChildCount() <= 0) return root;
        String viewSimpleClassName = rootGroup.getClass().getSimpleName();
        View result = null;
        while (!isContentView(viewSimpleClassName)) {
            result = rootGroup.getChildAt(0);
            viewSimpleClassName = result.getClass().getSimpleName();
            if (result instanceof ViewGroup) {
                rootGroup = ((ViewGroup) result);
            } else {
                break;
            }
        }
        return result;
    }

    private boolean isContentView(String contentClassName) {
        return !TextUtils.equals(contentClassName, "PopupDecorView") &&
                !TextUtils.equals(contentClassName, "PopupViewContainer") &&
                !TextUtils.equals(contentClassName, "PopupBackgroundView");

    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mHelper.isInterceptTouchEvent()) {
            measureWithIntercept(widthMeasureSpec, heightMeasureSpec);
        } else {
            measureWithOutIntercept(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void measureWithIntercept(int widthMeasureSpec, int heightMeasureSpec) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            //蒙层给最大值
            if (child == mMaskLayout) {
                measureChild(child, MeasureSpec.makeMeasureSpec(getScreenWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getScreenHeight(), MeasureSpec.EXACTLY));
            } else {
                final LayoutParams lp = child.getLayoutParams();
                final int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        childLeftMargin + childRightMargin, lp.width);
                final int childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                        childTopMargin + childBottomMargin, lp.height);

                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
        setMeasuredDimension(getScreenWidth(), getScreenHeight());
    }

    private void measureWithOutIntercept(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = 0;
        int maxHeight = 0;
        int childState = 0;

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE && child != mMaskLayout) {
                measureChild(child, widthMeasureSpec, heightMeasureSpec);
                maxWidth = Math.max(maxWidth, child.getMeasuredWidth());
                maxHeight = Math.max(maxHeight, child.getMeasuredHeight());
                childState = combineMeasuredStates(childState, child.getMeasuredState());
            }
        }

        //因为masklayout是match_parent的，因此测量完target后再决定自己的大小
        //背景由用户自己决定。
//        measureChild(mMaskLayout, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState), resolveSizeAndState(maxHeight, heightMeasureSpec,
                childState << MEASURED_HEIGHT_STATE_SHIFT));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (!changed) {
            return;
        }
        if (mHelper.isInterceptTouchEvent()) {
            layoutWithIntercept(l, t, r, b);
        } else {
            layoutWithOutIntercept(l, t, r, b);
        }
    }

    private void layoutWithOutIntercept(int l, int t, int r, int b) {
        final int childCount = getChildCount();
        if (childCount > 0) {
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                child.layout(l, t, r, b);
            }
        }
    }

    private void layoutWithIntercept(int l, int t, int r, int b) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();

            int gravity = mHelper.getPopupGravity();

            int childLeft = child.getLeft();
            int childTop = child.getTop();

            int offsetX = mHelper.getOffsetX();
            int offsetY = mHelper.getOffsetY();

            boolean delayLayoutMask = mHelper.isAlignBackground();

            if (child == mMaskLayout) {
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            } else {
                boolean isRelativeToAnchor = mHelper.isShowAsDropDown();
                int anchorCenterX = mHelper.getAnchorX() + (mHelper.getAnchorViewWidth() >> 1);
                int anchorCenterY = mHelper.getAnchorY() + (mHelper.getAnchorHeight() >> 1);
                //不跟anchorView联系的情况下，gravity意味着在整个view中的方位
                //如果跟anchorView联系，gravity意味着以anchorView为中心的方位
                switch (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.LEFT:
                    case Gravity.START:
                        if (isRelativeToAnchor) {
                            childLeft = mHelper.getAnchorX() - width + childLeftMargin;
                        }
                        break;
                    case Gravity.RIGHT:
                    case Gravity.END:
                        if (isRelativeToAnchor) {
                            childLeft = mHelper.getAnchorX() + mHelper.getAnchorViewWidth() + childLeftMargin;
                        } else {
                            childLeft = getMeasuredWidth() - width - childRightMargin;
                        }
                        break;
                    case Gravity.CENTER_HORIZONTAL:
                        if (isRelativeToAnchor) {
                            childLeft = mHelper.getAnchorX();
                            offsetX += anchorCenterX - (childLeft + (width >> 1));
                        } else {
                            childLeft = ((r - l - width) >> 1) + childLeftMargin - childRightMargin;
                        }
                        break;
                    default:
                        if (isRelativeToAnchor) {
                            childLeft = mHelper.getAnchorX() + childLeftMargin;
                        }
                        break;
                }

                switch (gravity & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.TOP:
                        if (isRelativeToAnchor) {
                            childTop = mHelper.getAnchorY() - height + childTopMargin;
                        }
                        break;
                    case Gravity.BOTTOM:
                        if (isRelativeToAnchor) {
                            childTop = mHelper.getAnchorY() + mHelper.getAnchorHeight() + childTopMargin;
                        } else {
                            childTop = b - t - height - childBottomMargin;
                        }
                        break;
                    case Gravity.CENTER_VERTICAL:
                        if (isRelativeToAnchor) {
                            childTop = mHelper.getAnchorY() + mHelper.getAnchorHeight();
                            offsetY += anchorCenterY - (childTop + (height >> 1));
                        } else {
                            childTop = ((b - t - height) >> 1) + childTopMargin - childBottomMargin;
                        }
                        break;
                    default:
                        if (isRelativeToAnchor) {
                            childTop = mHelper.getAnchorY() + mHelper.getAnchorHeight() + childTopMargin;
                        }
                        break;
                }

                int left = childLeft + offsetX;
                int top = childTop + offsetY;
                int right = left + width;
                int bottom = top + height;


                //-1:onTop
                //0:nothing
                //1:onBottom
                int adjustAutoLocatedResult = 0;
                if (mHelper.isAutoLocatePopup()) {
                    final boolean onTop = bottom > getMeasuredHeight();
                    if (onTop) {
                        adjustAutoLocatedResult = -1;
                        top += -(mHelper.getAnchorHeight() + height);
                        bottom = top + height;
                        mHelper.onAnchorTop();
                    } else {
                        adjustAutoLocatedResult = 1;
                        mHelper.onAnchorBottom();
                    }
                }

                if (mHelper.isClipToScreen()) {
                    //将popupContentView限制在屏幕内，跟autoLocate有冲突，因此分开解决。
                    //如果bottom超出屏幕过多，则需要特殊处理
                    int screenLeft = 0;
                    int screenTop = isRelativeToAnchor ? childTop : 0;
                    int screenRight = getMeasuredWidth();
                    int screenBottom = getMeasuredHeight();
                    if (adjustAutoLocatedResult != 0) {
                        //adjust的情况下，isRelativeToAnchor必定为true
                        switch (adjustAutoLocatedResult) {
                            case -1:
                                //自动定位到top，则bottom相当于上移
                                screenTop = 0;
                                screenBottom = childTop - mHelper.getAnchorHeight();
                                break;
                            case 1:
                                screenTop = childTop;
                                break;
                        }
                    }

                    left = Math.max(screenLeft, left);
                    top = Math.max(screenTop, top);

                    int tOffset = 0;
                    if (right > screenRight) {
                        tOffset = right - screenRight;
                        if (tOffset <= left) {
                            //只需要移动left即可
                            left -= tOffset;
                            right = left + width;
                        } else {
                            right = screenRight;
                        }
                    }

                    if (bottom > screenBottom) {
                        tOffset = bottom - screenBottom;
                        if (screenTop == 0) {
                            //如果screenTop没有限制，则可以上移，否则只能移到限定的screenTop下
                            top -= tOffset;
                            bottom = top + height;
                        } else {
                            bottom = top + height;
                        }
                    }
                }

                if (delayLayoutMask) {
                    mMaskLayout.handleAlignBackground(left, top, right, bottom);
                }

                child.layout(left, top, right, bottom);
            }

        }
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mMaskLayout != null) {
            mMaskLayout.handleStart(-2);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mHelper != null) {
            return mHelper.onInterceptTouchEvent(ev);
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean intercept = mHelper != null && mHelper.onDispatchKeyEvent(event);
        if (intercept) return true;
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (getKeyDispatcherState() == null) {
                return super.dispatchKeyEvent(event);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                final KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null) {
                    state.startTracking(event, this);
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                final KeyEvent.DispatcherState state = getKeyDispatcherState();
                if (state != null && state.isTracking(event) && !event.isCanceled()) {
                    if (mHelper != null) {
                        PopupLogUtil.trace(LogTag.i, TAG, "dispatchKeyEvent: >>> onBackPressed");
                        return mHelper.onBackPressed();
                    }
                }
            }
            return super.dispatchKeyEvent(event);
        } else {
            return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mHelper != null) {
            if (mHelper.onTouchEvent(event)) {
                return true;
            }
        }
        final int x = (int) event.getX();
        final int y = (int) event.getY();

        if ((event.getAction() == MotionEvent.ACTION_DOWN)
                && ((x < 0) || (x >= getWidth()) || (y < 0) || (y >= getHeight()))) {
            if (mHelper != null) {
                PopupLogUtil.trace(LogTag.i, TAG, "onTouchEvent:[ACTION_DOWN] >>> onOutSideTouch");
                return mHelper.onOutSideTouch();
            }
        } else if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            if (mHelper != null) {
                PopupLogUtil.trace(LogTag.i, TAG, "onTouchEvent:[ACTION_OUTSIDE] >>> onOutSideTouch");
                return mHelper.onOutSideTouch();
            }
        }
        return super.onTouchEvent(event);
    }

    int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }
}
