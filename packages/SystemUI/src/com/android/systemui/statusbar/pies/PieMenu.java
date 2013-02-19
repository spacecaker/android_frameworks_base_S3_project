/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.pies;

import android.app.Notification;
import android.animationing.Animator;
import android.animationing.Animator.AnimatorListener;
import android.animationing.AnimatorListenerAdapter;
import android.animationing.ValueAnimator;
import android.animationing.ValueAnimator.AnimatorUpdateListener;
import android.animationing.TimeInterpolator;
import android.database.ContentObserver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuff.Mode;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.LightingColorFilter;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.android.internal.statusbar.StatusBarNotification;
import com.android.internal.statusbar.StatusBarIcon;

import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.PieControl;
import com.android.systemui.statusbar.PieStatusPanel;
import com.android.systemui.statusbar.PieControlPanel;
import com.android.systemui.statusbar.StatusBarIconView;
import com.android.systemui.statusbar.clocks.Clock;
import com.android.systemui.statusbar.policy.PiePolicy;

import java.util.ArrayList;
import java.util.List;

public class PieMenu extends FrameLayout {

    // Linear
    private static int ANIMATOR_DEC_SPEED10 = 0;
    private static int ANIMATOR_DEC_SPEED15 = 1;
    private static int ANIMATOR_DEC_SPEED30 = 2;
    private static int ANIMATOR_ACC_SPEED10 = 3;
    private static int ANIMATOR_ACC_SPEED15 = 4;
    private static int ANIMATOR_ACC_SPEED30 = 5;

    // Cascade
    private static int ANIMATOR_ACC_INC_1 = 6;
    private static int ANIMATOR_ACC_INC_15 = 20;

    // Special purpose
    private static int ANIMATOR_BATTERY_METER = 21;
    private static int ANIMATOR_SNAP_GROW = 22;

    private static final int COLOR_ALPHA_MASK = 0xaa000000;
    private static final int COLOR_OPAQUE_MASK = 0xff000000;
    private static final int COLOR_SNAP_BACKGROUND = 0xffffffff;
    private static final int COLOR_PIE_BACKGROUND = 0x5133b5e5;
    private static final int COLOR_PIE_BUTTON = 0xb2ffffff;
    private static final int COLOR_PIE_SELECT = 0xff33b5e5;
    private static final int COLOR_PIE_OUTLINES = 0x55ffffff;
    private static final int COLOR_CHEVRON_LEFT = 0x33b5e5;
    private static final int COLOR_CHEVRON_RIGHT = 0x33b5e5;
    private static final int COLOR_BATTERY_JUICE = 0x33b5e5;
    private static final int COLOR_BATTERY_JUICE_LOW = 0xffbb33;
    private static final int COLOR_BATTERY_JUICE_CRITICAL = 0xff4444;
    private static final int COLOR_BATTERY_BACKGROUND = 0xffffff;
    private static final int COLOR_STATUS = 0xffffff;
    private static final int BASE_SPEED = 1000;
    private static final int EMPTY_ANGLE_BASE = 12;
    private static final int CHEVRON_FRAGMENTS = 16;
    private static final float SIZE_BASE = 1f;

    private static final long ANIMATION = 80;

    private Display mDisplay;
    private DisplayMetrics mDisplayMetrics = new DisplayMetrics();

    // System
    private Context mContext;
    private Resources mResources;
    private PiePolicy mPolicy;
    private Vibrator mVibrator;
    private PieStatusPanel mStatusPanel;

    // Pie handlers
    private PieItem mCurrentItem;
    private List<PieItem> mItems;
    private PieControlPanel mPanel;

    // sub menus
    private List<PieItem> mCurrentItems;
    private PieItem mOpenItem;
    private boolean mAnimating;

    private int mOverallSpeed = BASE_SPEED;
    private int mPanelDegree;
    private int mPanelOrientation;
    private int mInnerPieRadius;
    private int mOuterPieRadius;
    private int mPieGap;
    private int mInnerChevronRadius;
    private int mOuterChevronRadius;
    private int mInnerChevronRightRadius;
    private int mOuterChevronRightRadius;
    private int mInnerBatteryRadius;
    private int mOuterBatteryRadius;
    private int mStatusRadius;
    private int mNotificationsRadius;
    private int mEmptyAngle = EMPTY_ANGLE_BASE;

    private Point mCenter = new Point(0, 0);
    private float mCenterDistance = 0;

    private Path mStatusPath = new Path();
    private Path[] mChevronPathLeft  = new Path[CHEVRON_FRAGMENTS+1];
    private Path mChevronPathRight;
    private Path mBatteryPathBackground;
    private Path mBatteryPathJuice;

    private Paint mPieBackground = new Paint(COLOR_PIE_BACKGROUND);
    private Paint mPieSelected = new Paint(COLOR_PIE_SELECT);
    private Paint mPieOutlines = new Paint(COLOR_PIE_OUTLINES);
    private Paint mChevronBackgroundLeft = new Paint(COLOR_CHEVRON_LEFT);
    private Paint mChevronBackgroundRight = new Paint(COLOR_CHEVRON_RIGHT);
    private Paint mBatteryJuice = new Paint(COLOR_BATTERY_JUICE);
    private Paint mBatteryBackground = new Paint(COLOR_BATTERY_BACKGROUND);
    private Paint mSnapBackground = new Paint(COLOR_SNAP_BACKGROUND);

    private Paint mClockPaint;
    private Paint mAmPmPaint;
    private Paint mStatusPaint;
    private Paint mNotificationPaint;

    private String mClockText;
    private String mClockTextAmPm;
    private float mClockTextAmPmSize;
    private float mClockTextTotalOffset = 0;
    private float[] mClockTextOffsets = new float[20];
    private float mClockTextRotation;
    private float mClockOffset;
    private float mAmPmOffset;
    private float mStatusOffset;

    private int mNotificationCount;
    private float mNotificationsRowSize;
    private int mNotificationIconSize;
    private int mNotificationTextSize;
    private String[] mNotificationText;
    private Bitmap[] mNotificationIcon;
    private Path[] mNotificationPath;
    private NotificationData mTotalData = new NotificationData();
    private float mStartBattery;
    private float mEndBattery;
    private int mBatteryLevel;
    private boolean mNotifNew;

