declare module '@capacitor/core' {
  interface PluginRegistry {
    DevaYoutubeGenerator: DevaYoutubeGeneratorPlugin;
  }
}

export interface DevaYoutubeGeneratorPlugin {
  getDataFromLink(options: { link: string, isLive: boolean }): Promise<{ data: any }>;
  setPlayerData(options: { videoData: string; userData: string; token: string, responseHeaders: string, extraData: string, responseMapper: string }): Promise<{ videoData: string; userData: string; token: string,  responseHeaders: string, extraData: string, responseMapper: string }>;

}
