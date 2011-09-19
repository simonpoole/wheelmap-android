package org.wheelmap.android.ui;

import org.wheelmap.android.R;
import org.wheelmap.android.manager.SupportManager;
import org.wheelmap.android.service.SyncService;
import org.wheelmap.android.ui.map.POIsMapActivity;
import org.wheelmap.android.ui.mapsforge.POIsMapsforgeActivity;
import org.wheelmap.android.utils.DetachableResultReceiver;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

public class WheelmapHomeActivity extends Activity implements
		DetachableResultReceiver.Receiver {

	/** State held between configuration changes. */
	private State mState;
	private ProgressDialog mCreationDialog;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);

		mState = (State) getLastNonConfigurationInstance();
		final boolean previousState = mState != null;

		if (previousState) {
			// Start listening for SyncService updates again
			mState.mReceiver.setReceiver(this);
			updateRefreshStatus();

		} else {
			mState = new State();
			mState.mReceiver.setReceiver(this);
		}

		SupportManager.get().registerReceiver(this);

	}

	public void onListClick(View v) {
		startActivity(new Intent(this, POIsListActivity.class));
	}

	public void onSettingsClick(View v) {
		startActivity(new Intent(this, SettingsActivity.class));
	}

	public void onRefreshClick(View v) {
		// trigger off background sync
		final Intent intent = new Intent(Intent.ACTION_SYNC, null,
				WheelmapHomeActivity.this, SyncService.class);
		intent.putExtra(SyncService.EXTRA_STATUS_RECEIVER,
				WheelmapHomeActivity.this.mState.mReceiver);
		startService(intent);
	}

	/** Handle "map" action. */
	public void onMapClick(View v) {
		startActivity(new Intent(this, POIsMapActivity.class));
	}

	public void onMapsforgeClick(View v) {
		startActivity(new Intent(this, POIsMapsforgeActivity.class));
	}

	private void updateRefreshStatus() {
		findViewById(R.id.btn_title_refresh).setVisibility(
				mState.mSyncing ? View.GONE : View.VISIBLE);
		findViewById(R.id.title_refresh_progress).setVisibility(
				mState.mSyncing ? View.VISIBLE : View.GONE);
	}

	/** {@inheritDoc} */
	public void onReceiveResult(int resultCode, Bundle resultData) {
		switch (resultCode) {

		case SyncService.STATUS_RUNNING: {
			mState.mSyncing = true;
			updateRefreshStatus();
			break;
		}
		case SyncService.STATUS_FINISHED: {
			mState.mSyncing = false;
			updateRefreshStatus();
			break;
		}
		case SyncService.STATUS_ERROR: {
			// Error happened down in SyncService, show as toast.
			mState.mSyncing = false;
			updateRefreshStatus();
			final String errorText = getString(R.string.toast_sync_error,
					resultData.getString(Intent.EXTRA_TEXT));
			Toast.makeText(WheelmapHomeActivity.this, errorText,
					Toast.LENGTH_LONG).show();
			break;
		}
		case SupportManager.CREATION_RUNNING: {
			mCreationDialog = ProgressDialog.show(this,
					this.getText(R.string.title_please_wait),
					this.getText(R.string.description_loading_please_wait),
					true);
			break;
		}
		case SupportManager.CREATION_FINISHED: {
			if (mCreationDialog != null) {
				mCreationDialog.dismiss();
			}
			break;
		}
		case SupportManager.CREATION_ERROR: {
			if (mCreationDialog != null) {
				mCreationDialog.dismiss();
			}
			final String errorText = getString(R.string.toast_sync_error,
					resultData.getString(Intent.EXTRA_TEXT));
			Toast.makeText(WheelmapHomeActivity.this, errorText,
					Toast.LENGTH_LONG).show();
			break;
		}
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		mState.mReceiver.clearReceiver();
		return mState;
	}

	/**
	 * State specific to {@link HomeActivity} that is held between configuration
	 * changes. Any strong {@link Activity} references <strong>must</strong> be
	 * cleared before {@link #onRetainNonConfigurationInstance()}, and this
	 * class should remain {@code static class}.
	 */
	private static class State {
		public DetachableResultReceiver mReceiver;
		public boolean mSyncing = false;

		private State() {
			mReceiver = new DetachableResultReceiver(new Handler());
		}
	}
}