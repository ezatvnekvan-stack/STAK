// The production value comes from the matching package/Gradle versions in CRACO.
// Adapter-only runtimes do not provide process.env.
export const APP_VERSION=(()=>{try{return process.env.REACT_APP_APP_VERSION || '1.0.0';}catch{return '1.0.0';}})();
