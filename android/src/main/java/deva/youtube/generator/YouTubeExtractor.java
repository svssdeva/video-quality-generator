package deva.youtube.generator;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.evgenii.jsevaluator.JsEvaluator;
import com.evgenii.jsevaluator.interfaces.JsCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class YouTubeExtractor extends AsyncTask<String, Void, SparseArray<deva.youtube.generator.YtFile>> {

    private final static boolean CACHING = true;

    static boolean LOGGING = false;

    private final static String LOG_TAG = "YouTubeExtractor";
    private final static String CACHE_FILE_NAME = "decipher_js_funct";

    private WeakReference<Context> refContext;
    private String videoID;
    private deva.youtube.generator.VideoMeta videoMeta;
    private boolean includeWebM = true;
    private boolean isLiveVideo = false;
    private boolean useHttp = false;
    private String cacheDirPath;

    private volatile String decipheredSignature;

    private static String decipherJsFileName;
    private static String decipherFunctions;
    private static String decipherFunctionName;

    private final Lock lock = new ReentrantLock();
    private final Condition jsExecuting = lock.newCondition();

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36";

    private static final Pattern patYouTubePageLink = Pattern.compile("(http|https)://(www\\.|m.|)youtube\\.com/watch\\?v=(.+?)( |\\z|&)");
    private static final Pattern patYouTubeShortLink = Pattern.compile("(http|https)://(www\\.|)youtu.be/(.+?)( |\\z|&)");

    private static final Pattern patTitle = Pattern.compile("\"title\"\\s*:\\s*\"(.*?)\"");
    private static final Pattern patAuthor = Pattern.compile("\"author\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patChannelId = Pattern.compile("\"channelId\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patLength = Pattern.compile("\"lengthSeconds\"\\s*:\\s*\"(\\d+?)\"");
    private static final Pattern patViewCount = Pattern.compile("\"viewCount\"\\s*:\\s*\"(\\d+?)\"");
    private static final Pattern patShortDescript = Pattern.compile("\"shortDescription\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patStatusOk = Pattern.compile("status=ok(&|,|\\z)");

    private static final Pattern patHlsvp = Pattern.compile("hlsManifestUrl(.+?)(&|\\\\z)");
    private static final Pattern patHlsItag = Pattern.compile("/itag/(\\d+?)/");

    private static final Pattern patItag = Pattern.compile("itag=([0-9]+?)(&|\\z)");
    private static final Pattern patEncSig = Pattern.compile("s=(.{10,}?)(\\\\\\\\u0026|\\z)");
    private static final Pattern patUrl = Pattern.compile("\"url\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patCipher = Pattern.compile("\"signatureCipher\"\\s*:\\s*\"(.+?)\"");
    private static final Pattern patCipherUrl = Pattern.compile("url=(.+?)(\\\\\\\\u0026|\\z)");

    private static final Pattern patVariableFunction = Pattern.compile("([{; =])([a-zA-Z$][a-zA-Z0-9$]{0,2})\\.([a-zA-Z$][a-zA-Z0-9$]{0,2})\\(");
    private static final Pattern patFunction = Pattern.compile("([{; =])([a-zA-Z$_][a-zA-Z0-9$]{0,2})\\(");

    private static final Pattern patDecryptionJsFile = Pattern.compile("\\\\/s\\\\/player\\\\/([^\"]+?)\\.js");
    private static final Pattern patDecryptionJsFileWithoutSlash = Pattern.compile("/s/player/([^\"]+?).js");
    private static final Pattern patSignatureDecFunction = Pattern.compile("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)");

    private static final Pattern patPlayerResponse = Pattern.compile("var ytInitialPlayerResponse\\s*=\\s*(\\{.+?\\})\\s*;");
    private static final Pattern patSigEncUrl = Pattern.compile("url=(.+?)(\\u0026|$)");
    private static final Pattern patSignature = Pattern.compile("s=(.+?)(\\u0026|$)");

    private static final SparseArray<deva.youtube.generator.Format> FORMAT_MAP = new SparseArray<>();

    static {
        // http://en.wikipedia.org/wiki/YouTube#Quality_and_formats

        // Video and Audio
        FORMAT_MAP.put(17, new deva.youtube.generator.Format(17, "3gp", 144, deva.youtube.generator.Format.VCodec.MPEG4, deva.youtube.generator. Format.ACodec.AAC, 24, false));
        FORMAT_MAP.put(36, new deva.youtube.generator.Format(36, "3gp", 240, deva.youtube.generator.Format.VCodec.MPEG4, deva.youtube.generator. Format.ACodec.AAC, 32, false));
        FORMAT_MAP.put(5, new deva.youtube.generator.Format(5, "flv", 240,  deva.youtube.generator.Format.VCodec.H263, deva.youtube.generator. Format.ACodec.MP3, 64, false));
        FORMAT_MAP.put(43, new deva.youtube.generator.Format(43, "webm", 360,  deva.youtube.generator.Format.VCodec.VP8, deva.youtube.generator. Format.ACodec.VORBIS, 128, false));
        FORMAT_MAP.put(18, new deva.youtube.generator.Format(18, "mp4", 360,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 96, false));
        FORMAT_MAP.put(22, new deva.youtube.generator.Format(22, "mp4", 720,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 192, false));

        // Dash Video
        FORMAT_MAP.put(160, new deva.youtube.generator.Format(160, "mp4", 144,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(133, new deva.youtube.generator.Format(133, "mp4", 240,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(134, new deva.youtube.generator.Format(134, "mp4", 360,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(135, new deva.youtube.generator.Format(135, "mp4", 480,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(136, new deva.youtube.generator.Format(136, "mp4", 720,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(137, new deva.youtube.generator.Format(137, "mp4", 1080,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(264, new deva.youtube.generator.Format(264, "mp4", 1440,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(266, new deva.youtube.generator.Format(266, "mp4", 2160,  deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.NONE, true));

        FORMAT_MAP.put(298, new deva.youtube.generator.Format(298, "mp4", 720,  deva.youtube.generator.Format.VCodec.H264, 60, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(299, new deva.youtube.generator.Format(299, "mp4", 1080,  deva.youtube.generator.Format.VCodec.H264, 60, deva.youtube.generator. Format.ACodec.NONE, true));

        // Dash Audio
        FORMAT_MAP.put(140, new deva.youtube.generator.Format(140, "m4a",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.AAC, 128, true));
        FORMAT_MAP.put(141, new deva.youtube.generator.Format(141, "m4a",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.AAC, 256, true));
        FORMAT_MAP.put(256, new deva.youtube.generator.Format(256, "m4a",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.AAC, 192, true));
        FORMAT_MAP.put(258, new deva.youtube.generator.Format(258, "m4a",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.AAC, 384, true));

        // WEBM Dash Video
        FORMAT_MAP.put(278, new deva.youtube.generator.Format(278, "webm", 144,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(242, new deva.youtube.generator.Format(242, "webm", 240,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(243, new deva.youtube.generator.Format(243, "webm", 360,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(244, new deva.youtube.generator.Format(244, "webm", 480,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(247, new deva.youtube.generator.Format(247, "webm", 720,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(248, new deva.youtube.generator.Format(248, "webm", 1080,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(271, new deva.youtube.generator.Format(271, "webm", 1440,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(313, new deva.youtube.generator.Format(313, "webm", 2160,  deva.youtube.generator.Format.VCodec.VP9, deva.youtube.generator. Format.ACodec.NONE, true));

        FORMAT_MAP.put(302, new deva.youtube.generator.Format(302, "webm", 720,  deva.youtube.generator.Format.VCodec.VP9, 60, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(308, new deva.youtube.generator.Format(308, "webm", 1440,  deva.youtube.generator.Format.VCodec.VP9, 60, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(303, new deva.youtube.generator.Format(303, "webm", 1080,  deva.youtube.generator.Format.VCodec.VP9, 60, deva.youtube.generator. Format.ACodec.NONE, true));
        FORMAT_MAP.put(315, new deva.youtube.generator.Format(315, "webm", 2160,  deva.youtube.generator.Format.VCodec.VP9, 60, deva.youtube.generator. Format.ACodec.NONE, true));

        // WEBM Dash Audio
        FORMAT_MAP.put(171, new deva.youtube.generator.Format(171, "webm",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.VORBIS, 128, true));

        FORMAT_MAP.put(249, new deva.youtube.generator.Format(249, "webm",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.OPUS, 48, true));
        FORMAT_MAP.put(250, new deva.youtube.generator.Format(250, "webm",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.OPUS, 64, true));
        FORMAT_MAP.put(251, new deva.youtube.generator.Format(251, "webm",  deva.youtube.generator.Format.VCodec.NONE, deva.youtube.generator. Format.ACodec.OPUS, 160, true));

        // HLS Live Stream
        FORMAT_MAP.put(91, new deva.youtube.generator.Format(91, "mp4", 144 , deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 48, false, true));
        FORMAT_MAP.put(92, new deva.youtube.generator.Format(92, "mp4", 240 , deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 48, false, true));
        FORMAT_MAP.put(93, new deva.youtube.generator.Format(93, "mp4", 360 , deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 128, false, true));
        FORMAT_MAP.put(94, new deva.youtube.generator.Format(94, "mp4", 480 , deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 128, false, true));
        FORMAT_MAP.put(95, new deva.youtube.generator.Format(95, "mp4", 720 , deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 256, false, true));
        FORMAT_MAP.put(96, new deva.youtube.generator.Format(96, "mp4", 1080 , deva.youtube.generator.Format.VCodec.H264, deva.youtube.generator. Format.ACodec.AAC, 256, false, true));
    }

    public YouTubeExtractor(@NonNull Context con) {

        refContext = new WeakReference<>(con);
        cacheDirPath = con.getCacheDir().getAbsolutePath();
    }

    @Override
    protected void onPostExecute(SparseArray<deva.youtube.generator.YtFile> ytFiles) {
        onExtractionComplete(ytFiles, videoMeta);
    }


    /**
     * Start the extraction.
     *
     * @param youtubeLink       the youtube page link or video id
     * @param parseDashManifest not supported anymore
     * @param includeWebM       true if WebM streams should be extracted
     */
    public void extract(String youtubeLink, boolean parseDashManifest, boolean includeWebM, boolean isLiveVideo) {
        this.includeWebM = includeWebM;
        this.isLiveVideo = isLiveVideo;
        this.execute(youtubeLink);
    }

    protected abstract void onExtractionComplete(SparseArray<deva.youtube.generator.YtFile> ytFiles, deva.youtube.generator.VideoMeta videoMeta);

    @Override
    protected SparseArray<deva.youtube.generator.YtFile> doInBackground(String... params) {
        videoID = null;
        String ytUrl = params[0];
        if (ytUrl == null) {
            return null;
        }
        Matcher mat = patYouTubePageLink.matcher(ytUrl);
        if (mat.find()) {
            videoID = mat.group(3);
        } else {
            mat = patYouTubeShortLink.matcher(ytUrl);
            if (mat.find()) {
                videoID = mat.group(3);
            } else if (ytUrl.matches("\\p{Graph}+?")) {
                videoID = ytUrl;
            }
        }
        if (videoID != null) {
            try {
                if (isLiveVideo) return getLiveStreamUrls();
                else return getStreamUrls();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e(LOG_TAG, "Wrong YouTube link format");
        }
        return null;
    }

    private SparseArray<deva.youtube.generator.YtFile> getStreamUrls() throws IOException, InterruptedException, JSONException {

        String pageHtml;
        SparseArray<String> encSignatures = new SparseArray<>();
        SparseArray<deva.youtube.generator.YtFile> ytFiles = new SparseArray<>();

        BufferedReader reader = null;
        HttpURLConnection urlConnection = null;
        URL getUrl = new URL("https://youtube.com/watch?v=" + videoID);
        try {
            urlConnection = (HttpURLConnection) getUrl.openConnection();
            urlConnection.setRequestProperty("User-Agent", USER_AGENT);
            reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder sbPageHtml = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sbPageHtml.append(line);
            }
            pageHtml = sbPageHtml.toString();
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        Matcher mat = patPlayerResponse.matcher(pageHtml);
        if (mat.find()) {
            JSONObject ytPlayerResponse = new JSONObject(mat.group(1));
            JSONObject streamingData = ytPlayerResponse.getJSONObject("streamingData");

            JSONArray formats = streamingData.getJSONArray("formats");
            for (int i = 0; i < formats.length(); i++) {

                JSONObject format = formats.getJSONObject(i);
                int itag = format.getInt("itag");

                if (FORMAT_MAP.get(itag) != null) {
                    if (format.has("url")) {
                        String url = format.getString("url").replace("\\u0026", "&");
                        ytFiles.append(itag, new deva.youtube.generator.YtFile(FORMAT_MAP.get(itag), url));
                    } else if (format.has("signatureCipher")) {

                        mat = patSigEncUrl.matcher(format.getString("signatureCipher"));
                        Matcher matSig = patSignature.matcher(format.getString("signatureCipher"));
                        if (mat.find() && matSig.find()) {
                            String url = URLDecoder.decode(mat.group(1), "UTF-8");
                            String signature = URLDecoder.decode(matSig.group(1), "UTF-8");
                            ytFiles.append(itag, new YtFile(FORMAT_MAP.get(itag), url));
                            encSignatures.append(itag, signature);
                        }
                    }
                }
            }

            JSONArray adaptiveFormats = streamingData.getJSONArray("adaptiveFormats");
            for (int i = 0; i < adaptiveFormats.length(); i++) {

                JSONObject adaptiveFormat = adaptiveFormats.getJSONObject(i);
                int itag = adaptiveFormat.getInt("itag");

                if (FORMAT_MAP.get(itag) != null) {
                    if (adaptiveFormat.has("url")) {
                        String url = adaptiveFormat.getString("url").replace("\\u0026", "&");
                        ytFiles.append(itag, new YtFile(FORMAT_MAP.get(itag), url));
                    } else if (adaptiveFormat.has("signatureCipher")) {

                        mat = patSigEncUrl.matcher(adaptiveFormat.getString("signatureCipher"));
                        Matcher matSig = patSignature.matcher(adaptiveFormat.getString("signatureCipher"));
                        if (mat.find() && matSig.find()) {
                            String url = URLDecoder.decode(mat.group(1), "UTF-8");
                            String signature = URLDecoder.decode(matSig.group(1), "UTF-8");
                            ytFiles.append(itag, new YtFile(FORMAT_MAP.get(itag), url));
                            encSignatures.append(itag, signature);
                        }
                    }
                }
            }

            JSONObject videoDetails = ytPlayerResponse.getJSONObject("videoDetails");
            this.videoMeta = new VideoMeta(videoDetails.getString("videoId"),
                    videoDetails.getString("title"),
                    videoDetails.getString("author"),
                    videoDetails.getString("channelId"),
                    Long.parseLong(videoDetails.getString("lengthSeconds")),
                    Long.parseLong(videoDetails.getString("viewCount")),
                    videoDetails.getBoolean("isLiveContent"),
                    videoDetails.getString("shortDescription"));

        } else {
            Log.d(LOG_TAG, "ytPlayerResponse was not found");
        }

        if (encSignatures.size() > 0) {

            String curJsFileName;

            if (CACHING
                    && (decipherJsFileName == null || decipherFunctions == null || decipherFunctionName == null)) {
                readDecipherFunctFromCache();
            }

            mat = patDecryptionJsFile.matcher(pageHtml);
            if (!mat.find())
                mat = patDecryptionJsFileWithoutSlash.matcher(pageHtml);
            if (mat.find()) {
                curJsFileName = mat.group(0).replace("\\/", "/");
                if (decipherJsFileName == null || !decipherJsFileName.equals(curJsFileName)) {
                    decipherFunctions = null;
                    decipherFunctionName = null;
                }
                decipherJsFileName = curJsFileName;
            }

            if (LOGGING)
                Log.d(LOG_TAG, "Decipher signatures: " + encSignatures.size() + ", videos: " + ytFiles.size());

            String signature;
            decipheredSignature = null;
            if (decipherSignature(encSignatures)) {
                lock.lock();
                try {
                    jsExecuting.await(7, TimeUnit.SECONDS);
                } finally {
                    lock.unlock();
                }
            }

            signature = decipheredSignature;
            if (signature == null) {
                return null;
            } else {
                String[] sigs = signature.split("\n");
                for (int i = 0; i < encSignatures.size() && i < sigs.length; i++) {
                    int key = encSignatures.keyAt(i);
                    String url = ytFiles.get(key).getUrl();
                    url += "&sig=" + sigs[i];
                    YtFile newFile = new YtFile(FORMAT_MAP.get(key), url);
                    ytFiles.put(key, newFile);
                }
            }
        }

        if (ytFiles.size() == 0) {
            if (LOGGING)
                Log.d(LOG_TAG, pageHtml);
            return null;
        }

        return ytFiles;
    }

    private SparseArray<YtFile> getLiveStreamUrls() throws IOException, InterruptedException, JSONException {

        URL getUrl = new URL("https://youtube.com/watch?v=" + videoID);

        String streamMap;
        BufferedReader reader = null;
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) getUrl.openConnection();
            urlConnection.setRequestProperty("User-Agent", USER_AGENT);
            reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            StringBuilder sbPageHtml = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sbPageHtml.append(line);
            }
            streamMap = sbPageHtml.toString();
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        SparseArray<String> encSignatures = new SparseArray<>();
        SparseArray<YtFile> ytFiles = new SparseArray<>();

        Matcher mat = patPlayerResponse.matcher(streamMap);
        if (mat.find()) {
            JSONObject ytPlayerResponse = new JSONObject(mat.group(1));
            JSONObject streamingData = ytPlayerResponse.getJSONObject("streamingData");

            String liveVideoUrl = streamingData.getString("hlsManifestUrl");
            YtFile ytFile = new YtFile(new Format(0,"",-10,null,null,false),liveVideoUrl);
            ytFiles.put(1001,ytFile);
            return ytFiles;

        } else {
            Log.d(LOG_TAG, "ytPlayerResponse was not found");
        }
        return null;
    }


    private boolean decipherSignature(final SparseArray<String> encSignatures) throws IOException {
        // Assume the functions don't change that much
        if (decipherFunctionName == null || decipherFunctions == null) {
            String decipherFunctUrl = "https://youtube.com" + decipherJsFileName;

            BufferedReader reader = null;
            String javascriptFile;
            URL url = new URL(decipherFunctUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("User-Agent", USER_AGENT);
            try {
                reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                StringBuilder sb = new StringBuilder("");
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                    sb.append(" ");
                }
                javascriptFile = sb.toString();
            } finally {
                if (reader != null)
                    reader.close();
                urlConnection.disconnect();
            }

            if (LOGGING)
                Log.d(LOG_TAG, "Decipher FunctURL: " + decipherFunctUrl);
            Matcher mat = patSignatureDecFunction.matcher(javascriptFile);
            if (mat.find()) {
                decipherFunctionName = mat.group(1);
                if (LOGGING)
                    Log.d(LOG_TAG, "Decipher Functname: " + decipherFunctionName);

                Pattern patMainVariable = Pattern.compile("(var |\\s|,|;)" + decipherFunctionName.replace("$", "\\$") +
                        "(=function\\((.{1,3})\\)\\{)");

                String mainDecipherFunct;

                mat = patMainVariable.matcher(javascriptFile);
                if (mat.find()) {
                    mainDecipherFunct = "var " + decipherFunctionName + mat.group(2);
                } else {
                    Pattern patMainFunction = Pattern.compile("function " + decipherFunctionName.replace("$", "\\$") +
                            "(\\((.{1,3})\\)\\{)");
                    mat = patMainFunction.matcher(javascriptFile);
                    if (!mat.find())
                        return false;
                    mainDecipherFunct = "function " + decipherFunctionName + mat.group(2);
                }

                int startIndex = mat.end();

                for (int braces = 1, i = startIndex; i < javascriptFile.length(); i++) {
                    if (braces == 0 && startIndex + 5 < i) {
                        mainDecipherFunct += javascriptFile.substring(startIndex, i) + ";";
                        break;
                    }
                    if (javascriptFile.charAt(i) == '{')
                        braces++;
                    else if (javascriptFile.charAt(i) == '}')
                        braces--;
                }
                decipherFunctions = mainDecipherFunct;
                // Search the main function for extra functions and variables
                // needed for deciphering
                // Search for variables
                mat = patVariableFunction.matcher(mainDecipherFunct);
                while (mat.find()) {
                    String variableDef = "var " + mat.group(2) + "={";
                    if (decipherFunctions.contains(variableDef)) {
                        continue;
                    }
                    startIndex = javascriptFile.indexOf(variableDef) + variableDef.length();
                    for (int braces = 1, i = startIndex; i < javascriptFile.length(); i++) {
                        if (braces == 0) {
                            decipherFunctions += variableDef + javascriptFile.substring(startIndex, i) + ";";
                            break;
                        }
                        if (javascriptFile.charAt(i) == '{')
                            braces++;
                        else if (javascriptFile.charAt(i) == '}')
                            braces--;
                    }
                }
                // Search for functions
                mat = patFunction.matcher(mainDecipherFunct);
                while (mat.find()) {
                    String functionDef = "function " + mat.group(2) + "(";
                    if (decipherFunctions.contains(functionDef)) {
                        continue;
                    }
                    startIndex = javascriptFile.indexOf(functionDef) + functionDef.length();
                    for (int braces = 0, i = startIndex; i < javascriptFile.length(); i++) {
                        if (braces == 0 && startIndex + 5 < i) {
                            decipherFunctions += functionDef + javascriptFile.substring(startIndex, i) + ";";
                            break;
                        }
                        if (javascriptFile.charAt(i) == '{')
                            braces++;
                        else if (javascriptFile.charAt(i) == '}')
                            braces--;
                    }
                }

                if (LOGGING)
                    Log.d(LOG_TAG, "Decipher Function: " + decipherFunctions);
                decipherViaWebView(encSignatures);
                if (CACHING) {
                    writeDeciperFunctToChache();
                }
            } else {
                return false;
            }
        } else {
            decipherViaWebView(encSignatures);
        }
        return true;
    }

    private void parseVideoMeta(String getVideoInfo) {
        boolean isLiveStream = false;
        String title = null, author = null, channelId = null, shortDescript = null;
        long viewCount = 0, length = 0;
        Matcher mat = patTitle.matcher(getVideoInfo);
        if (mat.find()) {
            title = mat.group(1);
        }

        mat = patHlsvp.matcher(getVideoInfo);
        if(mat.find())
            isLiveStream = true;

        mat = patAuthor.matcher(getVideoInfo);
        if (mat.find()) {
            author = mat.group(1);
        }
        mat = patChannelId.matcher(getVideoInfo);
        if (mat.find()) {
            channelId = mat.group(1);
        }
        mat = patShortDescript.matcher(getVideoInfo);
        if (mat.find()) {
            shortDescript = mat.group(1);
        }

        mat = patLength.matcher(getVideoInfo);
        if (mat.find()) {
            length = Long.parseLong(mat.group(1));
        }
        mat = patViewCount.matcher(getVideoInfo);
        if (mat.find()) {
            viewCount = Long.parseLong(mat.group(1));
        }
        videoMeta = new VideoMeta(videoID, title, author, channelId, length, viewCount, isLiveStream, shortDescript);
    }

    private void readDecipherFunctFromCache() {
        File cacheFile = new File(cacheDirPath + "/" + CACHE_FILE_NAME);
        // The cached functions are valid for 2 weeks
        if (cacheFile.exists() && (System.currentTimeMillis() - cacheFile.lastModified()) < 1209600000) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(cacheFile), "UTF-8"));
                decipherJsFileName = reader.readLine();
                decipherFunctionName = reader.readLine();
                decipherFunctions = reader.readLine();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * not supported anymore
     */
    public void setParseDashManifest(boolean parseDashManifest) {
    }


    /**
     * Include the webm format files into the result. Default: true
     */
    public void setIncludeWebM(boolean includeWebM) {
        this.includeWebM = includeWebM;
    }


    /**
     * Set default protocol of the returned urls to HTTP instead of HTTPS.
     * HTTP may be blocked in some regions so HTTPS is the default value.
     * <p/>
     * Note: Enciphered videos require HTTPS so they are not affected by
     * this.
     */
    public void setDefaultHttpProtocol(boolean useHttp) {
        this.useHttp = useHttp;
    }

    private void writeDeciperFunctToChache() {
        File cacheFile = new File(cacheDirPath + "/" + CACHE_FILE_NAME);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cacheFile), "UTF-8"));
            writer.write(decipherJsFileName + "\n");
            writer.write(decipherFunctionName + "\n");
            writer.write(decipherFunctions);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void decipherViaWebView(final SparseArray<String> encSignatures) {
        final Context context = refContext.get();
        if (context == null)
        {
            return;
        }

        final StringBuilder stb = new StringBuilder(decipherFunctions + " function decipher(");
        stb.append("){return ");
        for (int i = 0; i < encSignatures.size(); i++) {
            int key = encSignatures.keyAt(i);
            if (i < encSignatures.size() - 1)
                stb.append(decipherFunctionName).append("('").append(encSignatures.get(key)).
                        append("')+\"\\n\"+");
            else
                stb.append(decipherFunctionName).append("('").append(encSignatures.get(key)).
                        append("')");
        }
        stb.append("};decipher();");

        new Handler(Looper.getMainLooper()).post(new Runnable() {

            @Override
            public void run() {
                new JsEvaluator(context).evaluate(stb.toString(), new JsCallback() {
                    @Override
                    public void onResult(String result) {
                        lock.lock();
                        try {
                            decipheredSignature = result;
                            jsExecuting.signal();
                        } finally {
                            lock.unlock();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        lock.lock();
                        try {
                            if(LOGGING)
                                Log.e(LOG_TAG, errorMessage);
                            jsExecuting.signal();
                        } finally {
                            lock.unlock();
                        }
                    }
                });
            }
        });
    }
    
}
