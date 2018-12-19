package org.copticlibrary.coptictime;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import org.joda.time.Chronology;
import org.joda.time.LocalDate;
import org.joda.time.chrono.CopticChronology;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CopticGregorianWatchFace extends CanvasWatchFaceService {
  private static final Typeface NORMAL_TYPEFACE =
    Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

  private static final Typeface SMALL_TYPEFACE =
    Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);

  private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

  private static final int MSG_UPDATE_TIME = 0;
  private static final String TAG = "CopticWatchFace";

  @Override
  public Engine onCreateEngine() {
    return new Engine();
  }

  private static class EngineHandler extends Handler {
    private final WeakReference<CopticGregorianWatchFace.Engine> mWeakReference;

    public EngineHandler(CopticGregorianWatchFace.Engine reference) {
      mWeakReference = new WeakReference<>(reference);
    }

    @Override
    public void handleMessage(Message msg) {
      CopticGregorianWatchFace.Engine engine = mWeakReference.get();
      if (engine != null) {
        switch (msg.what) {
          case MSG_UPDATE_TIME:
            engine.handleUpdateTimeMessage();
            break;
        }
      }
    }
  }

  private class Engine extends CanvasWatchFaceService.Engine {

    private final Handler mUpdateTimeHandler = new EngineHandler(this);
    private Calendar mCalendar;
    private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      }
    };
    private boolean mRegisteredTimeZoneReceiver = false;
    private float mXOffset;
    private float mYOffset;
    private Paint mBackgroundPaint;
    private Paint mTextPaint;
    private Paint mCopticTextPaint;
    private Paint mGregorianTextPaint;
    private Paint mCopticEventTextPaint;
    private float mXCopticOffset;
    private float mYCopticOffset;

    private float mXGregorianOffset,mXEventsOffset;
    private float mYGregorianOffset, mYCopticEventOffset;
    /**
     * Whether the display supports fewer bits for each color in ambient mode. When true, we
     * disable anti-aliasing in ambient mode.
     */
    private boolean mLowBitAmbient;
    private boolean mBurnInProtection;
    private boolean mAmbient;
    private String eventName;
    HashMap<String, String> copticEvents;

    @Override
    public void onCreate(SurfaceHolder holder) {
      super.onCreate(holder);

      // create multimap to store key and values



      setWatchFaceStyle(new WatchFaceStyle.Builder(CopticGregorianWatchFace.this)
        .setAcceptsTapEvents(true)
        .build());

      mCalendar = Calendar.getInstance();


      Resources resources = CopticGregorianWatchFace.this.getResources();
      mYOffset = resources.getDimension(R.dimen.digital_y_offset);
      mYCopticOffset = resources.getDimension(R.dimen.digital_y_coptic_offset);
      mYGregorianOffset = resources.getDimension(R.dimen.digital_y_gregorian_offset);
      mYCopticEventOffset = resources.getDimension(R.dimen.digital_events_y_offset);

      // Initializes background.
      mBackgroundPaint = new Paint();
      mBackgroundPaint.setColor(
        ContextCompat.getColor(getApplicationContext(), R.color.background));


      // Initializes Watch Face.
      mTextPaint = new Paint();
      mTextPaint.setTypeface(NORMAL_TYPEFACE);
      mTextPaint.setAntiAlias(true);
      Typeface currentTypeFace =   mTextPaint.getTypeface();
      Typeface bold = Typeface.create(currentTypeFace, Typeface.BOLD);
      Typeface normalTypeFace = Typeface.create(currentTypeFace, Typeface.NORMAL);

      mTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, getResources().getDisplayMetrics()));
      mTextPaint.setTypeface(bold);

      mTextPaint.setColor(
        ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

      mCopticTextPaint = new Paint();
      mCopticTextPaint.setTypeface(SMALL_TYPEFACE);
      mCopticTextPaint.setAntiAlias(true);
      mCopticTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics()));
      mCopticTextPaint.setTypeface(normalTypeFace);
      mCopticTextPaint.setColor(
        ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

      mGregorianTextPaint = new Paint();
      mGregorianTextPaint.setTypeface(SMALL_TYPEFACE);
      mGregorianTextPaint.setAntiAlias(true);

      mGregorianTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18, getResources().getDisplayMetrics()));
      mGregorianTextPaint.setTypeface(normalTypeFace);
      mGregorianTextPaint.setColor(
        ContextCompat.getColor(getApplicationContext(), R.color.digital_text));


      mCopticEventTextPaint = new Paint();
      mCopticEventTextPaint.setTypeface(SMALL_TYPEFACE);
      mCopticEventTextPaint.setAntiAlias(true);
      mCopticEventTextPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics()));
      mCopticEventTextPaint.setTypeface(normalTypeFace);
      mCopticEventTextPaint.setColor(
        ContextCompat.getColor(getApplicationContext(), R.color.digital_text));
    }

    @Override
    public void onDestroy() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      super.onDestroy();
    }

    @Override
    public void onVisibilityChanged(boolean visible) {
      super.onVisibilityChanged(visible);

      if (visible) {
        registerReceiver();

        // Update time zone in case it changed while we weren't visible.
        mCalendar.setTimeZone(TimeZone.getDefault());
        invalidate();
      } else {
        unregisterReceiver();
      }
      updateTimer();
    }

    private void registerReceiver() {
      if (mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = true;
      IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
      CopticGregorianWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
    }

    private void unregisterReceiver() {
      if (!mRegisteredTimeZoneReceiver) {
        return;
      }
      mRegisteredTimeZoneReceiver = false;
      CopticGregorianWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
    }

    @Override
    public void onApplyWindowInsets(WindowInsets insets) {
      super.onApplyWindowInsets(insets);

      // Load resources that have alternate values for round watches.
      Resources resources = CopticGregorianWatchFace.this.getResources();
      boolean isRound = insets.isRound();
      mXOffset = resources.getDimension(isRound
        ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

      mXCopticOffset = resources.getDimension(isRound
        ? R.dimen.digital_x_coptic_offset_round : R.dimen.digital_x_coptic_offset);

      mXGregorianOffset = resources.getDimension(isRound
        ? R.dimen.digital_x_gregorian_offset_round : R.dimen.digital_x_gregorian_offset);

      mXEventsOffset = resources.getDimension(isRound
        ? R.dimen.digital_events_x_offset_round : R.dimen.digital_events_x_offset);

//      float textSize = resources.getDimension(isRound
//        ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
//
//      float textCopticSize = resources.getDimension(isRound
//        ? R.dimen.digital_coptic_text_size_round : R.dimen.digital_coptic_text_size);
//
//      float textGreogorianSize = resources.getDimension(isRound
//        ? R.dimen.digital_gregorian_text_size_round : R.dimen.digital_gregorian_text_size);

    }

    @Override
    public void onPropertiesChanged(Bundle properties) {
      super.onPropertiesChanged(properties);
      mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
      mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
    }

    @Override
    public void onTimeTick() {
      super.onTimeTick();
      invalidate();
    }

    @Override
    public void onAmbientModeChanged(boolean inAmbientMode) {
      super.onAmbientModeChanged(inAmbientMode);

      mAmbient = inAmbientMode;
      if (mLowBitAmbient) {
        mTextPaint.setAntiAlias(!inAmbientMode);
        mCopticTextPaint.setAntiAlias(!inAmbientMode);
        mGregorianTextPaint.setAntiAlias(!inAmbientMode);
        mCopticEventTextPaint.setAntiAlias(!inAmbientMode);
      }

      updateTimer();
    }

//    @Override
//    public void onTapCommand(int tapType, int x, int y, long eventTime) {
//      switch (tapType) {
//        case TAP_TYPE_TOUCH:
//          // The user has started touching the screen.
//          break;
//        case TAP_TYPE_TOUCH_CANCEL:
//          // The user has started a different gesture or otherwise cancelled the tap.
//          break;
//        case TAP_TYPE_TAP:
//          // The user has completed the tap gesture.
//          // TODO: Add code to handle the tap gesture.
////          Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
////            .show();
//          break;
//      }
//      invalidate();
//    }

    @Override
    public void onDraw(Canvas canvas, Rect bounds) {
      // Draw the background.
      if (isInAmbientMode()) {
        canvas.drawColor(Color.BLACK);
      } else {
        canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
      }

      // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
      long now = System.currentTimeMillis();
      mCalendar.setTimeInMillis(now);

      try {
        get12HourFormatTime(mCalendar.get(Calendar.HOUR) + ":" + mCalendar.get(Calendar.MINUTE));
        get12HourFormatTime(mCalendar.get(Calendar.HOUR_OF_DAY) + ":" + mCalendar.get(Calendar.MINUTE));
      } catch (ParseException e) {
        e.printStackTrace();
      }

      @SuppressLint("DefaultLocale")
      String text = mAmbient
        ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
        mCalendar.get(Calendar.MINUTE)) + " " + getAMPMFromCalendar(mCalendar.get(Calendar.AM_PM))
        : String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
        mCalendar.get(Calendar.MINUTE)) + " " + getAMPMFromCalendar(mCalendar.get(Calendar.AM_PM));

      canvas.drawText(text, bounds.centerX() - (mTextPaint.measureText(text))/2, mYOffset, mTextPaint);

      String copticDate = coptDate();
      String textCopticDate = (copticDate != null) ? copticDate : "N/A";
      canvas.drawText(textCopticDate, bounds.centerX() - (mCopticTextPaint.measureText(textCopticDate))/2, mYCopticOffset, mCopticTextPaint);

      // For Gregorian date
      String gregorianDate = getGregorianDate();
      String txtGregorianDate = (gregorianDate != null) ? gregorianDate : "N/A";
      canvas.drawText(txtGregorianDate, bounds.centerX() - (mGregorianTextPaint.measureText(txtGregorianDate))/2, mYGregorianOffset, mGregorianTextPaint);

      // For Coptic events
      eventName = checkTheDateIsFestivalInCopticCal();
      Log.d(TAG, "EventName " + eventName);
      if(eventName != null) {
        //String textEventName = (eventName != null) ? eventName : "";
        canvas.drawText(eventName, bounds.centerX() - (mCopticEventTextPaint.measureText(eventName))/2, mYCopticEventOffset, mCopticEventTextPaint);
      }
    }

    private void updateTimer() {
      mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
      if (shouldTimerBeRunning()) {
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
      }
    }

    private boolean shouldTimerBeRunning() {
      return isVisible() && !isInAmbientMode();
    }

    private void handleUpdateTimeMessage() {
      invalidate();
      if (shouldTimerBeRunning()) {
        long timeMs = System.currentTimeMillis();
        long delayMs = INTERACTIVE_UPDATE_RATE_MS
          - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
        mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
      }
    }
  }

  private String coptDate()
  {
    String[] arrCopticMonths = {"Thout","Paope","Hathor","Koiahk","Tobe","Meshir","Paremhotep","Parmoute","Pashons","Paone","Epep","Mesore","Nesi"};
    Chronology coptic = CopticChronology.getInstance();
    LocalDate date = LocalDate.now();
    LocalDate todayCoptic = new LocalDate(date.toDateTimeAtStartOfDay(),
      coptic);
    DateTimeFormatter fmt = DateTimeFormat.forPattern("dd MMMM yyyy");
    String copticD = todayCoptic.toString(fmt);
    String[] copticMonthsArray = copticD.split(Pattern.quote(" "));
    String copticDay = copticMonthsArray[0];
    String copticMonth = arrCopticMonths[Integer.parseInt (copticMonthsArray[1])-1];
    return (copticMonth + " " + copticDay);
  }

  private String getGregorianDate() {
    LocalDate date = LocalDate.now();
    DateTimeFormatter gregorianDatefmt = DateTimeFormat.forPattern("EEE, MMM d");
    String gregorianFormmatedDate = date.toString(gregorianDatefmt);
    Log.d("Gregorian Time ", ""+ gregorianFormmatedDate);
    return gregorianFormmatedDate;
  }

  public String getAMPMFromCalendar(int i) {
    switch (i) {
      case 0:
        return "AM";
      case 1:
        return "PM";
      default:
        return "PM";
    }
  }

  public void get12HourFormatTime(String hours) throws ParseException {
    Log.d("", "Hours " + hours);
    @SuppressLint("SimpleDateFormat")
    SimpleDateFormat parseFormat = new SimpleDateFormat("hh:mm a");
    Date date = parseFormat.parse(hours);
    System.out.println(parseFormat.format(date));
  }

  public String checkTheDateIsFestivalInCopticCal() {
    LocalDate date = LocalDate.now();
    DateTimeFormatter copticDateEvent = DateTimeFormat.forPattern("MM-dd");
    String copticDateEventFormmated = date.toString(copticDateEvent);
    Log.d(TAG, "Formmated " + copticDateEventFormmated);

    HashMap<String, String> copticEvents = new HashMap<String, String>();
    //String value stored along with the key value in hash map
    //Fasts and feasts
    copticEvents.put("01-07", "Feast of the Holy Nativity");
    copticEvents.put("01-14", "Feast of the Circumcision");
    copticEvents.put("01-19", "Feast of the Holy Epiphany");
    copticEvents.put("01-21", "Feast of the Wedding at Cana of Galilee");
    copticEvents.put("03-19","Feast of the Cross");
    // Saint commemorations
    copticEvents.put("12-19","Departure of St. Nicholas of Myra");

    return copticEvents.get(copticDateEventFormmated);
  }

  private float getTextHeight(String text, Paint paint) {
    Rect rect = new Rect();
    paint.getTextBounds(text, 0, text.length(), rect);
    return rect.height();
  }
}