    private class SnapPoint {
        public SnapPoint(int snapX, int snapY, int snapRadius, int snapAlpha, int snapGravity) {
            x = snapX;
            y = snapY;
            radius = snapRadius;
            alpha = snapAlpha;
            gravity = snapGravity;
            active = false;
        }

        public int x;
        public int y;
        public int radius;
        public int alpha;
        public int gravity;
        public boolean active;
    }

    private SnapPoint[] mSnapPoint = new SnapPoint[3];
    int mSnapRadius;
    int mSnapThickness;

    // Flags
    private int mStatusMode;
    private float mPieSize = SIZE_BASE;
    private boolean mOpen;
    private boolean mNavbarZero;
    private boolean mEnableColor;
    private boolean mHapticFeedback;

    // Animations
    private int mGlowOffsetLeft = 150;
    private int mGlowOffsetRight = 150;

    private class CustomValueAnimator {

        public CustomValueAnimator(int animateIndex) {
            index = animateIndex;
            manual = false;
            animateIn = true;
            animator = ValueAnimator.ofInt(0, 1);
            animator.addUpdateListener(new CustomAnimatorUpdateListener(index));
            fraction = 0;
        }

        public void start() {
            if (!manual) {
                animator.setDuration(duration);
                animator.start();
            }
        }

        public void reverse(int milliSeconds) {
            if (!manual) {
                animator.setDuration(milliSeconds);
                animator.reverse();
            }
        }

        public void cancel() {
            animator.cancel();
            fraction = 0;
        }

        public int index;
        public int duration;
        public boolean manual;
        public boolean animateIn;
        public float fraction;
        public ValueAnimator animator;	
    }

    private CustomValueAnimator[] mAnimators = new CustomValueAnimator[25];

