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
}

const DevaYoutubeGenerator = new DevaYoutubeGeneratorWeb();

export { DevaYoutubeGenerator };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(DevaYoutubeGenerator);
