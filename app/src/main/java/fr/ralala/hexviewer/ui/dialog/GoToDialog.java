package fr.ralala.hexviewer.ui.dialog;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import fr.ralala.hexviewer.ApplicationCtx;
import fr.ralala.hexviewer.R;
import fr.ralala.hexviewer.models.LineEntries;
import fr.ralala.hexviewer.ui.activities.MainActivity;
import fr.ralala.hexviewer.ui.adapters.SearchableListArrayAdapter;
import fr.ralala.hexviewer.ui.utils.UIHelper;

/**
 * ******************************************************************************
 * <p><b>Project HexViewer</b><br/>
 * Management of the dialog box used for the "Go to line" action
 * </p>
 *
 * @author Keidan
 * <p>
 * License: GPLv3
 * </p>
 * ******************************************************************************
 */
public class GoToDialog implements View.OnClickListener {
  private static final Pattern HEXADECIMAL_PATTERN = Pattern.compile("\\p{XDigit}+");
  private String mPreviousGoToValueAddress = "0";
  private String mPreviousGoToValueLineHex = "0";
  private String mPreviousGoToValueLinePlain = "0";
  private AlertDialog mDialog;
  private EditText mEt;
  private TextInputLayout mLayout;
  private final MainActivity mActivity;
  private int mPosition = 0;
  private Mode mMode;
  private String mTitle;

  public enum Mode {
    ADDRESS,
    LINE_HEX,
    LINE_PLAIN
  }

  public GoToDialog(MainActivity activity) {
    mActivity = activity;
  }

