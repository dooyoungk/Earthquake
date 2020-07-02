package com.professionalandroid.apps.earthquake;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.icu.util.GregorianCalendar;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class EarthquakeViewModel extends AndroidViewModel {
    private static final String TAG = "EarthquakeUpdate";

    private MutableLiveData<List<Earthquake>> earthquakes;

    public EarthquakeViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<Earthquake>> getEarthquakes() {
        if (earthquakes == null) {
            earthquakes = new MutableLiveData<List<Earthquake>>();
            loadEarthquakes();
        }

        return earthquakes;
    }

    /**
     * 피드로부터 지진 데이터를 가져와서 비동기로 로드한다.
      */
    public void loadEarthquakes() {
        new AsyncTask<Void, Void, List<Earthquake>>() {

            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            protected List<Earthquake> doInBackground(Void... voids) {
                // 파싱된 지진 데이터를 저장하는 ArrayList
                ArrayList<Earthquake> earthquakes = new ArrayList<>(0);

                // XML을 가져온다.
                URL url;
                try {
                    String quakeFeed = getApplication().getString(R.string.earthquake_feed);
                    url = new URL(quakeFeed);

                    URLConnection connection;
                    connection = url.openConnection();

                    HttpURLConnection httpConnection = (HttpURLConnection)connection;
                    int responseCode = httpConnection.getResponseCode();

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream in = httpConnection.getInputStream();

                        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                        DocumentBuilder db = dbf.newDocumentBuilder();

                        // 지진 피드 데이터를 파싱한다.
                        Document dom = db.parse(in);
                        Element docEle = dom.getDocumentElement();

                        // 각 지진 항목의 내역을 가져온다.
                        NodeList nl = docEle.getElementsByTagName("entry");
                        if (nl != null && nl.getLength() > 0) {
                            for (int i = 0; i < nl.getLength(); i++) {
                                // 로딩이 취소되었는지 확인한다.
                                // 취소된 경우, 지금까지 갖고 있는 것을 변환한다.
                                if (isCancelled()) {
                                    Log.d(TAG, "Loading Cancelled");
                                    return  earthquakes;
                                }
                                Element entry = (Element)nl.item(i);
                                Element id = (Element)entry.getElementsByTagName("id").item(0);
                                Element title = (Element)entry.getElementsByTagName("title").item(0);
                                Element g = (Element)entry.getElementsByTagName("georss:point").item(0);
                                Element when = (Element)entry.getElementsByTagName("updated").item(0);
                                Element link = (Element)entry.getElementsByTagName("link").item(0);

                                String idString = id.getFirstChild().getNodeValue();
                                String details = title.getFirstChild().getNodeValue();
                                String hostname = "http://earthquake.usgs.gov";
                                String linkString = hostname + link.getAttribute("href");
                                String point = g.getFirstChild().getNodeValue();
                                String dt = when.getFirstChild().getNodeValue();
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS'Z'");
                                Date qdate = new GregorianCalendar(0, 0, 0).getTime();
                                try {
                                    qdate = sdf.parse(dt);
                                } catch (ParseException e) {
                                    Log.e(TAG, "Date parsing exception", e);
                                }

                                String[] location = point.split(" ");
                                Location l = new Location("dummyGPS");
                                l.setLatitude(Double.parseDouble(location[0]));
                                l.setLongitude(Double.parseDouble(location[1]));

                                String magnitudeString = details.split(" ")[1];
                                int end = magnitudeString.length() - 1;
                                double  magnitude = Double.parseDouble(magnitudeString.substring(0, end));

                                if (details.contains("-"))
                                    details = details.split("-")[1].trim();
                                else
                                    details = "";

                                final Earthquake earthquake = new Earthquake(idString,
                                        qdate,
                                        details,
                                        l,
                                        magnitude,
                                        linkString);

                                // 새 지진 데이터를 결과 배열에 추가한다.
                                earthquake.add(earthquake);
                            }
                        }
                    }
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                } catch (SAXException e) {
                    e.printStackTrace();
                }
                return earthquakes;
            }

            @Override
            protected void onPostExecute(List<Earthquake> data) {
                earthquakes.setValue(data);
            }
        }.execute();
    }
}
