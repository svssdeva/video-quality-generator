declare module '@capacitor/core' {
  interface PluginRegistry {
    DevaYoutubeGenerator: DevaYoutubeGeneratorPlugin;
  }
}

export interface DevaYoutubeGeneratorPlugin {
  getDataFromLink(options: { link: string, isLive: boolean }): Promise<{ data: any }>;
}
