package com.adamrocker.volleysample;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageLoader.ImageCache;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.Volley;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.adamrocker.volleysample.R;
import android.os.Bundle;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends SherlockActivity {

    private static final String INSTAGRAM_CLIENT_ID = "<< YOUR INSTAGRAM CLIENT-ID >>";
    private static final Object TAG = new Object();
    private static final String LOG = "VOLLEY-SAMPLE";
    private RequestQueue mQueue;
    private LinearLayout mBase;
    private MenuItem mRefreshMenu;
    private volatile int mTotalLoadedImgs = 0;
    private ImageLoader mImageLoader;

    private void toast(int id) {
        String text = getResources().getString(id);
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG).show();
    }

    private void startLoadingAnim() {
        if (mRefreshMenu != null) {
            Log.i(LOG, "===== start loading");
            ImageView iv = (ImageView) mRefreshMenu.getActionView();
            Animation rotation = AnimationUtils.loadAnimation(this,
                    R.anim.refresh_rotate);
            rotation.setRepeatCount(Animation.INFINITE);
            iv.startAnimation(rotation);
        }
    }

    private void stopLoadingAnim() {
        if (mRefreshMenu != null) {
            Log.i(LOG, "===== stop loading");
            ImageView iv = (ImageView) mRefreshMenu.getActionView();
            iv.setImageResource(R.drawable.ic_action_refresh);
            iv.clearAnimation();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.photo_stream);

        mBase = (LinearLayout) findViewById(R.id.base);
        mQueue = Volley.newRequestQueue(getApplicationContext());// thread
                                                                 // pool(4)
        mImageLoader = new ImageLoader(mQueue, new ImageCache() {
            private final LruCache<String, Bitmap> mCache = new LruCache<String, Bitmap>(10);
            public void putBitmap(String url, Bitmap bitmap) {
                mCache.put(url, bitmap);
            }
            public Bitmap getBitmap(String url) {
                return mCache.get(url);
            }
        });
        startLoadingAnim();
        refreshDatas();
    }

    private void refreshDatas() {
        String url = "https://api.instagram.com/v1/media/popular?client_id="
                + INSTAGRAM_CLIENT_ID;
        JsonObjectRequest jsonRequet = new JsonObjectRequest(Method.GET, url,
                null, new Listener<JSONObject>() {
                    public void onResponse(JSONObject result) {
                        try {
                            int code = parseJson(result);
                            if (code != 200) {
                                toast(R.string.server_error);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            toast(R.string.json_error);
                        }
                    }
                }, new Response.ErrorListener() {
                    public void onErrorResponse(VolleyError error) {
                        toast(R.string.connection_error);
                    }
                });
        jsonRequet.setTag(TAG);
        mQueue.add(jsonRequet);
    }

    private int parseJson(JSONObject root) throws JSONException {
        boolean useNetworkImageView = true;
        int code = root.getJSONObject("meta").getInt("code");
        if (code == 200) {
            int[] resTexts = { R.id.photo_text1, R.id.photo_text2,
                    R.id.photo_text3, R.id.photo_text4, R.id.photo_text5,
                    R.id.photo_text6 };
            int[] resImgs = { R.id.photo1, R.id.photo2, R.id.photo3,
                    R.id.photo4, R.id.photo5, R.id.photo6 };
            int resIndex = 0;
            LayoutInflater inf = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

            JSONArray arr = root.getJSONArray("data");
            final int len = arr.length();
            RelativeLayout rl = (RelativeLayout) inf.inflate(
                    R.layout.photo_item, null);
            mBase.addView(rl);
            for (int i = 0; i < len; i++, resIndex++) {
                JSONObject json = arr.getJSONObject(i);
                String text = null;
                try {
                    JSONObject caption = json.getJSONObject("caption");
                    if (caption != null) {
                        text = caption.getString("text");
                    } else {
                        text = "...";
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(LOG, json.toString());
                    text = "...";
                }
                String imgUrl = json.getJSONObject("images")
                        .getJSONObject("low_resolution").getString("url");

                NetworkImageView niv = (NetworkImageView) rl
                        .findViewById(resImgs[resIndex]);
                if (niv == null) {
                    rl = (RelativeLayout) inf
                            .inflate(R.layout.photo_item, null);
                    mBase.addView(rl);
                    resIndex = 0;
                    niv = (NetworkImageView) rl.findViewById(resImgs[resIndex]);
                }

                TextView tv = (TextView) rl.findViewById(resTexts[resIndex]);
                tv.setText(text);
                tv.invalidate();

                if (useNetworkImageView) {
                    // in case of using NetworkImageView
                    niv.setImageUrl(imgUrl, mImageLoader);
                } else {
                    requestImage(niv, imgUrl, len);
                }
            }
            if (useNetworkImageView) {
                stopLoadingAnim();
            }
        }
        return code;
    }

    private void requestImage(final ImageView niv, final String imgUrl, final int len) {
        ImageRequest request = new ImageRequest(imgUrl, new Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap bm) {
                niv.setImageBitmap(bm);
                mTotalLoadedImgs++;
                if (len == mTotalLoadedImgs) {
                    stopLoadingAnim();
                    mTotalLoadedImgs = 0;
                }
            }
        }, 0, 0, Config.ARGB_8888, new ErrorListener() {
            public void onErrorResponse(VolleyError arg0) {
                arg0.printStackTrace();
                mTotalLoadedImgs++;
                if (len == mTotalLoadedImgs) {
                    stopLoadingAnim();
                    mTotalLoadedImgs = 0;
                }
            }
        });
        request.setTag(TAG);
        mQueue.add(request);
    }

    public void onStop() {
        super.onStop();
        mQueue.cancelAll(TAG);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.main, menu);
        mRefreshMenu = menu.findItem(R.id.action_refresh);
        ImageView iv = (ImageView) mRefreshMenu.getActionView();
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        iv = (ImageView) inflater.inflate(R.layout.actionbar_refresh, null);
        iv.setId(R.id.action_refresh);
        iv.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mQueue.cancelAll(TAG);
                mBase.removeAllViews();
                stopLoadingAnim();
                startLoadingAnim();
                refreshDatas();
            }
        });
        mRefreshMenu.setActionView(iv);
        startLoadingAnim();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return true;
    }
}
