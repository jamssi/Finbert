package paksu.finbert;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import paksu.finbert.DilbertImageSwitcher.Direction;
import paksu.finbert.DilbertImageSwitcher.OnFlingListener;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.ViewSwitcher.ViewFactory;

import com.google.gson.JsonParseException;

public final class StripBrowserActivity extends Activity implements ViewFactory {
	private DilbertImageSwitcher imageSwitcher;
	private ImageView nextButton;
	private ImageView prevButton;
	private TextView commentCount;

	private final DilbertReader dilbertReader = DilbertReader.getInstance();
	private final CommentHandler commentHandler = new CommentHandler();
	private final ImageCache imagecache = ImageCache.getInstance();

	private final Map<DilbertDate, Boolean> availabilityCache = new HashMap<DilbertDate, Boolean>();
	private DilbertDate selectedDate = DilbertDate.newest();

	private boolean checkAvailabilityTaskRunning = false;

	private class CheckAvailabilityTask extends AsyncTask<DilbertDate, Void, Void> {
		@Override
		protected void onPreExecute() {
			checkAvailabilityTaskRunning = true;
		}

		@Override
		protected Void doInBackground(DilbertDate... params) {
			for (DilbertDate date : params) {
				if (!availabilityCache.containsKey(date)) {
					Boolean dilbertIsAvailable = dilbertReader.isDilbertAvailableForDate(date);
					availabilityCache.put(date, dilbertIsAvailable);
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			for (Entry<DilbertDate, Boolean> entry : availabilityCache.entrySet()) {
				if (selectedDate.next().equals(entry.getKey())) {
					nextButton.setEnabled(entry.getValue());
				} else if (selectedDate.previous().equals(entry.getKey())) {
					prevButton.setEnabled(entry.getValue());
				}
			}

			checkAvailabilityTaskRunning = false;
		}
	}

	private class DownloadFinbertTask extends AsyncTask<DilbertDate, Void, Bitmap> {
		private DilbertDate date;

		@Override
		protected Bitmap doInBackground(DilbertDate... params) {
			try {
				date = params[0];
				return dilbertReader.downloadFinbertForDate(date);
			} catch (NetworkException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			if (selectedDate.equals(date)) {
				fadeToImage(result);
			}
		}
	}

	private class GetCommentCountTask extends AsyncTask<DilbertDate, Void, Integer> {
		private DilbertDate date;

		@Override
		protected Integer doInBackground(DilbertDate... params) {
			date = params[0];
			try {
				return commentHandler.getCommentCount(date);
			} catch (JsonParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NetworkException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (date.equals(selectedDate)) {
				commentCount.setText(result.toString());
			}
		}
	}

	private final OnFlingListener imageSwitcherOnFlingListener = new OnFlingListener() {
		@Override
		public void onFling(Direction direction) {
			if (direction == Direction.LEFT) {
				changeToNextDay();
			} else if (direction == Direction.RIGHT) {
				changeToPreviousDay();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.strip_browser);

		imageSwitcher = (DilbertImageSwitcher) findViewById(R.id.dilbert_image_switcher);
		imageSwitcher.setFactory(this);
		imageSwitcher.setOnFlingListener(imageSwitcherOnFlingListener);

		nextButton = (ImageView) findViewById(R.id.next);
		prevButton = (ImageView) findViewById(R.id.previous);

		commentCount = (TextView) findViewById(R.id.comments_count);

		setFonts();
		fadeToTemporary();
		downloadAndFadeTo(selectedDate);
		fetchCommentCount();
		checkAvailabilityForDates(selectedDate.previous(), selectedDate.next());
	}

	private void setFonts() {
		Typeface customTypeFace = Typeface.createFromAsset(getAssets(), "default_font.ttf");
		((TextView) findViewById(R.id.share_text)).setTypeface(customTypeFace);
		((TextView) findViewById(R.id.click_to_comment)).setTypeface(customTypeFace);
		((TextView) findViewById(R.id.comments)).setTypeface(customTypeFace);
		((TextView) findViewById(R.id.comments_count)).setTypeface(customTypeFace);
	}

	public void buttonListener(View v) {
		if (v.getId() == R.id.comments_bubble) {
			launchCommentsActivityForCurrentDate();
			return;
		}

		if (checkAvailabilityTaskRunning) {
			return;
		}

		if (v.getId() == R.id.next) {
			changeToNextDay();
		} else if (v.getId() == R.id.previous) {
			changeToPreviousDay();
		}
	}

	private void fetchCommentCount() {
		new GetCommentCountTask().execute(selectedDate);
	}

	private void changeToNextDay() {
		selectedDate = selectedDate.next();
		fetchFinbert(selectedDate, Direction.RIGHT);
		fetchCommentCount();
		checkAvailabilityForDates(selectedDate.next());
	}

	private void changeToPreviousDay() {
		selectedDate = selectedDate.previous();
		fetchFinbert(selectedDate, Direction.LEFT);
		fetchCommentCount();
		checkAvailabilityForDates(selectedDate.previous());
	}

	private void checkAvailabilityForDates(DilbertDate... dates) {
		new CheckAvailabilityTask().execute(dates);
	}

	private void updateTitle() {
		// TODO: jotai järkevämpää ?
		setTitle("Finbert - " + selectedDate.getYear() + "-" + selectedDate.getMonth() + "-" + selectedDate.getDay());
	}

	private void fetchFinbert(DilbertDate date, Direction direction) {
		updateTitle();
		if (imagecache.isImageCachedForDate(selectedDate)) {
			slideToImage(imagecache.get(date), direction);
		} else {
			slideToTemporary(direction);
			downloadAndFadeTo(date);
		}
	}

	private void downloadAndFadeTo(DilbertDate date) {
		new DownloadFinbertTask().execute(date);
	}

	private void slideToImage(Bitmap bitmap, Direction direction) {
		imageSwitcher.slideToDrawable(new BitmapDrawable(bitmap), ScaleType.FIT_CENTER, direction);
	}

	private void fadeToImage(Bitmap bitmap) {
		imageSwitcher.fadeToDrawable(new BitmapDrawable(bitmap), ScaleType.FIT_CENTER);
	}

	private void fadeToTemporary() {
		imageSwitcher.fadeToDrawable(temporaryDrawable(), ScaleType.CENTER_INSIDE);
	}

	private void slideToTemporary(Direction direction) {
		imageSwitcher.slideToDrawable(temporaryDrawable(), ScaleType.CENTER_INSIDE, direction);
	}

	private Drawable temporaryDrawable() {
		return getResources().getDrawable(R.drawable.loading_face);
	}

	private void launchCommentsActivityForCurrentDate() {
		Intent intent = new Intent(this, CommentsActivity.class);
		intent.putExtra(CommentsActivity.EXTRAS_YEAR, selectedDate.getYear());
		intent.putExtra(CommentsActivity.EXTRAS_MONTH, selectedDate.getMonth());
		intent.putExtra(CommentsActivity.EXTRAS_DAY, selectedDate.getDay());
		startActivity(intent);
	}

	/**
	 * Generates views for {@link DilbertImageSwitcher}
	 */
	@Override
	public View makeView() {
		ImageView view = new ImageView(this);
		view.setScaleType(ScaleType.FIT_CENTER);
		view.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		return view;
	}
}