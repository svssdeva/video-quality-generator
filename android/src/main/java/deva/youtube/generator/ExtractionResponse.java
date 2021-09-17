package deva.youtube.generator;

import java.util.List;

import deva.youtube.generator.YtFile;

public class ExtractionResponse {

    private String playUrl;
    private String audioUrl;
    private String hlsManifestUrl;
    private Boolean isLiveContent = false;
    private List<YtFile> extractedFiles;


    public ExtractionResponse() {
    }

    public void setHlsManifestUrl(String hlsManifestUrl) {
        this.hlsManifestUrl = hlsManifestUrl;
    }

    public void setLiveContent(Boolean liveContent) {
        isLiveContent = liveContent;
    }


    public String getHlsManifestUrl() {
        return hlsManifestUrl;
    }

    public Boolean getLiveContent() {
        return isLiveContent;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public List<YtFile> getExtractedFiles() {
        return extractedFiles;
    }

    public void setExtractedFiles(List<YtFile> extractedFiles) {
        this.extractedFiles = extractedFiles;
    }

    public String getPlayUrl() {
        return playUrl;
    }

    public void setPlayUrl(String playUrl) {
        this.playUrl = playUrl;
    }

}
