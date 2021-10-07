package deva.youtube.generator;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.google.gson.Gson;
import com.pw.nativeplayer.NativePlayerModal;
import com.pw.nativeplayer.modals.BatchCredential;
import com.pw.nativeplayer.utils.NativePlayer;
import com.pw.nativeplayer.utils.NetworkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

@NativePlugin
public class DevaYoutubeGenerator extends Plugin {

    @PluginMethod
    public void setPlayerData(PluginCall call) {
        String videoData = call.getString("videoData");
        String userData = call.getString("userData");
        String token = call.getString("token");
        String responseHeaders = call.getString("responseHeaders");
        String extraData = call.getString("extraData");

        NetworkManager networkManager = new NetworkManager(getContext());
        networkManager.getLoginManager().setAPIToken(token);
        Object response = videoData;
        NativePlayerModal nativePlayerModal = new Gson().fromJson(extraData, NativePlayerModal.class);
        //NativePlayer.playVideo(getContext(),response,nativePlayerModal);

    }

    @PluginMethod
    public void getDataFromLink(PluginCall call) {

        String value = call.getString("link");
        Boolean isLive = call.getBoolean("isLive");

        Log.v("original data", "here :" + value);
        ExtractionResponse extResponse = new ExtractionResponse();


        new YouTubeExtractor(getContext()) {
            public void onExtractionComplete(SparseArray<YtFile> ytFiles, VideoMeta vMeta) {
                if (ytFiles != null && ytFiles.size() > 0) {

                    if (ytFiles.get(1001) != null) {
                        extResponse.setLiveContent(true);
                        extResponse.setHlsManifestUrl(ytFiles.get(10001).getUrl());
                        Log.v("data", "liveFile :" + new Gson().toJson(extResponse));
                        return;
                    }
//
                    int lowestPlayUrl = 1080000;  // mostly we'll get 360
                    ArrayList<Integer> uniqueHeightList = new ArrayList<>();
                    ArrayList<YtFile> list = new ArrayList<>();
                    for (int i = 0; i < ytFiles.size(); i++) {
                        int key = ytFiles.keyAt(i);
                        YtFile ytFile = ytFiles.get(key);
                        Log.v("data", "ytFile :" + new Gson().toJson(ytFile));
                        if (ytFile.getFormat().getExt().equals("mp4")) {
                            if (!ytFile.getFormat().isDashContainer() && ytFile.getFormat().getHeight() < lowestPlayUrl) {
                                extResponse.setPlayUrl(ytFile.getUrl());
                                lowestPlayUrl = ytFile.getFormat().getHeight();
                            } else {
                                if (!uniqueHeightList.contains(ytFile.getFormat().getHeight())) {
                                    list.add(ytFile);
                                    uniqueHeightList.add(ytFile.getFormat().getHeight());
                                }
                            }
                        } else {
                            extResponse.setAudioUrl(ytFile.getUrl());
                        }

                    }
                    Collections.sort(list, new Comparator<YtFile>() {
                        @Override
                        public int compare(YtFile ytFile, YtFile t1) {
                            Integer height = ytFile.getFormat().getHeight();
                            Integer height2 = t1.getFormat().getHeight();
                            return height.compareTo(height2);
                        }
                    });
                    extResponse.setExtractedFiles(list);
                    Log.v("data", "extResponse :" + new Gson().toJson(extResponse));

                    JSObject ret = new JSObject();
                    getRet(ret).put("data", new Gson().toJson(extResponse));
                    call.success(getRet(ret));
                }
            }
        }.extract(value, true, false, isLive);




    }

    public static JSObject getRet(JSObject ret) {
        return ret;
    }
}
