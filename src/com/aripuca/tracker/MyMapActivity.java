package com.aripuca.tracker;

import java.util.ArrayList;
import java.util.List;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import com.aripuca.tracker.util.TrackPoint;
import com.aripuca.tracker.util.Utils;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class MyMapActivity extends MapActivity {

	private MyApp myApp;

	private MapView mapView;

	private MapController mc;

	private GeoPoint waypoint;
	private TrackPoint startPoint;

	private ArrayList<TrackPoint> points;

	private long trackId;
	/**
	 * Track span values
	 */
	private int minLat = (int) (180 * 1E6);
	private int maxLat = (int) (-180 * 1E6);
	private int minLng = (int) (180 * 1E6);
	private int maxLng = (int) (-180 * 1E6);

	/**
	 * map display mode: SHOW_WAYPOINT or SHOW_TRACK
	 */
	private int mode;

	class MapOverlay extends com.google.android.maps.Overlay {

		@Override
		public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {

			super.draw(canvas, mapView, shadow);

			final Projection projection = mapView.getProjection();

			// display waypoint
			if (mode == Constants.SHOW_WAYPOINT) {

				// ---translate the GeoPoint to screen pixels---
				Point screenPts = new Point();
				projection.toPixels(waypoint, screenPts);

				// ---add the marker---
				Bitmap bmp = BitmapFactory.decodeResource(getResources(),
						android.R.drawable.btn_star_big_on);
				canvas.drawBitmap(bmp, screenPts.x - bmp.getWidth() / 2, screenPts.y - bmp.getHeight() / 2, null);
			}

			// drawing the track on the map
			if (mode == Constants.SHOW_TRACK) {

				drawSegments(projection, canvas);

				/*
				 * Paint paint = new Paint();
				 * paint.setColor(getResources().getColor(R.color.red));
				 * paint.setStrokeWidth(3); paint.setStyle(Paint.Style.STROKE);
				 * paint.setAntiAlias(true);
				 * 
				 * updatePath(projection);
				 * 
				 * canvas.drawPath(path, paint);
				 */

			}

			return true;

		}

/*		private void updatePath_DEPRECATED(Projection projection) {

			if (path == null) {
				path = new Path();
			} else {
				path.reset();
			}

			boolean pathStarted = false;
			Point screenPts = new Point();

			for (int i = 0; i < points.size(); i++) {

				projection.toPixels(points.get(i).getGeoPoint(), screenPts);

				if (!pathStarted) {
					path.moveTo(screenPts.x, screenPts.y);
					pathStarted = true;
				} else {
					path.lineTo(screenPts.x, screenPts.y);
				}

			}

		} */

		/**
		 * Drawing segments in different colors
		 * 
		 * @param projection
		 * @param canvas
		 */
		private void drawSegments(Projection projection, Canvas canvas) {

			Paint paint = new Paint();
			paint.setStrokeWidth(3);
			paint.setStyle(Paint.Style.STROKE);
			paint.setAntiAlias(true);

			boolean pathStarted = false;
			Point screenPts = new Point();

			// 
			int currentSegment = -1;

			Path path = null;
			ArrayList<Path> segmentPath = new ArrayList<Path>();

			for (int i = 0; i < points.size(); i++) {
				
				// start new segment
				if (currentSegment != points.get(i).getSegmentId()) {

					if (i == 0) {

						// first segment created
						path = new Path();

					} else {

						// saving previous segment
						segmentPath.add(path);

						// create new segment
						path = new Path();
					}

					pathStarted = false;
					
					// new current segment
					currentSegment = points.get(i).getSegmentId();

				}

				// converting geopoint coordinates to screen ones
				projection.toPixels(points.get(i).getGeoPoint(), screenPts);

				// populating path object
				if (!pathStarted) {
					path.moveTo(screenPts.x, screenPts.y);
					pathStarted = true;
				} else {
					path.lineTo(screenPts.x, screenPts.y);
				}

				// adding last segment if not empty
				if (i==points.size()-1 && pathStarted) {
					segmentPath.add(path);
				}
				
			}

			// drawing segments in different colors
			for (int i = 0; i < segmentPath.size(); i++) {
				
				if (i % 2 == 0) {
					paint.setColor(getResources().getColor(R.color.red));
				} else {
					paint.setColor(getResources().getColor(R.color.blue));
				}
				
				canvas.drawPath(segmentPath.get(i), paint);
				
			}
			

		}

	}

	/**
	 * Called when the activity is first created
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		myApp = ((MyApp) getApplicationContext());

		setContentView(R.layout.map_view);

		mapView = (MapView) findViewById(R.id.mapview);

		mapView.setBuiltInZoomControls(true);
		// mapView.setSatellite(true);

		// getting extra data passed to the activity
		Bundle b = getIntent().getExtras();

		this.mode = b.getInt("mode");

		// show waypoint
		if (this.mode == Constants.SHOW_WAYPOINT) {

			waypoint = new GeoPoint((int) (b.getDouble("lat") * 1E6), (int) (b.getDouble("lng") * 1E6));

			mapView.getController().setZoom(17);
			mapView.getController().animateTo(waypoint);

		}

		// show track
		if (this.mode == Constants.SHOW_TRACK) {

			this.trackId = b.getLong("track_id");

			this.createPath(this.trackId);

			// zoom to track points span 
			mapView.getController().zoomToSpan(maxLat - minLat, maxLng - minLng);

			// pan to the center of track occupied area
			mapView.getController().animateTo(new GeoPoint((maxLat + minLat) / 2,
															(maxLng + minLng) / 2));

			// get track data
			String sql = "SELECT tracks.*, COUNT(track_points.track_id) AS count FROM tracks, track_points WHERE tracks._id="
					+ this.trackId + " AND tracks._id = track_points.track_id";

			Cursor cursor = myApp.getDatabase().rawQuery(sql, null);
			cursor.moveToFirst();

			String distanceUnit = myApp.getPreferences().getString("distance_units", "km");

			((TextView) findViewById(R.id.distance)).setText(Utils.formatDistance(
					cursor.getInt(cursor.getColumnIndex("distance")), distanceUnit));

			cursor.close();

		}

		// ---Add a location marker---
		MapOverlay mapOverlay = new MapOverlay();

		List<Overlay> listOfOverlays = mapView.getOverlays();
		listOfOverlays.clear();
		listOfOverlays.add(mapOverlay);

		mapView.invalidate();

	}

	private void createPath(long trackId) {

		String sql = "SELECT * FROM track_points WHERE track_id=" + trackId + ";";
		Cursor tpCursor = myApp.getDatabase().rawQuery(sql, null);
		tpCursor.moveToFirst();

		points = new ArrayList<TrackPoint>();

		int lat, lng, segmentId;

		while (tpCursor.isAfterLast() == false) {

			lat = (int) (tpCursor.getFloat(tpCursor.getColumnIndex("lat")) * 1E6);
			lng = (int) (tpCursor.getFloat(tpCursor.getColumnIndex("lng")) * 1E6);
			segmentId = tpCursor.getInt(tpCursor.getColumnIndex("segment_id"));

			TrackPoint gp = new TrackPoint(new GeoPoint(lat, lng), segmentId);

			calculateTrackSpan(lat, lng);

			if (startPoint == null) {
				startPoint = gp;
			}

			points.add(gp);

			tpCursor.moveToNext();
		}

		tpCursor.close();
	}

	private void calculateTrackSpan(int lat, int lng) {

		if (lat < minLat) {
			minLat = lat;
		}
		if (lat > maxLat) {
			maxLat = lat;
		}
		if (lng < minLng) {
			minLng = lng;
		}
		if (lng > maxLng) {
			maxLng = lng;
		}

	}

	@Override
	protected boolean isRouteDisplayed() {
		return false;
	}

	/**
	 * onCreateOptionsMenu handler
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// options menu only in track mode

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.map_menu, menu);

		return true;

	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {

		if (mode == Constants.SHOW_WAYPOINT) {
			(menu.findItem(R.id.showTrack)).setVisible(false);
		} else {
			(menu.findItem(R.id.showWaypoint)).setVisible(false);
		}

		return true;
	}

	/**
	 * Process main activity menu
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		// Handle item selection
		switch (item.getItemId()) {

			case R.id.showWaypoint:

				mapView.getController().setZoom(16);
				mapView.getController().animateTo(waypoint);

				return true;

			case R.id.showTrack:

				// zoom to track points span 
				mapView.getController().zoomToSpan(maxLat - minLat, maxLng - minLng);

				// pan to the center of track occupied area
				mapView.getController().animateTo(new GeoPoint((maxLat + minLat) / 2,
																(maxLng + minLng) / 2));

				return true;

			default:

				return super.onOptionsItemSelected(item);

		}

	}

}
