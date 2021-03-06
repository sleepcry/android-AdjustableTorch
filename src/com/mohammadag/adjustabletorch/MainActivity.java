package com.mohammadag.adjustabletorch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.google.ads.AdRequest;
import com.google.ads.AdView;
import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.execution.Shell;

public class MainActivity extends Activity {
	private SeekBar mBrightnessSlider = null;
	private SharedPreferences mPreferences = null;

	private boolean mInvertValues = false;

	private Shell mShell = null;

	private enum Errors {
		NO_SYSFS_FILE,
		NO_ROOT,
		NO_ROOT_ACCESS,
		NO_BUSYBOX, // Just in case we need BusyBox for some reason later on.
		NO_BRAIN
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		getViews();
		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		toggleAds(mPreferences.getBoolean(Constants.SETTINGS_ENABLE_ADS, true));
		mInvertValues = mPreferences.getBoolean(Constants.SETTINGS_INVERT_VALUES, false);

		registerReceiver(mFlashValueUpdatedReceiver,
				new IntentFilter(Constants.FLASH_VALUE_UPDATED_BROADCAST_NAME));

		if (mBrightnessSlider != null) {
			mBrightnessSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					int newValue = progress;

					if (progress != 0 && mInvertValues) {
						/* Some devices like the Galaxy S3 have inverted values for some reason
						 * For example, 15 == 1, 14 == 2
						 */
						if (progress == mBrightnessSlider.getMax()) {
							newValue = 1;
						} else {
							newValue = mBrightnessSlider.getMax() - progress;
						}
					}

					updateTorchValue(newValue);
					mPreferences.edit().putInt(Constants.SETTINGS_FLASH_KEY, progress).commit();
				}
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) { }
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) { }
			});

			mBrightnessSlider.setProgress(mPreferences.getInt(Constants.SETTINGS_FLASH_KEY, 0));
		}

		if (Utils.getSysFsFile() != null) {
			checkForRoot();
		} else {
			complainAbout(Errors.NO_SYSFS_FILE);
		}
	}

	BroadcastReceiver mFlashValueUpdatedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			int newValue = intent.getIntExtra(Constants.KEY_NEW_VALUE, 0);
			if (mInvertValues && newValue != 0) {
				if (newValue == mBrightnessSlider.getMax())
					newValue = 0;
				else
					newValue = mBrightnessSlider.getMax() - newValue;
			}

			mBrightnessSlider.setProgress(newValue);
		}
	};

	private void getViews() {
		mBrightnessSlider = (SeekBar) findViewById(R.id.flashlightBrightnessSlider);
	}

	private void createDialog(int titleId, int messageId, int positiveTextId, DialogInterface.OnClickListener positiveAction,
			int negativeTextId, DialogInterface.OnClickListener negativeAction) {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this)
		.setTitle(titleId)
		.setMessage(messageId);

		if (positiveTextId != 0 && positiveAction != null)
			alertDialog.setPositiveButton(positiveTextId, positiveAction);

		if (negativeTextId != 0 && negativeAction != null)
			alertDialog.setNegativeButton(negativeTextId, negativeAction);

		alertDialog.setCancelable(false);
		alertDialog.show();
	}

	private void complainAbout(Errors error) {
		OnClickListener quitListener = new OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
				finish();
			}
		};

		switch (error) {
		case NO_SYSFS_FILE:
			createDialog(R.string.error, R.string.no_sysfs_file, 0, null, R.string.uninstall_and_quit, new OnClickListener() {		
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();

					Uri packageUri = Uri.parse(String.format("package:%s", Constants.PACKAGE_NAME));
					Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
					startActivity(uninstallIntent);

					finish();
				}
			});
			break;
		case NO_ROOT:
			createDialog(R.string.error, R.string.device_not_rooted, 0, null, R.string.quit, quitListener);
			break;
		case NO_ROOT_ACCESS:
			createDialog(R.string.error, R.string.root_access_not_given, 0, null, R.string.quit, quitListener);
			break;
		case NO_BUSYBOX:
			break;
		case NO_BRAIN:
			// Not yet implemented, need real life example first.
			break;
		default:
			break;
		}
	}

	private void checkForRoot() {
		if (!RootTools.isRootAvailable()) {
			complainAbout(Errors.NO_ROOT);
		} else {
			if (!RootTools.isAccessGiven()) {
				complainAbout(Errors.NO_ROOT_ACCESS);
			} else {
				openShell();
				updateTorchValue(mBrightnessSlider.getProgress());
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);

		MenuItem item = menu.findItem(R.id.enable_ads);
		item.setChecked(mPreferences.getBoolean("enable_ads", true));

		MenuItem invertValuesItem = menu.findItem(R.id.invert_values);
		invertValuesItem.setChecked(mPreferences.getBoolean("invert_values", false));

		return true;
	}

	private boolean updateTorchValue(int value) {
		showOngoingNotification((value > 0));
		return Utils.updateTorchValue(value, mShell);
	}

	private void openShell() {
		try {
			mShell = RootTools.getShell(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void showOngoingNotification(boolean show) {
		Utils.showOngoingNotification(this, show);
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(mFlashValueUpdatedReceiver);
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.action_about:
			showAbout();
			return true;
		case R.id.action_donate:
			Intent intent = new Intent(Intent.ACTION_VIEW, 
					Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=DRYFYGTC7Z8V2"));
			startActivity(intent);
			return true;
		case R.id.enable_ads:
			toggleAds(!item.isChecked());
			mPreferences.edit().putBoolean(Constants.SETTINGS_ENABLE_ADS, !item.isChecked()).commit();
			item.setChecked(!item.isChecked());
			return true;
		case R.id.invert_values:
			mBrightnessSlider.setProgress(0);
			updateTorchValue(0);
			mPreferences.edit().putBoolean(Constants.SETTINGS_INVERT_VALUES, !item.isChecked()).commit();
			item.setChecked(!item.isChecked());
			mInvertValues = item.isChecked();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		mPreferences.edit().putInt(Constants.SETTINGS_FLASH_KEY, mBrightnessSlider.getProgress()).commit();
	}

	public void showAbout() {
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this)
		.setTitle(R.string.about_dialog_title)
		.setMessage(R.string.about_text);

		alertDialog.show();
	}

	private void toggleAds(boolean enable) {
		if (enable) {
			showAd();
		} else {
			hideAd();
		}
	}

	private void showAd() {
		final AdView adLayout = (AdView) findViewById(R.id.adView);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adLayout.setEnabled(true);
				adLayout.setVisibility(View.VISIBLE);
				adLayout.loadAd(new AdRequest());
			}
		});
	}

	private void hideAd() {
		final AdView adLayout = (AdView) findViewById(R.id.adView);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adLayout.setEnabled(false);
				adLayout.setVisibility(View.GONE);
			}
		});
	}
}