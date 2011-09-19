package org.wheelmap.android.ui;

import org.wheelmap.android.R;
import org.wheelmap.android.manager.MyLocationManager;
import org.wheelmap.android.model.POIHelper;
import org.wheelmap.android.model.POIsCursorWrapper;
import org.wheelmap.android.model.POIsListCursorAdapter;
import org.wheelmap.android.model.Wheelmap;
import org.wheelmap.android.service.SyncService;
import org.wheelmap.android.ui.mapsforge.POIsMapsforgeActivity;
import org.wheelmap.android.utils.DetachableResultReceiver;
import org.wheelmap.android.utils.GeocoordinatesMath;
import org.wheelmap.android.utils.GeocoordinatesMath.DistanceUnit;

import wheelmap.org.BoundingBox.Wgs84GeoCoordinates;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

public class POIsListActivity extends ListActivity implements
		DetachableResultReceiver.Receiver {

	private final static String TAG = "poislist";
	private MyLocationManager mLocationManager;
	private Location mLocation;

	private final static String PREF_KEY_LIST_DISTANCE = "listDistance";

	private State mState;
	private float mDistance;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list);
		
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

		mLocationManager = MyLocationManager.get(mState.mReceiver, true);
		mLocation = mLocationManager.getLastLocation();
		mDistance = getDistanceFromPreferences();
		
		// Run query
		runQuery();
		getListView().setTextFilterEnabled(true);
		

	}

	@Override
	public void onPause() {
		super.onPause();
		mLocationManager.release(mState.mReceiver);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mLocationManager.register(mState.mReceiver, true);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();		
	}

	public void runQuery() {
		long startTime = System.currentTimeMillis();		
		Uri uri = Wheelmap.POIs.CONTENT_URI_POI_SORTED;

		Cursor cursor = managedQuery(uri, Wheelmap.POIs.PROJECTION, null,
				createWhereValues(), "");
		Cursor wrappingCursor = createCursorWrapper(cursor);
		startManagingCursor(wrappingCursor);
		
		if ( wrappingCursor.getCount() == 0 ) {
			onRefreshClick(null);
		}

		POIsListCursorAdapter adapter = new POIsListCursorAdapter(this,
				wrappingCursor);
		setListAdapter(adapter);
		getListView().setSelection( mState.mListPosition);
		
		long duration = System.currentTimeMillis() - startTime;
		Log.d ( TAG, "runQuery duration = " + duration + "ms" );
	}

	public String[] createWhereValues() {
		String[] lonlat = new String[] {
				String.valueOf(mLocation.getLongitude()),
				String.valueOf(mLocation.getLatitude()) };
		return lonlat;
	}

	public Cursor createCursorWrapper(Cursor cursor) {
		Wgs84GeoCoordinates wgsLocation = new Wgs84GeoCoordinates(
				mLocation.getLongitude(), mLocation.getLatitude());
		return new POIsCursorWrapper(cursor, wgsLocation);
	}

	public float getDistanceFromPreferences() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		String prefDist = prefs.getString(PREF_KEY_LIST_DISTANCE, "0.5");
		return Float.valueOf(prefDist);
	}

	public int getSelectionFromPreferences() {
		String[] values = getResources().getStringArray(
				R.array.distance_array_values);
		int i;
		for (i = 0; i < values.length; i++) {
			if (Float.valueOf(values[i]) == mDistance)
				return i;
		}
		return 0;
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		// Clear any strong references to this Activity, we'll reattach to
		// handle events on the other side.
		mState.mReceiver.clearReceiver();
		return mState;
	}

	public void onHomeClick(View v) {
		final Intent intent = new Intent(this, WheelmapHomeActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		this.startActivity(intent);
	}
	
	public void onFilterClick(View v) {
		final Resources res = getResources();
		final CharSequence[] items = res
				.getStringArray(R.array.distance_array);

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		int textRes = GeocoordinatesMath.DISTANCE_UNIT == DistanceUnit.KILOMETRES ? R.string.spinner_description_distance_km
				: R.string.spinner_description_distance_miles;
		builder.setTitle(textRes);

		builder.setSingleChoiceItems(items, getSelectionFromPreferences(),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						mDistance = Float.valueOf( res.getStringArray( R.array.distance_array_values)[item]);
						onRefreshClick(null);
						dialog.dismiss();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	public void onMapClick(View v) {
		mState.mListPosition = getListView().getSelectedItemPosition();
		Intent intent = new Intent(this, POIsMapsforgeActivity.class);
		intent.putExtra(POIsMapsforgeActivity.EXTRA_NO_RETRIEVAL, false);
		startActivity(intent);
	}
	
    public void onNewPOIClick(View v) {
    	
    	// create new POI and start editing
        ContentValues cv = new ContentValues();
        cv.put(Wheelmap.POIs.NAME,  getString(R.string.new_default_name));
        cv.put(Wheelmap.POIs.COORD_LAT,  Math.ceil(mLocation.getLatitude() * 1E6));
        cv.put(Wheelmap.POIs.COORD_LON,  Math.ceil(mLocation.getLongitude() * 1E6));
        cv.put(Wheelmap.POIs.CATEGORY_ID, 1);
        cv.put(Wheelmap.POIs.NODETYPE_ID, 1);
        
        Uri new_pois = getContentResolver().insert(Wheelmap.POIs.CONTENT_URI, cv);
        
        // edit activity
        Log.i(TAG, new_pois.toString());
        long poiId = Long.parseLong(new_pois.getLastPathSegment());
    	Intent i = new Intent(POIsListActivity.this, POIDetailActivityEditable.class);
    	i.putExtra(Wheelmap.POIs.EXTRAS_POI_ID, poiId);
    	startActivity(i);
        
		
	}
	

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		mState.mListPosition = l.getSelectedItemPosition();

		Cursor cursor = (Cursor) l.getAdapter().getItem(position);

		long poiId = POIHelper.getId(cursor);
		Intent i = new Intent(POIsListActivity.this, POIDetailActivity.class);
		i.putExtra(Wheelmap.POIs.EXTRAS_POI_ID, poiId);
		startActivity(i);
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
			Toast.makeText(POIsListActivity.this, errorText, Toast.LENGTH_LONG)
					.show();
			break;
		}
		case MyLocationManager.WHAT_LOCATION_MANAGER_UPDATE: {
			mLocation = (Location) resultData
					.getParcelable(MyLocationManager.EXTRA_LOCATION_MANAGER_LOCATION);
			break;
		}
		}
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
		public int mListPosition = 0;

		private State() {
			mReceiver = new DetachableResultReceiver(new Handler());
		}
	}

	public void onRefreshClick(View v) {
		// start service for sync
		final Intent intent = new Intent(Intent.ACTION_SYNC, null,
				POIsListActivity.this, SyncService.class);
		intent.putExtra(SyncService.EXTRA_WHAT, SyncService.WHAT_RETRIEVE_NODES );
		intent.putExtra(SyncService.EXTRA_STATUS_RECEIVER, mState.mReceiver);
		intent.putExtra(SyncService.EXTRA_LOCATION, mLocation);
		intent.putExtra(SyncService.EXTRA_DISTANCE_LIMIT, mDistance);	
		startService(intent);
	}
}