    private void getDimensions() {
        mPanelDegree = mPanel.getDegree();
        mPanelOrientation = mPanel.getOrientation();

        // Fetch modes
        mNavbarZero = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.SHOW_NAVI_BUTTONS, 1) == 1;
        mStatusMode = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_MODE, 2);
        mPieSize = Settings.System.getFloat(mContext.getContentResolver(),
                Settings.System.PIE_SIZE, 0.8f);
        mPieGap = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_GAP, 1);
        mHapticFeedback = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0;

        // Snap
        mSnapRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_snap_radius) * mPieSize);
        mSnapThickness = (int)(mResources.getDimensionPixelSize(R.dimen.pie_snap_thickness) * mPieSize);

        mDisplay = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        mDisplay.getMetrics(mDisplayMetrics);
        int mWidth = mDisplayMetrics.widthPixels;
        int mHeight = mDisplayMetrics.heightPixels;

        int snapIndex = 0;
        if (mPanelOrientation != Gravity.LEFT)
            mSnapPoint[snapIndex++] = new SnapPoint(0 + mSnapThickness / 2, mHeight / 2, mSnapRadius, 0x22, Gravity.LEFT);
        if (mPanelOrientation != Gravity.TOP)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth / 2, mSnapThickness / 2, mSnapRadius, 0x22, Gravity.TOP);
        if (mPanelOrientation != Gravity.RIGHT)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth - mSnapThickness / 2, mHeight / 2, mSnapRadius, 0x22, Gravity.RIGHT);
        if (mPanelOrientation != Gravity.BOTTOM)
            mSnapPoint[snapIndex++] = new SnapPoint(mWidth / 2, mHeight - mSnapThickness / 2, mSnapRadius, 0x22, Gravity.BOTTOM);
 
        // Create Pie
        mEmptyAngle = (int)(EMPTY_ANGLE_BASE * mPieSize);
        mInnerPieRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_radius_start) * mPieSize);
        mOuterPieRadius = (int)(mInnerPieRadius + mResources.getDimensionPixelSize(R.dimen.pie_radius_increment) * mPieSize);

        // Calculate chevrons: 0 - 82 & -4 - 90
        mInnerChevronRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_chevron_start) * mPieSize);
        mOuterChevronRadius = (int)(mInnerChevronRadius + mResources.getDimensionPixelSize(R.dimen.pie_chevron_increment) * mPieSize);
        mInnerChevronRightRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_chevron_start_right) * mPieSize);
        mOuterChevronRightRadius = (int)(mInnerChevronRightRadius + mResources.getDimensionPixelSize(R.dimen.pie_chevron_increment_right) * mPieSize);

        // Create slices
        float fragmentSize = 90 / CHEVRON_FRAGMENTS;
        for (int i=0; i < CHEVRON_FRAGMENTS + 1; i++) {
            mChevronPathLeft[i] = makeSlice(mPanelDegree + (i * fragmentSize), mPanelDegree + (i * fragmentSize) + fragmentSize / 2,
                    mInnerChevronRadius, mOuterChevronRadius, mCenter);
        }
        mChevronPathRight = makeSlice(mPanelDegree + (mPanelOrientation != Gravity.TOP ? -5 : 3), mPanelDegree + 90, mInnerChevronRightRadius,
                mOuterChevronRightRadius, mCenter);

        // Calculate text circle
        mStatusRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_status_start) * mPieSize);
        mStatusPath.reset();
        mStatusPath.addCircle(mCenter.x, mCenter.y, mStatusRadius, Path.Direction.CW);

        mClockPaint.setTextSize(mResources.getDimensionPixelSize(R.dimen.pie_clock_size) * mPieSize);
        mClockOffset = mResources.getDimensionPixelSize(R.dimen.pie_clock_offset) * mPieSize;
        mAmPmPaint.setTextSize(mResources.getDimensionPixelSize(R.dimen.pie_ampm_size) * mPieSize);
        mAmPmOffset = mResources.getDimensionPixelSize(R.dimen.pie_ampm_offset) * mPieSize;

        mStatusPaint.setTextSize((int)(mResources.getDimensionPixelSize(R.dimen.pie_status_size) * mPieSize));
        mStatusOffset = mResources.getDimensionPixelSize(R.dimen.pie_status_offset) * mPieSize;
        mNotificationTextSize = (int)(mResources.getDimensionPixelSize(R.dimen.pie_notification_size) * mPieSize);
        mNotificationPaint.setTextSize(mNotificationTextSize);

        // Battery
        mInnerBatteryRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_battery_start) * mPieSize);
        mOuterBatteryRadius = (int)(mInnerBatteryRadius + mResources.getDimensionPixelSize(R.dimen.pie_battery_increment) * mPieSize);

        mBatteryBackground.setColor(COLOR_BATTERY_BACKGROUND);
        mBatteryLevel = mPolicy.getBatteryLevel();
        if(mBatteryLevel <= PiePolicy.LOW_BATTERY_LEVEL
                && mBatteryLevel > PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(COLOR_BATTERY_JUICE_LOW);
        } else if(mBatteryLevel <= PiePolicy.CRITICAL_BATTERY_LEVEL) {
            mBatteryJuice.setColor(COLOR_BATTERY_JUICE_CRITICAL);
        } else {
            mBatteryJuice.setColor(COLOR_BATTERY_JUICE);
        }

        mStartBattery = mPanel.getDegree() + mEmptyAngle + mPieGap;
        mEndBattery = mPanel.getDegree() + (mPieGap <= 2 ? 88 : 90 - mPieGap);
        mBatteryPathBackground = makeSlice(mStartBattery, mEndBattery, mInnerBatteryRadius, mOuterBatteryRadius, mCenter);
        mBatteryPathJuice = makeSlice(mStartBattery, mStartBattery + mBatteryLevel * (mEndBattery-mStartBattery) /
                100, mInnerBatteryRadius, mOuterBatteryRadius, mCenter);

        mSnapBackground.setColor(COLOR_SNAP_BACKGROUND);
        mStatusPaint.setColor(COLOR_STATUS);
        mAmPmPaint.setColor(COLOR_STATUS);

        mEnableColor = (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.PIE_ENABLE_COLOR, 0) == 1);

        if (mEnableColor) {
            mPieOutlines.setColor(extractRGB(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_OUTLINE_COLOR, COLOR_PIE_OUTLINES)) | COLOR_ALPHA_MASK);
            mPieBackground.setColor(extractRGB(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_BACKGROUND_BUTTON_COLOR, COLOR_PIE_BACKGROUND)) | COLOR_ALPHA_MASK);
            mPieSelected.setColor(extractRGB(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_CHOICE_BUTTON_COLOR, COLOR_PIE_SELECT)) | COLOR_ALPHA_MASK);
            mClockPaint.setColor(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_CLOCK_COLOR, COLOR_STATUS));
            mChevronBackgroundLeft.setColor(extractRGB(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_CHEVRON_COLOR, COLOR_CHEVRON_LEFT)) | COLOR_OPAQUE_MASK);
            mChevronBackgroundRight.setColor(mNotifNew ? COLOR_STATUS : (extractRGB(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_CHEVRON_COLOR, COLOR_CHEVRON_RIGHT)) | COLOR_OPAQUE_MASK));
            mNotificationPaint.setColor(mNotifNew ? (extractRGB(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_CHEVRON_COLOR, COLOR_CHEVRON_RIGHT)) | COLOR_OPAQUE_MASK) : COLOR_STATUS);
            mBatteryJuice.setColorFilter(new PorterDuffColorFilter(extractRGB(Settings.System.getInt(mContext.getContentResolver(), 
                              Settings.System.PIE_BATTERY_COLOR, COLOR_BATTERY_JUICE)) | COLOR_OPAQUE_MASK, Mode.SRC_ATOP));
            for (PieItem item : mItems) {
                item.setColor(Settings.System.getInt(mContext.getContentResolver(), Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON));
            }
        } else {
            mPieOutlines.setColor(COLOR_PIE_OUTLINES);
            mPieBackground.setColor(COLOR_PIE_BACKGROUND);
            mPieSelected.setColor(COLOR_PIE_SELECT);
            mClockPaint.setColor(COLOR_STATUS);
            mChevronBackgroundLeft.setColor(COLOR_CHEVRON_LEFT);
            mChevronBackgroundRight.setColor(mNotifNew ? COLOR_STATUS : COLOR_CHEVRON_RIGHT);
            mNotificationPaint.setColor(mNotifNew ? COLOR_CHEVRON_RIGHT : COLOR_STATUS);
            mBatteryJuice.setColorFilter(null);
            for (PieItem item : mItems) {
                item.setColor(COLOR_PIE_BUTTON);
            }
        }

        // Notifications
        mNotificationCount = 0;
        mNotificationsRadius = (int)(mResources.getDimensionPixelSize(R.dimen.pie_notifications_start) * mPieSize);
        mNotificationIconSize = (int)(mResources.getDimensionPixelSize(R.dimen.pie_notification_icon_size) * mPieSize);
        mNotificationsRowSize = mResources.getDimensionPixelSize(R.dimen.pie_notification_row_size) * mPieSize;

        getNotifications();

        // Measure clock
        measureClock(mPolicy.getSimpleTime());

        // Determine animationspeed
        mOverallSpeed = BASE_SPEED * (mStatusMode == -1 ? 0 : mStatusMode);

        // Create animators
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i] = new CustomValueAnimator(i);
        }

        // Linear animators
        mAnimators[ANIMATOR_DEC_SPEED10].duration = (int)(mOverallSpeed * 1);
        mAnimators[ANIMATOR_DEC_SPEED10].animator.setInterpolator(new DecelerateInterpolator());

        mAnimators[ANIMATOR_DEC_SPEED15].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_DEC_SPEED15].animator.setInterpolator(new DecelerateInterpolator());

        mAnimators[ANIMATOR_DEC_SPEED30].duration = (int)(mOverallSpeed * 3);
        mAnimators[ANIMATOR_DEC_SPEED30].animator.setInterpolator(new DecelerateInterpolator());

        mAnimators[ANIMATOR_ACC_SPEED10].duration = (int)(mOverallSpeed * 1);
        mAnimators[ANIMATOR_ACC_SPEED10].animator.setInterpolator(new AccelerateInterpolator());

        mAnimators[ANIMATOR_ACC_SPEED15].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_ACC_SPEED15].animator.setInterpolator(new AccelerateInterpolator());

        mAnimators[ANIMATOR_ACC_SPEED30].duration = (int)(mOverallSpeed * 3);
        mAnimators[ANIMATOR_ACC_SPEED30].animator.setInterpolator(new AccelerateInterpolator());

        // Cascade accelerators
        for(int i = ANIMATOR_ACC_INC_1; i < ANIMATOR_ACC_INC_15 + 1; i++) {
            mAnimators[i].duration = (int)(mOverallSpeed - (mOverallSpeed * 0.8) / (i + 2));
            mAnimators[i].animator.setInterpolator(new AccelerateInterpolator());
            mAnimators[i].animator.setStartDelay(i * mOverallSpeed / 10);
        }

        // Special purpose
        mAnimators[ANIMATOR_BATTERY_METER].duration = (int)(mOverallSpeed * 1.5);
        mAnimators[ANIMATOR_BATTERY_METER].animator.setInterpolator(new DecelerateInterpolator());

        mAnimators[ANIMATOR_SNAP_GROW].manual = true;
        mAnimators[ANIMATOR_SNAP_GROW].animator.setDuration(1000);
        mAnimators[ANIMATOR_SNAP_GROW].animator.setInterpolator(new AccelerateInterpolator());
        mAnimators[ANIMATOR_SNAP_GROW].animator.addListener(new Animator.AnimatorListener() {
            @Override public void onAnimationCancel(Animator animation) {}
            @Override public void onAnimationRepeat(Animator animation) {}
            @Override public void onAnimationStart(Animator animation) {}
            @Override public void onAnimationEnd(Animator animation) {
                if (mAnimators[ANIMATOR_SNAP_GROW].fraction == 1) {
                    for (int i = 0; i < 3; i++) {
                        SnapPoint snap = mSnapPoint[i];
                        if (snap.active) {
                            if(mHapticFeedback) mVibrator.vibrate(2);
                            mStatusPanel.hidePanels(true);
                            deselect();
                            animateOut();
                            mPanel.reorient(snap.gravity);
                        }
                    }
                }
            }});
    }

    private int extractRGB(int color) {
        return color & 0x00FFFFFF;
    }

    private void measureClock(String text) {
        mClockText = text;
        mClockTextAmPm = mPolicy.getAmPm();
        mClockTextAmPmSize = mAmPmPaint.measureText(mClockTextAmPm);
        mClockTextTotalOffset = 0;

        for( int i = 0; i < mClockText.length(); i++ ) {
            char character = mClockText.charAt(i);
            float measure = mClockPaint.measureText("" + character); 
            mClockTextOffsets[i] = measure * (character == '1' || character == ':' ? 0.5f : 0.8f);
            mClockTextTotalOffset += measure * (character == '1' || character == ':' ? 0.6f : 0.9f);
        }

        mClockTextRotation = mPanel.getDegree() + (180 - (mClockTextTotalOffset * 360 /
                (2f * (mStatusRadius+Math.abs(mClockOffset)) * (float)Math.PI))) - 2;
    }

    public NotificationData setNotifications(NotificationData list) {
        mTotalData = list;
        return list;
    }

    public void setNotifNew(boolean notifnew) {
        mNotifNew = notifnew;
    }

    private void getNotifications() {
      if (mTotalData != null) {
        NotificationData notifData = mTotalData;
        if (notifData != null) {

            mNotificationText = new String[notifData.size()];
            mNotificationIcon = new Bitmap[notifData.size()];
            mNotificationPath = new Path[notifData.size()];

            for (int i = 0; i < notifData.size(); i++ ) {
                NotificationData.Entry entry = notifData.getEntryAt(i);
                StatusBarNotification statusNotif = entry.notification;
                if (statusNotif == null) continue;
                Notification notif = statusNotif.notification;
                if (notif == null) continue;
                CharSequence tickerText = notif.tickerText;
                if (tickerText == null) continue;

                if (entry.icon != null) {
                    StatusBarIconView iconView = entry.icon;
                    StatusBarIcon icon = iconView.getStatusBarIcon();
                    Drawable drawable = entry.icon.getIcon(mContext, icon);
                    if (!(drawable instanceof BitmapDrawable)) continue;
                    
                    mNotificationIcon[mNotificationCount] = ((BitmapDrawable)drawable).getBitmap();
                    mNotificationText[mNotificationCount] = tickerText.toString();

                    Path notifictionPath = new Path();
                    notifictionPath.addCircle(mCenter.x, mCenter.y, mNotificationsRadius +
                            (mNotificationsRowSize * mNotificationCount) + (mNotificationsRowSize-mNotificationTextSize),
                            Path.Direction.CW);
                    mNotificationPath[mNotificationCount] = notifictionPath;

                    mNotificationCount++;
                }
            }
        }
      }
    }

    public PieMenu(Context context, PieControlPanel panel) {
        super(context);

        mContext = context;
        mResources = mContext.getResources();
        mPanel = panel;

        setWillNotDraw(false);
        setDrawingCacheEnabled(false);

        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mPolicy = new PiePolicy(mContext);

        // Initialize classes
        mItems = new ArrayList<PieItem>();
        mPieBackground.setAntiAlias(true);
        mPieSelected.setAntiAlias(true);
        mPieOutlines.setAntiAlias(true);
        mPieOutlines.setStyle(Style.STROKE);
        mPieOutlines.setStrokeWidth(mResources.getDimensionPixelSize(R.dimen.pie_outline));
        mChevronBackgroundLeft.setAntiAlias(true);
        mChevronBackgroundRight.setAntiAlias(true);
        mBatteryJuice.setAntiAlias(true);
        mBatteryBackground.setAntiAlias(true);
        mSnapBackground.setAntiAlias(true);

        mClockPaint = new Paint();
        mClockPaint.setAntiAlias(true);     
        mClockPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mAmPmPaint = new Paint();
        mAmPmPaint.setAntiAlias(true);
        mAmPmPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mStatusPaint = new Paint();
        mStatusPaint.setAntiAlias(true);
        mStatusPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        mNotificationPaint = new Paint();
        mNotificationPaint.setAntiAlias(true);
        mNotificationPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));

        // Clock observer
        mPolicy.setOnClockChangedListener(new PiePolicy.OnClockChangedListener() {
            @Override
            public void onChange(String s) {
                measureClock(s);
            }
        });

        // Get all dimensions
        getDimensions();
    }

    public void init() {
        mStatusPanel = new PieStatusPanel(mContext, mPanel);
        getNotifications();
    }

    public PieStatusPanel getStatusPanel() {
        return mStatusPanel;
    }

    public void addItem(PieItem item) {
        mItems.add(item);
    }

    public void removeItem(PieItem item) {
        if (!mItems.isEmpty()) {
            mItems.remove(item);
        }
    }

    public void resetItem() {
        if (mItems != null) {
            mItems = null;
        }
        mItems = new ArrayList<PieItem>();
    }

    public void show(boolean show) {
        mOpen = show;
        if (mOpen) {

            // Get fresh dimensions
            getDimensions();

            // ensure clean state
            mAnimating = false;
            mCurrentItem = null;
            mOpenItem = null;
            mCurrentItems = mItems;
            for (PieItem item : mCurrentItems) {
                item.setSelected(false);
            }

            // Calculate pie's
            layoutPie();
            animateOpen();
        }
        invalidate();
    }

    private void animateOpen() {
        ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                for (PieItem item : mCurrentItems) {
                    item.setAnimationAngle((1 - animation.getAnimatedFraction()) * (- item.getStart()));
                }
                invalidate();
            }

        });
        anim.setDuration(2*ANIMATION);
        anim.start();
    }

    public void setCenter(int x, int y) {
        mCenter.y = y;
        mCenter.x = x;

        mStatusPath.reset();
        mStatusPath.addCircle(mCenter.x, mCenter.y, mStatusRadius, Path.Direction.CW);
    }

    private void layoutPie() {
        float emptyangle = mEmptyAngle * (float)Math.PI / 180;
        int inner = mInnerPieRadius;
        int outer = mOuterPieRadius;
        int itemCount = mItems.size();

        int lesserSweepCount = 0;
        for (PieItem item : mItems) {
            if (item.isLesser()) {
                lesserSweepCount += 1;
            }
        }

        float adjustedSweep = lesserSweepCount > 0 ? (((1-0.65f) * lesserSweepCount) / (itemCount-lesserSweepCount)) : 0;    
        float sweep = 0;
        float angle = 0;
        float total = 0;

        for (PieItem item : mCurrentItems) {
            sweep = ((float) (Math.PI - 2 * emptyangle) / itemCount) * (item.isLesser() ? 0.65f : 1 + adjustedSweep);
            angle = (emptyangle + sweep / 2 - (float)Math.PI/2);
            item.setPath(makeSlice(getDegrees(0) - mPieGap, getDegrees(sweep) + mPieGap, outer, inner, mCenter));
            View view = item.getView();

            if (view != null) {
                view.measure(view.getLayoutParams().width, view.getLayoutParams().height);
                int w = view.getMeasuredWidth();
                int h = view.getMeasuredHeight();
                int r = inner + (outer - inner) * 2 / 3;
                int x = (int) (r * Math.sin(total + angle));
                int y = (int) (r * Math.cos(total + angle));

                switch(mPanelOrientation) {
                    case Gravity.LEFT:
                        y = mCenter.y - (int) (r * Math.sin(total + angle)) - h / 2;
                        x = (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.RIGHT:
                        y = mCenter.y - (int) (Math.PI/2-r * Math.sin(total + angle)) - h / 2;
                        x = mCenter.x - (int) (r * Math.cos(total + angle)) - w / 2;
                        break;
                    case Gravity.TOP:
                        y = y - h / 2;
                        x = mCenter.x - (int)(Math.PI/2-x) - w / 2;
                        break;
                    case Gravity.BOTTOM: 
                        y = mCenter.y - y - h / 2;
                        x = mCenter.x - x - w / 2;
                        break;
                }                
                view.layout(x, y, x + w, y + h);
            }                    
            float itemstart = total + angle - sweep / 2;
            item.setGeometry(itemstart, sweep, inner, outer);
            total += sweep;
        }
    }

    // param angle from 0..PI to Android degrees (clockwise starting at 3
    private float getDegrees(double angle) {
        return (float) (270 - 180 * angle / Math.PI);
    }

    private class CustomAnimatorUpdateListener implements ValueAnimator.AnimatorUpdateListener {
        private int mIndex;
        CustomAnimatorUpdateListener(int index) {
            mIndex = index;
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            mAnimators[mIndex].fraction = animation.getAnimatedFraction();

            // Special purpose animators go here
            if (mIndex == ANIMATOR_BATTERY_METER) {
                mBatteryPathJuice = makeSlice(mStartBattery, mStartBattery + (float)animation.getAnimatedFraction() *
                        (mBatteryLevel * (mEndBattery-mStartBattery) / 100), mInnerBatteryRadius, mOuterBatteryRadius, mCenter);
            }
            invalidate();
        }
    }

    private void cancelAnimation() {
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i].cancel();
        }
    }

    private void animateIn() {
        // Cancel & start all animations
        cancelAnimation();
        invalidate();
        for (int i = 0; i < mAnimators.length; i++) {
            mAnimators[i].animateIn = true;
            mAnimators[i].start();
        }
    }

    public void animateOut() {
        mPanel.show(false);
        cancelAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mOpen) {
            int state;

            // Draw background
            if (mStatusMode != -1 && !mNavbarZero) {
                canvas.drawARGB((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xcc), 0, 0, 0);
            }

            // Snap points
            if (mCenterDistance > mOuterChevronRadius) {
                for (int i = 0; i < 3; i++) {
                    SnapPoint snap = mSnapPoint[i];
                    mSnapBackground.setAlpha((int)(snap.alpha + (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction * 80 : 0)));

                    canvas.drawCircle (snap.x, snap.y, (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction *
                            Math.max(getWidth(), getHeight()) : 0), mSnapBackground);

                    mSnapBackground.setAlpha((int)(snap.alpha * 2.15f  + (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction * 80 : 0)));
                    int len = (int)(snap.radius * 1.3f + (snap.active ? mAnimators[ANIMATOR_SNAP_GROW].fraction * 500 : 0));
                    int thick = (int)(len * 0.2f);
                    canvas.drawRect(snap.x - len / 2, snap.y - thick / 2, snap.x + len / 2, snap.y + thick / 2, mSnapBackground);
                    canvas.drawRect(snap.x - thick / 2, snap.y - len / 2, snap.x + thick / 2, snap.y + len / 2, mSnapBackground);
                }
            }

            // Draw base menu
            PieItem last = mCurrentItem;
            if (mOpenItem != null) {
                last = mOpenItem;
            }
            for (PieItem item : mCurrentItems) {
                if (item != last) {
                    drawItem(canvas, item);
                }
            }
            if (last != null) {
                drawItem(canvas, last);
            }

            // Paint status report only if settings allow
            if (mStatusMode != -1 && !mNavbarZero) {

                // Draw chevron rings
                mChevronBackgroundLeft.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED30].fraction * mGlowOffsetLeft / 2 * (mPanelOrientation == Gravity.TOP ? 0.2 : 1)));
                mChevronBackgroundRight.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED30].fraction * mGlowOffsetRight * (mPanelOrientation == Gravity.TOP ? 0.2 : 1)));

                if (mStatusPanel.getCurrentViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL) {
                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    for (int i=0; i < CHEVRON_FRAGMENTS + 1; i++) {
                        canvas.drawPath(mChevronPathLeft[i], mChevronBackgroundLeft);
                    }
                    canvas.restoreToCount(state);
                }

                if (mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {
                    state = canvas.save();
                    canvas.rotate(180, mCenter.x, mCenter.y);
                    canvas.drawPath(mChevronPathRight, mChevronBackgroundRight);
                    canvas.restoreToCount(state);
                }

                // Better not show inverted junk for top pies
                if (mPanelOrientation != Gravity.TOP) {

                    // Draw Battery
                    mBatteryBackground.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED15].fraction * 0x22));
                    mBatteryJuice.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED15].fraction * 0x88));

                    state = canvas.save();
                    canvas.rotate(90 + (1-mAnimators[ANIMATOR_ACC_INC_1].fraction) * 500, mCenter.x, mCenter.y);
                    canvas.drawPath(mBatteryPathBackground, mBatteryBackground);
                    canvas.restoreToCount(state);

                    state = canvas.save();
                    canvas.rotate(90, mCenter.x, mCenter.y);
                    canvas.drawPath(mBatteryPathJuice, mBatteryJuice);
                    canvas.restoreToCount(state);

                    // Draw clock && AM/PM
                    state = canvas.save();
                    canvas.rotate(mClockTextRotation, mCenter.x, mCenter.y);

                    mClockPaint.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED30].fraction * 0xcc));
                    float lastPos = 0;
                    for(int i = 0; i < mClockText.length(); i++) {
                        canvas.drawTextOnPath("" + mClockText.charAt(i), mStatusPath, lastPos, mClockOffset, mClockPaint);
                        lastPos += mClockTextOffsets[i];
                    }

                    mAmPmPaint.setAlpha((int)(mAnimators[ANIMATOR_DEC_SPEED15].fraction * 0xaa));
                    canvas.drawTextOnPath(mClockTextAmPm, mStatusPath, lastPos - mClockTextAmPmSize, mAmPmOffset, mAmPmPaint);
                    canvas.restoreToCount(state);

                    // Device status information and date
                    mStatusPaint.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED15].fraction * 0xaa));
                    
                    state = canvas.save();
                    canvas.rotate(mPanel.getDegree() + 180, mCenter.x, mCenter.y);

                    canvas.drawTextOnPath(mPolicy.getSimpleDate(), mStatusPath, 0, mStatusOffset * 4, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getDataType() + " | " + mPolicy.getSignalText(),
                                 mStatusPath, 0, mStatusOffset * 3, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getNetworkProvider() + " | " + mPolicy.getWifiSsid(),
                                 mStatusPath, 0, mStatusOffset * 2, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getMemoryInfo(), mStatusPath, 0, mStatusOffset * 1, mStatusPaint);
                    canvas.drawTextOnPath(mPolicy.getBatteryLevelReadable(), mStatusPath, 0, mStatusOffset * 0, mStatusPaint);

                    // Notifications
                    if (mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {
                        mNotificationPaint.setAlpha((int)(mAnimators[ANIMATOR_ACC_SPEED30].fraction * mGlowOffsetRight));

                        for (int i = 0; i < mNotificationCount && i < 10; i++) {
                            canvas.drawTextOnPath(mNotificationText[i], mNotificationPath[i],
                                    (1-mAnimators[ANIMATOR_ACC_INC_1 + i].fraction) * 500, 0, mNotificationPaint);

                            int IconState = canvas.save();
                            int posX = (int)(mCenter.x + mNotificationsRadius + i * mNotificationsRowSize +
                                    (1-mAnimators[ANIMATOR_ACC_INC_1 + i].fraction) * 2000);
                            int posY = (int)(mCenter.y - mNotificationIconSize * 1.4f);
                            int iconCenter = mNotificationIconSize / 2;

                            canvas.rotate(90, posX + iconCenter, posY + iconCenter);
                            canvas.drawBitmap(mNotificationIcon[i], null, new Rect(posX, posY, posX +
                                    mNotificationIconSize,posY + mNotificationIconSize), mNotificationPaint);
                            canvas.restoreToCount(IconState);
                        }
                    }
                    canvas.restoreToCount(state);
                }
            }
        }
    }

    private void drawItem(Canvas canvas, PieItem item) {
        if (item.getView() != null) {
            int state = canvas.save();
            canvas.rotate(getDegrees(item.getStartAngle())
                        + mPanel.getDegree(), mCenter.x, mCenter.y);
            canvas.drawPath(item.getPath(), item.isSelected() ? mPieSelected : mPieBackground);
            canvas.drawPath(item.getPath(), mPieOutlines);
            canvas.restoreToCount(state);

            state = canvas.save();
            ImageView view = (ImageView)item.getView();
            canvas.translate(view.getLeft(), view.getTop());
            canvas.rotate(getDegrees(item.getStartAngle()
                    + item.getSweep() / 2) + mPanel.getDegree(),
                    view.getWidth() / 2, view.getHeight() / 2);

            view.draw(canvas);
            canvas.restoreToCount(state);
        }
    }

    private Path makeSlice(float start, float end, int outer, int inner, Point center) {
        RectF bb = new RectF(center.x - outer, center.y - outer, center.x + outer, center.y + outer);
        RectF bbi = new RectF(center.x - inner, center.y - inner, center.x + inner, center.y + inner);
        Path path = new Path();
        path.arcTo(bb, start, end - start, true);
        path.arcTo(bbi, end, start - end);
        path.close();
        return path;
    }

    // touch handling for pie
    @Override
    public boolean onTouchEvent(MotionEvent evt) {
        if (evt.getPointerCount() > 1) return true;

        float x = evt.getRawX();
        float y = evt.getRawY();
        float distanceX = mCenter.x-x;
        float distanceY = mCenter.y-y;
        mCenterDistance = (float)Math.sqrt(Math.pow(distanceX, 2) + Math.pow(distanceY, 2));

        float shadeTreshold = mOuterChevronRadius;

        int action = evt.getActionMasked();
        if (MotionEvent.ACTION_DOWN == action) {
            // Open panel
            animateIn();
        } else if (MotionEvent.ACTION_UP == action) {
            if (mOpen) {
                PieItem item = mCurrentItem;

                mStatusPanel.hidePanels(true);
                switch(mStatusPanel.getFlipViewState()) {
                    case PieStatusPanel.NOTIFICATIONS_PANEL:
                        mStatusPanel.setCurrentViewState(PieStatusPanel.NOTIFICATIONS_PANEL);
                        mStatusPanel.showNotificationsPanel();
                        break;
                    case PieStatusPanel.QUICK_SETTINGS_PANEL:
                        mStatusPanel.setCurrentViewState(PieStatusPanel.QUICK_SETTINGS_PANEL);
                        mStatusPanel.showTilesPanel();
                    break;
                }

                if (!mAnimating) {
                    deselect();
                }
                // Check for click actions
                if (item != null && item.getView() != null && mCenterDistance < shadeTreshold) {
                    if(mHapticFeedback) mVibrator.vibrate(2);
                    item.getView().performClick();
                }
            }

            // Say good bye
            deselect();
            animateOut();
            return true;
        } else if (MotionEvent.ACTION_CANCEL == action) {
            if (!mAnimating) {
                deselect();
                invalidate();
            }
            return false;
        } else if (MotionEvent.ACTION_MOVE == action) {
            if (mAnimating) return false;
            boolean snapActive = false;
            for (int i = 0; i < 3; i++) {
                SnapPoint snap = mSnapPoint[i];                
                float snapDistanceX = snap.x-x;
                float snapDistanceY = snap.y-y;
                float snapDistance = (float)Math.sqrt(Math.pow(snapDistanceX, 2) + Math.pow(snapDistanceY, 2));

                if (snapDistance < mSnapRadius) {
                    snap.alpha = 50;
                    if (!snap.active) {
                        mAnimators[ANIMATOR_SNAP_GROW].cancel();
                        mAnimators[ANIMATOR_SNAP_GROW].animator.start();
                        if(mHapticFeedback) mVibrator.vibrate(2);
                    }
                    snap.active = true;
                    snapActive = true;
                    mStatusPanel.setFlipViewState(-1);
                    mGlowOffsetLeft = 150;
                    mGlowOffsetRight = 150;
                } else {
                    if (snap.active) {
                        mAnimators[ANIMATOR_SNAP_GROW].cancel();
                    }
                    snap.alpha = 30;
                    snap.active = false;
                }
            }

            // Trigger the shades?
            if (mCenterDistance > shadeTreshold) {
                int state = -1;
                switch (mPanelOrientation) {
                    case Gravity.BOTTOM:
                        state = distanceX > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.TOP:
                        state = distanceX > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.LEFT:
                        state = distanceY > 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                    case Gravity.RIGHT:
                        state = distanceY < 0 ? PieStatusPanel.QUICK_SETTINGS_PANEL : PieStatusPanel.NOTIFICATIONS_PANEL;
                        break;
                }

                if (mStatusMode != -1 && !mNavbarZero) {
                    if (state == PieStatusPanel.QUICK_SETTINGS_PANEL && 
                            mStatusPanel.getFlipViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL
                            && mStatusPanel.getCurrentViewState() != PieStatusPanel.QUICK_SETTINGS_PANEL) {
                        mGlowOffsetRight = mPanelOrientation != Gravity.TOP ? 150 : 255;;
                        mGlowOffsetLeft = mPanelOrientation != Gravity.TOP ? 255 : 150;
                        mStatusPanel.setFlipViewState(PieStatusPanel.QUICK_SETTINGS_PANEL);
                        if (mHapticFeedback && !snapActive) mVibrator.vibrate(2);
                    } else if (state == PieStatusPanel.NOTIFICATIONS_PANEL && 
                            mStatusPanel.getFlipViewState() != PieStatusPanel.NOTIFICATIONS_PANEL
                            && mStatusPanel.getCurrentViewState() != PieStatusPanel.NOTIFICATIONS_PANEL) {
                        mGlowOffsetRight = mPanelOrientation != Gravity.TOP ? 255 : 150;
                        mGlowOffsetLeft = mPanelOrientation != Gravity.TOP ? 150 : 255;
                        mStatusPanel.setFlipViewState(PieStatusPanel.NOTIFICATIONS_PANEL);
                        if (mHapticFeedback && !snapActive) mVibrator.vibrate(2);
                    }
                }
                deselect();
            }

            // Take back shade trigger if user decides to abandon his gesture
            if (mCenterDistance > shadeTreshold) {
                if (mOpenItem != null) {
                    closeSub();
                } else if (!mAnimating) {
                    deselect();
                    invalidate();
                }
            }

            if (mCenterDistance < shadeTreshold) {
                mStatusPanel.setFlipViewState(-1);
                mGlowOffsetLeft = 150;
                mGlowOffsetRight = 150;

                // Check for onEnter separately or'll face constant deselect
                PieItem item = findItem(getPolar(x, y));
                if (item == null) {
                } else if (mCurrentItem != item) {
                    if (mCenterDistance < shadeTreshold && mCenterDistance > (mInnerPieRadius/2)) {
                        onEnter(item);
                    } else {
                        deselect();
                    }
                }
            }
            invalidate();
        }
        // always re-dispatch event
        return false;
    }

    private void onEnter(PieItem item) {
        if (mCurrentItem == item) return;

        // deselect
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (item != null) {
            // clear up stack
            item.setSelected(true);
            mCurrentItem = item;
            if ((mCurrentItem != mOpenItem) && mCurrentItem.hasItems()) {
                openSub(mCurrentItem);
                mOpenItem = item;
            }
        } else {
            mCurrentItem = null;
        }
    }

    private void animateOut(final PieItem fixed, AnimatorListener listener) {
        if ((mCurrentItems == null) || (fixed == null)) return;
        final float target = fixed.getStartAngle();
        ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                for (PieItem item : mCurrentItems) {
                    item.setColor(mEnableColor ? Settings.System.getInt(mContext.getContentResolver(),
                                    Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON) : COLOR_PIE_BUTTON);
                    if (item != fixed) {
                        item.setAnimationAngle(animation.getAnimatedFraction()
                                * (target - item.getStart()));
                    }
                }
                invalidate();
            }
        });
        anim.setDuration(ANIMATION);
        anim.addListener(listener);
        anim.start();
    }

    private void animateIn(final PieItem fixed, AnimatorListener listener) {
        if ((mCurrentItems == null) || (fixed == null)) return;
        final float target = fixed.getStartAngle();
        ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
        anim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                for (PieItem item : mCurrentItems) {
                    item.setColor(mEnableColor ? Settings.System.getInt(mContext.getContentResolver(),
                                    Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON) : COLOR_PIE_BUTTON);
                    if (item != fixed) {
                        item.setAnimationAngle((1 - animation.getAnimatedFraction())
                                * (target - item.getStart()));
                    }
                }
                invalidate();

            }

        });
        anim.setDuration(ANIMATION);
        anim.addListener(listener);
        anim.start();
    }

    private void openSub(final PieItem item) {
        mAnimating = true;
        animateOut(item, new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                for (PieItem item : mCurrentItems) {
                    item.setColor(mEnableColor ? Settings.System.getInt(mContext.getContentResolver(),
                              Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON) : COLOR_PIE_BUTTON);
                    item.setAnimationAngle(0);
                }
                mCurrentItems = new ArrayList<PieItem>(mItems.size());
                int i = 0, j = 0;
                while (i < mItems.size()) {
                    if (mItems.get(i) == item) {
                        mCurrentItems.add(item);
                    } else {
                        mCurrentItems.add(item.getItems().get(j++));
                    }
                    i++;
                }
                layoutPie();
                animateIn(item, new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        for (PieItem item : mCurrentItems) {
                            item.setColor(mEnableColor ? Settings.System.getInt(mContext.getContentResolver(),
                                    Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON) : COLOR_PIE_BUTTON);
                            item.setAnimationAngle(0);
                        }
                        mAnimating = false;
                    }
                });
            }
        });
    }

    private void closeSub() {
        mAnimating = true;
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        animateOut(mOpenItem, new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator a) {
                for (PieItem item : mCurrentItems) {
                    item.setColor(mEnableColor ? Settings.System.getInt(mContext.getContentResolver(),
                                    Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON) : COLOR_PIE_BUTTON);
                    item.setAnimationAngle(0);
                }
                mCurrentItems = mItems;
                animateIn(mOpenItem, new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        for (PieItem item : mCurrentItems) {
                            item.setColor(mEnableColor ? Settings.System.getInt(mContext.getContentResolver(),
                                    Settings.System.PIE_BUTTON_COLOR, COLOR_PIE_BUTTON) : COLOR_PIE_BUTTON);
                            item.setAnimationAngle(0);
                        }
                        mAnimating = false;
                        mOpenItem = null;
                        mCurrentItem = null;
                    }
                });
            }
        });
    }

    private void deselect() {
        if (mCurrentItem != null) {
            mCurrentItem.setSelected(false);
        }
        if (mOpenItem != null) {
            mOpenItem = null;
            mCurrentItems = mItems;
        }
        mCurrentItem = null;
    }

    private float getPolar(float x, float y) {
        float deltaY = mCenter.y - y;
        float deltaX = mCenter.x - x;
        float adjustAngle = 0;;
        switch(mPanelOrientation) {
            case Gravity.TOP:
            case Gravity.LEFT:
                adjustAngle = 90;
                break;
            case Gravity.RIGHT:
                adjustAngle = -90;
                break;
        }
        return (adjustAngle + (float)Math.atan2(mPanelOrientation == Gravity.TOP ? deltaY : deltaX,
                mPanelOrientation == Gravity.TOP ? deltaX : deltaY) * 180 / (float)Math.PI)
                * (mPanelOrientation == Gravity.TOP ? -1 : 1) * (float)Math.PI / 180;
    }

    private PieItem findItem(float polar) {
        if (mCurrentItems != null) {
            int c = 0;
            for (PieItem item : mCurrentItems) {
                if (inside(polar, item)) {
                    return item;
                }
            }
        }
        return null;
    }

    private boolean inside(float polar, PieItem item) {
        return (item.getStartAngle() < polar)
        && (item.getStartAngle() + item.getSweep() > polar);
    }
}
