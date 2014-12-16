package edu.ucsd.rendezvous;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * View used to display a static google map with some information beside it.
 *
 */
public class RendezvousView extends FrameLayout{

    private TextView info;
    private ImageView map, maptmp;

    private String infoStr;

    private String map_url;
    private String markers_url;

    /** About 1 FPS, update markers on the map once a second */
    static final long DELAY_MILLIS_STABLE = 1000;

    /** About 24 FPS, at the beginning */
//    static final long DELAY_MILLIS_BEGIN = 40;

    private long DELAY_MILLIS;

    private static final String STATIC_MAP_URL_TEMPLATE =
            "https://maps.googleapis.com/maps/api/staticmap"
                    + "?center=%.5f,%.5f"
                    + "&zoom=%d"
                    + "&sensor=true"
                    + "&size=330x360"
                    + "&scale=1"
                    + "&style=element:geometry%%7Cinvert_lightness:true"
                    + "&style=feature:landscape.natural.terrain%%7Celement:geometry%%7Cvisibility:on"
                    + "&style=feature:landscape%%7Celement:geometry.fill%%7Ccolor:0x303030"
                    + "&style=feature:poi%%7Celement:geometry.fill%%7Ccolor:0x404040"
                    + "&style=feature:poi.park%%7Celement:geometry.fill%%7Ccolor:0x0a330a"
                    + "&style=feature:water%%7Celement:geometry%%7Ccolor:0x00003a"
                    + "&style=feature:transit%%7Celement:geometry%%7Cvisibility:on%%7Ccolor:0x101010"
                    + "&style=feature:road%%7Celement:geometry.stroke%%7Cvisibility:on"
                    + "&style=feature:road.local%%7Celement:geometry.fill%%7Ccolor:0x606060"
                    + "&style=feature:road.arterial%%7Celement:geometry.fill%%7Ccolor:0x888888";

    private static final String STATIC_MAP_URL_MARKER_TEMPLATE =
                    "&markers=size:large%%7Ccolor:red%%7Clabel:%c%%7C%.5f,%.5f";

    private static final String FRIEND_NAME_AND_DISTANCE_TEMPLATE =
                    "%c : %s , %.1f mi\n";

    private class friendInfo
    {
        public double latitude, longitude, distance;
        String name;
        friendInfo(double la, double lo, double d, String fname)
        {
            latitude = la;
            longitude = lo;
            distance = d;
            name = fname;
        }
    };

    private double latitude, longitude;
    private int zoom;
    private List<friendInfo> friends_info;

    private static float rotation = 0;

    public interface Listener {
        /** Notified of a change in the view. */
        public void onChange();
    }

    private Listener mChangeListener;
    private final Handler mHandler = new Handler();
    private final Runnable mUpdateTextRunnable = new Runnable() {

        @Override
        public void run() {
            if (mRunning) {
                update();
                postDelayed(mUpdateTextRunnable, DELAY_MILLIS);
            }
        }
    };

    private boolean mRunning;

    /**
     * Sets a {@link Listener}.
     */
    public void setListener(Listener listener) {
        mChangeListener = listener;
    }

    /**
     * Returns the set {@link Listener}.
     */
    public Listener getListener() {
        return mChangeListener;
    }


    public RendezvousView(Context context) {
        this(context, null, 0);
    }

    public RendezvousView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RendezvousView(Context context, AttributeSet attrs, int i) {
        super(context, attrs, i);
        LayoutInflater.from(context).inflate(R.layout.map_layout, this);

        info=(TextView)findViewById(R.id.information);
        map= (ImageView)findViewById(R.id.static_map_pic);

        friends_info = new ArrayList<friendInfo>();
        DELAY_MILLIS = DELAY_MILLIS_STABLE;

    }

    @Override
    public boolean postDelayed(Runnable action, long delayMillis) {
        return mHandler.postDelayed(action, delayMillis);
    }

    @Override
    public boolean removeCallbacks(Runnable action) {
        mHandler.removeCallbacks(action);
        return true;
    }


    /**
     * Starts the view.
     */
    public void start() {
        if (!mRunning) {
            postDelayed(mUpdateTextRunnable, DELAY_MILLIS);
        }
        mRunning = true;
    }

    /**
     * Stops the view.
     */
    public void stop() {
        if (mRunning) {
            removeCallbacks(mUpdateTextRunnable);
        }
        mRunning = false;
    }

    /**
     * update map's latitude/longitude/zoom
     * update map_url
     */
    public void updateMap()
    {
        /**
         * TODO: get new latitude/longitude/zoom from server.
         */

        // test
        latitude = 40.7;
        longitude = -73.998;
        zoom = 13;

        map_url = makeStaticMapsUrl(latitude, longitude, zoom);
    }

    /**
     * update markers according to friends_info
     * update markers_url
     */
    public void updateMarkers()
    {
        friendInfo info;
        markers_url = "";

        for ( int i=0; i<friends_info.size(); ++i)
        {
            info = friends_info.get(i);
            markers_url += String.format( STATIC_MAP_URL_MARKER_TEMPLATE, (char)(i + 'A'), info.latitude, info.longitude );
        }
    }

    /**
     * update infoStr
     */
    public void updateInfo()
    {
        friendInfo info;
        infoStr = "";
        for ( int i=0; i<friends_info.size(); ++i)
        {
            info = friends_info.get(i);
            infoStr += String.format( FRIEND_NAME_AND_DISTANCE_TEMPLATE, (char)(i + 'A'), info.name, info.distance );
        }
    }

    static float count = 0;
    /**
     * update friends info, store them in friends_info, use them to update infoStr and markers
     */
    public void updateFriendsInfo()
    {
        friends_info.clear();

        /**
         * TODO: get friends' name/latitude/longitude/distance from server and update friends_info list.
         */

        // test
        friends_info.add( new friendInfo(40.694 + Math.min(count,12)*0.0005, -73.998, 6 - Math.min(count,12)*0.5, "Rick"));
        friends_info.add( new friendInfo(40.7, -73.998, 0, "Nicky") );
        friends_info.add( new friendInfo(40.7, -74.02 + Math.min(count,22)*0.001, 22 - Math.min(count,22), "Jeff") );
        count+=.25;
        if (count >= 25) count = 0;

        updateMarkers();
        updateInfo();
    }

    /**
     * Returns {@link android.os.SystemClock.elapsedRealtime}, overridable for testing.
     */
    protected long getElapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }

    public void update() {
        updateMap();
        updateFriendsInfo();

        new DownloadImageTask(map).execute(map_url + markers_url);
        if (map.getDrawable() == null || map.getDrawable() == getResources().getDrawable(R.drawable.spinner))
        {
            map.setImageDrawable(getResources().getDrawable(R.drawable.spinner));
        }
        info.setText(infoStr);

        if (mChangeListener != null) {
            mChangeListener.onChange();
        }

    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
                in.close();

            } catch (MalformedURLException e) {

                Log.e("Error", e.getMessage());
                e.printStackTrace();
                return null;
            } catch (IOException e) {

                Log.e("Error", e.getMessage());
                e.printStackTrace();
                return null;
            }

            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            if (result != null)
                bmImage.setImageBitmap(result);
        }
    }

    /** Formats a Google static maps URL for the specified location and zoom level. */
    private static String makeStaticMapsUrl(double latitude, double longitude, int zoom) {
        return String.format(STATIC_MAP_URL_TEMPLATE, latitude, longitude, zoom);
    }

}
