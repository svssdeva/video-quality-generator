import { WebPlugin } from '@capacitor/core';
import { DevaYoutubeGeneratorPlugin } from './definitions';

export class DevaYoutubeGeneratorWeb extends WebPlugin implements DevaYoutubeGeneratorPlugin {
  constructor() {
    super({
      name: 'DevaYoutubeGenerator',
      platforms: ['web'],
    });
  }
  getDataFromLink(options: { link: string; isLive: boolean }): Promise<{ data: any }> {
    return Promise.resolve({ data: options });
  }

  async setPlayerData(options: { videoData: string; userData: string; token: string, responseHeaders: string, extraData: string, responseMapper: string }): Promise<{ videoData: string; userData: string; token: string,  responseHeaders: string, extraData: string, responseMapper: string }> {
    return options;
  }
}

const DevaYoutubeGenerator = new DevaYoutubeGeneratorWeb();

export { DevaYoutubeGenerator };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(DevaYoutubeGenerator);
