import { config as loadEnv } from "dotenv";
loadEnv();

import { describe, it, expect } from "vitest";
import { handleCompareScreenshots } from "../tools/compare.js";
import { fileExists } from "../utils/exec.js";

describe("Visual: Compare current app to design", () => {
  const design = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/design/首页-推荐.jpg";
  const home = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/screenshots/v3_home.png";
  const earn = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/screenshots/v2_earn.png";
  const mall = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/screenshots/v2_mall.png";
  const profile = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/screenshots/v2_profile.png";

  const designEarn = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/design/赚钱页面.jpg";
  const designMall = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/design/商城界面.jpg";
  const designProfile = "/Users/cm/AndroidStudioProjects/ToutiaoFeedDemo/design/我的.jpg";

  it("compare home to design", async () => {
    if (!(await fileExists(home)) || !(await fileExists(design))) {
      console.log("⏭️ skipping - files missing");
      return;
    }
    const result = await handleCompareScreenshots({ baselinePath: design, currentPath: home });
    console.log("HOME COMPARE RESULT:", JSON.stringify(result, null, 2).slice(0, 5000));
    expect(result.isError).toBeFalsy();
  }, 120000);

  it("compare earn to design", async () => {
    if (!(await fileExists(earn)) || !(await fileExists(designEarn))) {
      console.log("⏭️ skipping - files missing");
      return;
    }
    const result = await handleCompareScreenshots({ baselinePath: designEarn, currentPath: earn });
    console.log("EARN COMPARE RESULT:", JSON.stringify(result, null, 2).slice(0, 5000));
    expect(result.isError).toBeFalsy();
  }, 120000);

  it("compare mall to design", async () => {
    if (!(await fileExists(mall)) || !(await fileExists(designMall))) {
      console.log("⏭️ skipping - files missing");
      return;
    }
    const result = await handleCompareScreenshots({ baselinePath: designMall, currentPath: mall });
    console.log("MALL COMPARE RESULT:", JSON.stringify(result, null, 2).slice(0, 5000));
    expect(result.isError).toBeFalsy();
  }, 120000);

  it("compare profile to design", async () => {
    if (!(await fileExists(profile)) || !(await fileExists(designProfile))) {
      console.log("⏭️ skipping - files missing");
      return;
    }
    const result = await handleCompareScreenshots({ baselinePath: designProfile, currentPath: profile });
    console.log("PROFILE COMPARE RESULT:", JSON.stringify(result, null, 2).slice(0, 5000));
    expect(result.isError).toBeFalsy();
  }, 120000);
});