  /**
   * Displays the dialog
   */
  @SuppressLint("InflateParams")
  public AlertDialog show(Mode mode) {
    AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
    builder.setCancelable(true)
        .setTitle(R.string.action_go_to_address)
        .setPositiveButton(android.R.string.ok, null)
        .setNegativeButton(android.R.string.cancel, (dialog, whichButton) -> {
        });
    LayoutInflater factory = LayoutInflater.from(mActivity);
    builder.setView(factory.inflate(R.layout.content_dialog_go_to, null));
    mDialog = builder.create();
    if (mDialog.isShowing())
      mDialog.dismiss();
    mMode = mode;
    mDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN |
        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    String title1, title2;
    if (mode == Mode.ADDRESS) {
      title1 = mActivity.getString(R.string.action_go_to_address);
      title2 = mActivity.getString(R.string.hexadecimal);
    } else {
      title1 = mActivity.getString(R.string.action_go_to_line);
      title2 = mActivity.getString(R.string.decimal);
    }
    mTitle = title1 + " (" + title2 + ")";
    mDialog.setTitle(mTitle);
    mDialog.show();
    mEt = mDialog.findViewById(R.id.tieValue);
    mLayout = mDialog.findViewById(R.id.tilValue);

    if (mEt != null && mLayout != null) {
      if (mode == Mode.ADDRESS) {
        mEt.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        mEt.setText(mPreviousGoToValueAddress);
      } else {
        mEt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        mEt.setText(mode == Mode.LINE_HEX ? mPreviousGoToValueLineHex : mPreviousGoToValueLinePlain);
      }
      if (mEt.getText().length() == 0)
        mLayout.setError(" "); /* only for the color */
      mEt.addTextChangedListener(UIHelper.getResetLayoutWatcher(mLayout, true));
      mEt.setSelection(0, mEt.getText().length());

      mEt.requestFocus();
    }
    mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this);
    mDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener((v) -> mDialog.dismiss());
    return mDialog;
  }

  /**
   * Called when a view has been clicked.
   *
   * @param v The view that was clicked.
   */
  @Override
  public void onClick(View v) {
    if (mEt == null) {
      if (mLayout != null)
        mLayout.setError(" "); /* only for the color */
      return;
    }
    String text = mEt.getText().toString();
    if (text.isEmpty())
      return;

    ListView lv = (mMode == Mode.ADDRESS || mMode == Mode.LINE_HEX) ?
        mActivity.getPayloadHex().getListView() : mActivity.getPayloadPlain().getListView();
    SearchableListArrayAdapter adapter = ((SearchableListArrayAdapter) lv.getAdapter());
    int position;
    if (mMode == Mode.ADDRESS) {
      if (validatePosition(text, adapter.getEntries().getItemsCount() - 1))
        return;
      position = evaluatePosition(adapter, adapter.getCount());
      if (position == -1) {
        displayError(mActivity.getString(R.string.error_not_available));
        return;
      }
    } else {
      if (validatePosition(text, lv.getAdapter().getCount() - 1))
        return;
      position = mPosition;
    }
    lv.post(() -> {
      lv.setSelectionFromTop(position, 0);
      lv.post(() -> blinkBackground(position));
    });
    if (mMode == Mode.LINE_PLAIN)
      mPreviousGoToValueLinePlain = text;
    else if (mMode == Mode.LINE_HEX)
      mPreviousGoToValueLineHex = text;
    else
      mPreviousGoToValueAddress = text;
    mDialog.dismiss();
    mActivity.setOrphanDialog(null);
  }

  /**
   * Evaluates the position in the list.
   *
   * @param adapter SearchableListArrayAdapter
   * @param count   Nb of elements.
   * @return The position.
   */
  private int evaluatePosition(SearchableListArrayAdapter adapter, int count) {
    LineEntries entries = adapter.getEntries();
    int position;
    if (count <= 500) {
      position = getAddressPosition(entries, 0, count);
    } else {
      int middle = count / 2;
      if (mPosition == middle)
        position = middle;
      else if (mPosition < middle) {
        position = getAddressPosition(entries, 0, middle);
      } else {
        position = getAddressPosition(entries, middle, count);
      }
    }
    return position;
  }

  /**
   * Gets the position of the address in the list.
   *
   * @param entries LineEntries
   * @param start   Start index.
   * @param end     End index.
   * @return The position.
   */
  private int getAddressPosition(LineEntries entries, int start, int end) {
    int position = -1;
    for (int i = start; i < end; i++) {
      if (entries.getItemIndex(i) == mPosition) {
        position = i;
        break;
      }
    }
    return position;
  }

  /**
   * Validates the position of the cursor.
   *
   * @param text     The input value.
   * @param maxLines The maximum number of lines.
   * @return true on error.
   */
  private boolean validatePosition(String text, int maxLines) {
    int position;
    int max = maxLines;
    String s_max;
    if (mMode == Mode.ADDRESS) {
      if (!HEXADECIMAL_PATTERN.matcher(text).matches()) {
        UIHelper.shakeError(mEt, null);
        if (mLayout != null)
          mLayout.setError(" "); /* only for the color */
        return true;
      }
      int nbBytesPerLines = ApplicationCtx.getInstance().getNbBytesPerLine();
      try {
        position = Integer.parseInt(text, 16) / nbBytesPerLines;
      } catch (Exception e) {
        Log.e(getClass().getSimpleName(), "Exception: " + e.getMessage(), e);
        position = -1;
      }
      final int maxLength = String.format("%X", max * nbBytesPerLines).length();
      s_max = String.format("%0" + maxLength + "X", (max * nbBytesPerLines) + nbBytesPerLines - 1);
    } else {
      try {
        position = Integer.parseInt(text) - 1;
        if (position < 0)
          position = 0;
      } catch (Exception e) {
        Log.e(getClass().getSimpleName(), "Exception: " + e.getMessage(), e);
        position = -1;
      }
      s_max = String.valueOf(max + 1);
      if (position <= max)
        max++;
    }
    if (position == -1 || position > max) {
      String err = String.format(mActivity.getString(R.string.error_cant_exceed_xxx), s_max);
      displayError(err);
      return true;
    }
    mPosition = Math.max(0, position);
    return false;
  }

  /**
   * Displays an error message
   *
   * @param err The message.
   */
  private void displayError(String err) {
    if (mLayout != null) {
      UIHelper.shakeError(mEt, null);
      mLayout.setError(err);
    } else {
      mDialog.dismiss();
      UIHelper.showErrorDialog(mActivity, mTitle, err);
    }
  }

  /**
   * Blinks the background of the selected view
   */
  private void blinkBackground(int position) {
    ListView lv = (mMode == Mode.ADDRESS || mMode == Mode.LINE_HEX) ?
        mActivity.getPayloadHex().getListView() : mActivity.getPayloadPlain().getListView();
    View v = UIHelper.getViewByPosition(position, lv);
    lv.setOnScrollListener(null);
    int windowBackground = ContextCompat.getColor(mActivity, R.color.windowBackground);
    int colorAccent = ContextCompat.getColor(mActivity, R.color.colorAccent);
    ObjectAnimator anim = ObjectAnimator.ofInt(v, "backgroundColor",
        windowBackground, colorAccent, windowBackground);
    anim.setDuration(1000);
    anim.setEvaluator(new ArgbEvaluator());
    anim.setRepeatMode(ValueAnimator.REVERSE);
    anim.setRepeatCount(3);
    anim.start();
  }
}
