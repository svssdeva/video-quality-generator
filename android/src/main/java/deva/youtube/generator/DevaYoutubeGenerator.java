package deva.youtube.generator;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.google.gson.Gson;
import com.pw.nativeplayer.NativePlayerModal;
import com.pw.nativeplayer.modals.BatchCredential;
import com.pw.nativeplayer.response.BatchData;
import com.pw.nativeplayer.response.CourseContentData;
import com.pw.nativeplayer.response.DemoVideosData;
import com.pw.nativeplayer.response.DoubtData;
import com.pw.nativeplayer.response.FAQData;
import com.pw.nativeplayer.response.RecentActivityData;
import com.pw.nativeplayer.response.Schedules;
import com.pw.nativeplayer.response.Solution;
import com.pw.nativeplayer.response.StoreListData;
import com.pw.nativeplayer.response.SubjectListData;
import com.pw.nativeplayer.response.TodayClassesData;
import com.pw.nativeplayer.response.TopicContentData;
import com.pw.nativeplayer.response.VideoBookmarkData;
import com.pw.nativeplayer.response.VideoDetails;
import com.pw.nativeplayer.utils.NativePlayer;
import com.pw.nativeplayer.utils.NetworkManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import deva.youtube.generator.youtubequalitygenerator.R;

@NativePlugin
public class DevaYoutubeGenerator extends Plugin {

    @PluginMethod
    public void setPlayerData(PluginCall call) {
        String videoData = call.getString("videoData");
        String userData = call.getString("userData");
        String token = call.getString("token");
        String responseHeaders = call.getString("responseHeaders");
        String extraData = call.getString("extraData");
        String responseMapper = call.getString("responseMapper");

        NetworkManager networkManager = new NetworkManager(getContext());
        networkManager.getLoginManager().setAPIToken(token);

        NativePlayerModal nativePlayerModal = new Gson().fromJson(extraData, NativePlayerModal.class);

        switch (responseMapper){
            case ResponseMapper.VIDEO_DETAILS : {
                VideoDetails videoDetails = new Gson().fromJson(videoData, VideoDetails.class);
                NativePlayer.playVideo(getContext(),videoDetails,nativePlayerModal);
                break;
            }

            case ResponseMapper.COURSE_CONTENT_DATA: {
                CourseContentData courseContentData = new Gson().fromJson(videoData, CourseContentData.class);
                NativePlayer.playVideo(getContext(),courseContentData,nativePlayerModal);
                break;
            }

            case ResponseMapper.TOPIC_CONTENT_DATA: {
                TopicContentData topicContentData = new Gson().fromJson(videoData, TopicContentData.class);
                NativePlayer.playVideo(getContext(),topicContentData,nativePlayerModal);
                break;
            }

            case ResponseMapper.RECENT_ACTIVITY_DATA: {
                RecentActivityData recentActivityData = new Gson().fromJson(videoData, RecentActivityData.class);
                NativePlayer.playVideo(getContext(),recentActivityData,nativePlayerModal);
                break;
            }

            case ResponseMapper.VIDEO_BOOKMARK_DATA: {
                VideoBookmarkData videoBookmarkData = new Gson().fromJson(videoData, VideoBookmarkData.class);
                NativePlayer.playVideo(getContext(),videoBookmarkData,nativePlayerModal);
                break;
            }

            case ResponseMapper.SUBJECT_LIST_DATA: {
                SubjectListData subjectListData = new Gson().fromJson(videoData, SubjectListData.class);
                NativePlayer.playVideo(getContext(),subjectListData,nativePlayerModal);
                break;
            }

            case ResponseMapper.DOUBT_DATA: {
                DoubtData doubtData = new Gson().fromJson(videoData, DoubtData.class);
                NativePlayer.playVideo(getContext(),doubtData,nativePlayerModal);
                break;
            }

            case ResponseMapper.BATCH_DATA: {
                BatchData batchData = new Gson().fromJson(videoData, BatchData.class);
                NativePlayer.playVideo(getContext(),batchData,nativePlayerModal);
                break;
            }

            case ResponseMapper.SCHEDULES: {
                Schedules schedules = new Gson().fromJson(videoData, Schedules.class);
                NativePlayer.playVideo(getContext(),schedules,nativePlayerModal);
                break;
            }

            case ResponseMapper.TODAY_CLASSES_DATA: {
                TodayClassesData todayClassesData = new Gson().fromJson(videoData, TodayClassesData.class);
                NativePlayer.playVideo(getContext(),todayClassesData,nativePlayerModal);
                break;
            }

            case ResponseMapper.DEMO_VIDEOS_DATA: {
                DemoVideosData demoVideosData = new Gson().fromJson(videoData, DemoVideosData.class);
                NativePlayer.playVideo(getContext(),demoVideosData,nativePlayerModal);
                break;
            }

            case ResponseMapper.STORE_LIST_DATA: {
                StoreListData storeListData = new Gson().fromJson(videoData, StoreListData.class);
                NativePlayer.playVideo(getContext(),storeListData,nativePlayerModal);
                break;
            }

            case ResponseMapper.SOLUTION: {
                Solution solution = new Gson().fromJson(videoData, Solution.class);
                NativePlayer.playVideo(getContext(),solution,nativePlayerModal);
                break;
            }

            case ResponseMapper.FAQ_DATA: {
                FAQData faqData = new Gson().fromJson(videoData, FAQData.class);
                NativePlayer.playVideo(getContext(),faqData,nativePlayerModal);
                break;
            }

            default: {
                Toast.makeText(getContext(), getContext().getString(R.string.response_not_handled),Toast.LENGTH_SHORT).show();
            }

        }

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
