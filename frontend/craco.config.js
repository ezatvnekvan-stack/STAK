const path = require("path");
const buildCode = require("fs").readFileSync(path.join(__dirname, "android/app/build.gradle"), "utf8").match(/versionCode (\d+)/)[1];
const appVersion = require("fs").readFileSync(path.join(__dirname, "android/app/build.gradle"), "utf8").match(/versionName ['"]([^'"]+)/)[1];
if (require("./package.json").version !== appVersion) throw new Error("package.json version must match Android versionName");
process.env.REACT_APP_APP_VERSION = appVersion;
module.exports = {
  webpack: { alias: { "@": path.resolve(__dirname, "src") }, configure: config => {
    config.plugins.push({ apply(compiler) {
      compiler.hooks.thisCompilation.tap("STAKVersion", compilation => {
        compilation.hooks.processAssets.tap({ name: "STAKVersion", stage: compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_SUMMARIZE }, () => {
          const worker = compilation.getAsset("sw.js");
          if (worker) compilation.updateAsset("sw.js", new compiler.webpack.sources.RawSource(worker.source.source().toString().replace(/const VERSION = "[^"]+"/, `const VERSION = "stak-${appVersion}-${buildCode}"`).replace(/STAK [\d.]+ offline/, `STAK ${appVersion} offline`)));
        });
      });
    }});
    return config;
  } },
  jest: { configure: { moduleNameMapper: {
    "^@/(.*)$": "<rootDir>/src/$1",
    "^react-router/dom$": path.join(path.dirname(require.resolve("react-router/package.json")), "dist/development/dom-export.js")
  } } }
};
